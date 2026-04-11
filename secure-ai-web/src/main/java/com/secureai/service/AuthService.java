package com.secureai.service;

import com.secureai.model.LoginRequest;
import com.secureai.model.LoginResponse;
import com.secureai.model.RegisterRequest;
import com.secureai.model.User;
import com.secureai.repository.UserRepository;
import com.secureai.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authentication Service
 *
 * Responsibilities:
 *  - User registration with BCrypt (cost=12) hashing
 *  - Login with credential verification against PostgreSQL
 *  - JWT token issuance
 *  - Account lockout after repeated failed attempts
 *
 * Security guarantees:
 *  - Passwords NEVER stored or logged in plaintext
 *  - BCrypt random salt defeats rainbow table attacks
 *  - Constant-time comparison via BCrypt (no timing attacks)
 *  - Duplicate usernames/emails rejected at registration
 *  - Account lockout: 5 failed attempts → 15 minute cooldown
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_SECONDS = 900; // 15 minutes

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /** Tracks failed login attempts per username: username → [count, lastFailureEpoch] */
    private final ConcurrentHashMap<String, long[]> failedAttempts = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Authenticate a user and issue a JWT token.
     * Enforces account lockout after MAX_FAILED_ATTEMPTS failures.
     */
    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername();

        // Check lockout before any database or password operations
        checkAccountLockout(username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    recordFailedAttempt(username);
                    return new AuthException("Invalid credentials");
                });

        if (!user.isEnabled()) {
            throw new AuthException("Account is disabled");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            recordFailedAttempt(username);
            long[] state = failedAttempts.get(username);
            int remaining = MAX_FAILED_ATTEMPTS - (state != null ? (int) state[0] : 0);
            log.warn("Failed login attempt for user '{}' ({} attempts remaining before lockout)",
                    username, Math.max(remaining, 0));
            throw new AuthException("Invalid credentials");
        }

        // Successful login — clear any failed attempts
        failedAttempts.remove(username);

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        log.info("User '{}' logged in successfully", user.getUsername());

        return new LoginResponse(
                token,
                jwtUtil.getExpirationSeconds(),
                user.getUsername(),
                user.getRole()
        );
    }

    private void checkAccountLockout(String username) {
        long[] state = failedAttempts.get(username);
        if (state == null) {
            return;
        }
        long attempts = state[0];
        long lastFailure = state[1];
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            long elapsed = Instant.now().getEpochSecond() - lastFailure;
            if (elapsed < LOCKOUT_DURATION_SECONDS) {
                long remaining = LOCKOUT_DURATION_SECONDS - elapsed;
                log.warn("Account '{}' is locked out for {} more seconds", username, remaining);
                throw new AuthException(
                        "Account is temporarily locked due to too many failed attempts. Try again in "
                        + (remaining / 60 + 1) + " minutes.");
            }
            // Lockout expired — reset
            failedAttempts.remove(username);
        }
    }

    private void recordFailedAttempt(String username) {
        failedAttempts.compute(username, (key, state) -> {
            long now = Instant.now().getEpochSecond();
            if (state == null) {
                return new long[]{1, now};
            }
            // If lockout expired, reset counter
            if (now - state[1] >= LOCKOUT_DURATION_SECONDS) {
                return new long[]{1, now};
            }
            return new long[]{state[0] + 1, now};
        });
    }

    /**
     * Register a new user with BCrypt-hashed password.
     */
    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AuthException("Username '" + request.getUsername() + "' is already taken");
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && userRepository.existsByEmail(request.getEmail())) {
            throw new AuthException("Email is already registered");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword())) // BCrypt cost=12
                .email(request.getEmail())
                .role("USER")
                .enabled(true)
                .build();

        User saved = userRepository.save(user);
        log.info("New user registered: '{}'", saved.getUsername());
        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────

    public static class AuthException extends RuntimeException {
        public AuthException(String message) { super(message); }
    }

}

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

/**
 * Authentication Service
 *
 * Responsibilities:
 *  - User registration with BCrypt (cost=12) hashing
 *  - Login with credential verification against PostgreSQL
 *  - JWT token issuance
 *
 * Security guarantees:
 *  - Passwords NEVER stored or logged in plaintext
 *  - BCrypt random salt defeats rainbow table attacks
 *  - Constant-time comparison via BCrypt (no timing attacks)
 *  - Duplicate usernames/emails rejected at registration
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Authenticate a user and issue a JWT token.
     */
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AuthException("Invalid credentials"));

        if (!user.isEnabled()) {
            throw new AuthException("Account is disabled");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Failed login attempt for user '{}'", sanitizeLog(request.getUsername()));
            throw new AuthException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        log.info("User '{}' logged in successfully", sanitizeLog(user.getUsername()));

        return new LoginResponse(
                token,
                jwtUtil.getExpirationSeconds(),
                user.getUsername(),
                user.getRole()
        );
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
        log.info("New user registered: '{}'", sanitizeLog(saved.getUsername()));
        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────

    public static class AuthException extends RuntimeException {
        public AuthException(String message) { super(message); }
    }

    /** Strips CR and LF to prevent CRLF injection in log messages. */
    private static String sanitizeLog(String value) {
        if (value == null) return "(null)";
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}

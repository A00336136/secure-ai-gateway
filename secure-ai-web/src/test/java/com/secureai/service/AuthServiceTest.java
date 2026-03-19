package com.secureai.service;

import com.secureai.model.LoginRequest;
import com.secureai.model.LoginResponse;
import com.secureai.model.RegisterRequest;
import com.secureai.model.User;
import com.secureai.repository.UserRepository;
import com.secureai.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Tests")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtUtil);
    }

    @Nested
    @DisplayName("Login")
    class LoginTests {

        @Test
        @DisplayName("Successful login should return token")
        void successfulLoginShouldReturnToken() {
            User user = User.builder()
                    .username("alice")
                    .password("$2a$12$hashedpassword")
                    .role("USER")
                    .enabled(true)
                    .build();
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("password123", "$2a$12$hashedpassword")).thenReturn(true);
            when(jwtUtil.generateToken("alice", "USER")).thenReturn("jwt.token.here");
            when(jwtUtil.getExpirationSeconds()).thenReturn(3600L);

            LoginResponse response = authService.login(new LoginRequest("alice", "password123"));

            assertThat(response.getToken()).isEqualTo("jwt.token.here");
            assertThat(response.getUsername()).isEqualTo("alice");
            assertThat(response.getRole()).isEqualTo("USER");
        }

        @Test
        @DisplayName("Login with unknown user should throw AuthException")
        void unknownUserShouldThrow() {
            when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(new LoginRequest("unknown", "pass")))
                    .isInstanceOf(AuthService.AuthException.class)
                    .hasMessage("Invalid credentials");
        }

        @Test
        @DisplayName("Login with disabled account should throw AuthException")
        void disabledAccountShouldThrow() {
            User user = User.builder()
                    .username("disabled_user")
                    .password("hashed")
                    .role("USER")
                    .enabled(false)
                    .build();
            when(userRepository.findByUsername("disabled_user")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(new LoginRequest("disabled_user", "pass")))
                    .isInstanceOf(AuthService.AuthException.class)
                    .hasMessage("Account is disabled");
        }

        @Test
        @DisplayName("Login with wrong password should throw AuthException")
        void wrongPasswordShouldThrow() {
            User user = User.builder()
                    .username("alice")
                    .password("$2a$12$hashedpassword")
                    .role("USER")
                    .enabled(true)
                    .build();
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongpass", "$2a$12$hashedpassword")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrongpass")))
                    .isInstanceOf(AuthService.AuthException.class)
                    .hasMessage("Invalid credentials");
        }
    }

    @Nested
    @DisplayName("Register")
    class RegisterTests {

        @Test
        @DisplayName("Successful registration should return saved user")
        void successfulRegistrationShouldReturnUser() {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("newuser");
            req.setPassword("securepassword");
            req.setEmail("new@example.com");

            when(userRepository.existsByUsername("newuser")).thenReturn(false);
            when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
            when(passwordEncoder.encode("securepassword")).thenReturn("$2a$12$encoded");
            User savedUser = User.builder()
                    .username("newuser")
                    .password("$2a$12$encoded")
                    .email("new@example.com")
                    .role("USER")
                    .enabled(true)
                    .build();
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            User result = authService.register(req);

            assertThat(result.getUsername()).isEqualTo("newuser");
            assertThat(result.getRole()).isEqualTo("USER");
        }

        @Test
        @DisplayName("Duplicate username should throw AuthException")
        void duplicateUsernameShouldThrow() {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("existing");
            req.setPassword("password123");

            when(userRepository.existsByUsername("existing")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(req))
                    .isInstanceOf(AuthService.AuthException.class)
                    .hasMessageContaining("already taken");
        }

        @Test
        @DisplayName("Duplicate email should throw AuthException")
        void duplicateEmailShouldThrow() {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("newuser");
            req.setPassword("password123");
            req.setEmail("taken@example.com");

            when(userRepository.existsByUsername("newuser")).thenReturn(false);
            when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(req))
                    .isInstanceOf(AuthService.AuthException.class)
                    .hasMessageContaining("already registered");
        }

        @Test
        @DisplayName("Registration with null email should skip email check")
        void nullEmailShouldSkipEmailCheck() {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("newuser");
            req.setPassword("securepassword");
            req.setEmail(null);

            when(userRepository.existsByUsername("newuser")).thenReturn(false);
            when(passwordEncoder.encode("securepassword")).thenReturn("$2a$12$encoded");
            User savedUser = User.builder()
                    .username("newuser")
                    .password("$2a$12$encoded")
                    .role("USER")
                    .enabled(true)
                    .build();
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            User result = authService.register(req);

            assertThat(result.getUsername()).isEqualTo("newuser");
        }

        @Test
        @DisplayName("Registration with blank email should skip email check")
        void blankEmailShouldSkipEmailCheck() {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("newuser");
            req.setPassword("securepassword");
            req.setEmail("   ");

            when(userRepository.existsByUsername("newuser")).thenReturn(false);
            when(passwordEncoder.encode("securepassword")).thenReturn("$2a$12$encoded");
            User savedUser = User.builder()
                    .username("newuser")
                    .password("$2a$12$encoded")
                    .role("USER")
                    .enabled(true)
                    .build();
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            User result = authService.register(req);

            assertThat(result.getUsername()).isEqualTo("newuser");
        }
    }
}

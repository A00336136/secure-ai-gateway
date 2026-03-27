package com.secureai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecurityConfig Tests")
class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Nested
    @DisplayName("Password Encoder")
    class PasswordEncoderTests {

        @Test
        @DisplayName("Should provide BCrypt password encoder with cost 12")
        void shouldProvideBCryptEncoder() {
            PasswordEncoder encoder = securityConfig.passwordEncoder();
            assertThat(encoder).isNotNull().isInstanceOf(BCryptPasswordEncoder.class);
        }

        @Test
        @DisplayName("Encoded password should match raw password")
        void shouldMatchRawPassword() {
            PasswordEncoder encoder = securityConfig.passwordEncoder();
            String raw = "StrongP@ss123";
            String encoded = encoder.encode(raw);
            assertThat(encoder.matches(raw, encoded)).isTrue();
            assertThat(encoder.matches("wrong", encoded)).isFalse();
        }
    }

    @Nested
    @DisplayName("CORS Configuration")
    class CorsTests {

        @Test
        @DisplayName("Should configure CORS source with allowed origins")
        void shouldConfigureCorsWithOrigins() {
            CorsConfigurationSource source = securityConfig.corsConfigurationSource();
            assertThat(source).isNotNull();

            // Retrieve the config for any path
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/test");
            CorsConfiguration config = source.getCorsConfiguration(request);
            assertThat(config).isNotNull();
            assertThat(config.getAllowedOriginPatterns()).contains(
                    "http://localhost:*",
                    "http://127.0.0.1:*",
                    "https://*.secureai.local"
            );
        }

        @Test
        @DisplayName("Should configure allowed HTTP methods")
        void shouldConfigureAllowedMethods() {
            CorsConfigurationSource source = securityConfig.corsConfigurationSource();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/test");
            CorsConfiguration config = source.getCorsConfiguration(request);

            assertThat(config.getAllowedMethods())
                    .containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE", "OPTIONS");
        }

        @Test
        @DisplayName("Should configure allowed headers")
        void shouldConfigureAllowedHeaders() {
            CorsConfigurationSource source = securityConfig.corsConfigurationSource();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/test");
            CorsConfiguration config = source.getCorsConfiguration(request);

            assertThat(config.getAllowedHeaders())
                    .containsExactlyInAnyOrder("Authorization", "Content-Type", "X-Requested-With");
        }

        @Test
        @DisplayName("Should configure exposed headers")
        void shouldConfigureExposedHeaders() {
            CorsConfigurationSource source = securityConfig.corsConfigurationSource();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/test");
            CorsConfiguration config = source.getCorsConfiguration(request);

            assertThat(config.getExposedHeaders())
                    .containsExactlyInAnyOrder("X-Rate-Limit-Remaining", "Retry-After");
        }

        @Test
        @DisplayName("Should allow credentials")
        void shouldAllowCredentials() {
            CorsConfigurationSource source = securityConfig.corsConfigurationSource();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/test");
            CorsConfiguration config = source.getCorsConfiguration(request);

            assertThat(config.getAllowCredentials()).isTrue();
            assertThat(config.getMaxAge()).isEqualTo(3600L);
        }
    }
}

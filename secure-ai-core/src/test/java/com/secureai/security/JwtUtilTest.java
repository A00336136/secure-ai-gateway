package com.secureai.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtUtil Tests")
class JwtUtilTest {

    private JwtUtil jwtUtil;
    private static final String VALID_SECRET =
            "test-secret-key-minimum-32-chars-for-hs256-algorithm";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", VALID_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 3600000L);
    }

    @Nested
    @DisplayName("Token Generation")
    class TokenGeneration {

        @Test
        @DisplayName("Should generate non-null token for valid user")
        void shouldGenerateToken() {
            String token = jwtUtil.generateToken("alice", "USER");
            assertThat(token).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("Should generate token with three JWT segments")
        void shouldHaveThreeSegments() {
            String token = jwtUtil.generateToken("alice", "USER");
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("Different users should get different tokens")
        void differentUsersGetDifferentTokens() {
            String token1 = jwtUtil.generateToken("alice", "USER");
            String token2 = jwtUtil.generateToken("bob", "USER");
            assertThat(token1).isNotEqualTo(token2);
        }

        @Test
        @DisplayName("Generating multiple tokens for the same user all return valid tokens")
        void multipleTokensForSameUserAreAllValid() {
            String token1 = jwtUtil.generateToken("alice", "USER");
            String token2 = jwtUtil.generateToken("alice", "USER");
            assertThat(token1).isNotNull().isNotBlank();
            assertThat(token2).isNotNull().isNotBlank();
        }
    }

    @Nested
    @DisplayName("Token Validation")
    class TokenValidation {

        @Test
        @DisplayName("Valid token should pass validation")
        void validTokenShouldPass() {
            String token = jwtUtil.generateToken("alice", "USER");
            assertThat(jwtUtil.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("Tampered token should fail validation")
        void tamperedTokenShouldFail() {
            String token = jwtUtil.generateToken("alice", "USER");
            String tampered = token.substring(0, token.length() - 5) + "XXXXX";
            assertThat(jwtUtil.validateToken(tampered)).isFalse();
        }

        @Test
        @DisplayName("Expired token should fail validation")
        void expiredTokenShouldFail() throws InterruptedException {
            ReflectionTestUtils.setField(jwtUtil, "expirationMs", 1L); // 1ms TTL
            String token = jwtUtil.generateToken("alice", "USER");
            Thread.sleep(50);
            assertThat(jwtUtil.validateToken(token)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "invalid", "not.a.jwt", "Bearer abc"})
        @DisplayName("Invalid token strings should fail validation")
        void invalidTokensShouldFail(String token) {
            assertThat(jwtUtil.validateToken(token)).isFalse();
        }

        @Test
        @DisplayName("Null token should return false, not throw")
        void nullTokenShouldReturnFalse() {
            assertThat(jwtUtil.validateToken(null)).isFalse();
        }

        @Test
        @DisplayName("Token signed with different secret should fail")
        void wrongSecretShouldFail() {
            JwtUtil otherJwt = new JwtUtil();
            ReflectionTestUtils.setField(otherJwt, "secret",
                    "completely-different-secret-key-32-chars-long");
            ReflectionTestUtils.setField(otherJwt, "expirationMs", 3600000L);

            String token = otherJwt.generateToken("alice", "USER");
            assertThat(jwtUtil.validateToken(token)).isFalse();
        }
    }

    @Nested
    @DisplayName("Claims Extraction")
    class ClaimsExtraction {

        @Test
        @DisplayName("Should extract correct username from token")
        void shouldExtractUsername() {
            String token = jwtUtil.generateToken("alice", "USER");
            assertThat(jwtUtil.getUsernameFromToken(token)).isEqualTo("alice");
        }

        @Test
        @DisplayName("Should extract correct role from token")
        void shouldExtractRole() {
            String token = jwtUtil.generateToken("admin", "ADMIN");
            assertThat(jwtUtil.getRoleFromToken(token)).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("Should extract USER role correctly")
        void shouldExtractUserRole() {
            String token = jwtUtil.generateToken("alice", "USER");
            assertThat(jwtUtil.getRoleFromToken(token)).isEqualTo("USER");
        }

        @Test
        @DisplayName("Expiration seconds should be positive")
        void expirationSecondsShouldBePositive() {
            assertThat(jwtUtil.getExpirationSeconds()).isGreaterThan(0);
        }
    }
}

package com.secureai.util;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtUtil class.
 * Tests JWT token generation, validation, and extraction.
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;
    private static final String TEST_SECRET = "test-secret-key-that-is-at-least-32-characters-long-for-security";
    private static final String TEST_ISSUER = "test-issuer";
    private static final long TEST_EXPIRATION = 3600000L; // 1 hour

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", TEST_EXPIRATION);
        ReflectionTestUtils.setField(jwtUtil, "issuer", TEST_ISSUER);
        jwtUtil.init();
    }

    @Test
    void testGenerateToken_Success() {
        // Given
        String username = "testuser";

        // When
        String token = jwtUtil.generateToken(username);

        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.split("\\.").length == 3); // JWT has 3 parts
    }

    @Test
    void testValidateToken_ValidToken_ReturnsTrue() {
        // Given
        String username = "testuser";
        String token = jwtUtil.generateToken(username);

        // When
        boolean isValid = jwtUtil.validateToken(token);

        // Then
        assertTrue(isValid);
    }

    @Test
    void testValidateToken_InvalidToken_ReturnsFalse() {
        // Given
        String invalidToken = "invalid.jwt.token";

        // When
        boolean isValid = jwtUtil.validateToken(invalidToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testValidateToken_EmptyToken_ReturnsFalse() {
        // Given
        String emptyToken = "";

        // When
        boolean isValid = jwtUtil.validateToken(emptyToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testGetUsernameFromToken_ValidToken_ReturnsUsername() {
        // Given
        String username = "testuser";
        String token = jwtUtil.generateToken(username);

        // When
        String extractedUsername = jwtUtil.getUsernameFromToken(token);

        // Then
        assertEquals(username, extractedUsername);
    }

    @Test
    void testGetUsernameFromToken_InvalidToken_ThrowsException() {
        // Given
        String invalidToken = "invalid.jwt.token";

        // When & Then
        assertThrows(JwtException.class, () -> jwtUtil.getUsernameFromToken(invalidToken));
    }

    @Test
    void testGetExpirationDateFromToken_ValidToken_ReturnsDate() {
        // Given
        String username = "testuser";
        String token = jwtUtil.generateToken(username);

        // When
        Date expirationDate = jwtUtil.getExpirationDateFromToken(token);

        // Then
        assertNotNull(expirationDate);
        assertTrue(expirationDate.after(new Date()));
    }

    @Test
    void testValidateToken_ExpiredToken_ReturnsFalse() {
        // Given - Create a JWT util with very short expiration
        JwtUtil shortExpirationJwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(shortExpirationJwtUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(shortExpirationJwtUtil, "expiration", 1L); // 1ms
        ReflectionTestUtils.setField(shortExpirationJwtUtil, "issuer", TEST_ISSUER);
        shortExpirationJwtUtil.init();

        String username = "testuser";
        String token = shortExpirationJwtUtil.generateToken(username);

        // Wait for token to expire
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // When
        boolean isValid = shortExpirationJwtUtil.validateToken(token);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testInit_ShortSecret_ThrowsException() {
        // Given
        JwtUtil invalidJwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(invalidJwtUtil, "secret", "short");
        ReflectionTestUtils.setField(invalidJwtUtil, "expiration", TEST_EXPIRATION);
        ReflectionTestUtils.setField(invalidJwtUtil, "issuer", TEST_ISSUER);

        // When & Then
        assertThrows(IllegalStateException.class, invalidJwtUtil::init);
    }

    @Test
    void testInit_NullSecret_ThrowsException() {
        // Given
        JwtUtil invalidJwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(invalidJwtUtil, "secret", null);
        ReflectionTestUtils.setField(invalidJwtUtil, "expiration", TEST_EXPIRATION);
        ReflectionTestUtils.setField(invalidJwtUtil, "issuer", TEST_ISSUER);

        // When & Then
        assertThrows(IllegalStateException.class, invalidJwtUtil::init);
    }

    @Test
    void testGenerateToken_DifferentUsers_GenerateDifferentTokens() {
        // Given
        String user1 = "user1";
        String user2 = "user2";

        // When
        String token1 = jwtUtil.generateToken(user1);
        String token2 = jwtUtil.generateToken(user2);

        // Then
        assertNotEquals(token1, token2);
        assertEquals(user1, jwtUtil.getUsernameFromToken(token1));
        assertEquals(user2, jwtUtil.getUsernameFromToken(token2));
    }
}

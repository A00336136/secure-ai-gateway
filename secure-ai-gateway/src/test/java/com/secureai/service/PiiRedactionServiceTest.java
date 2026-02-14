package com.secureai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PiiRedactionService class.
 * Tests PII detection and redaction functionality.
 */
class PiiRedactionServiceTest {

    private PiiRedactionService piiRedactionService;

    @BeforeEach
    void setUp() {
        piiRedactionService = new PiiRedactionService();
        // Enable all PII patterns for testing
        ReflectionTestUtils.setField(piiRedactionService, "emailEnabled", true);
        ReflectionTestUtils.setField(piiRedactionService, "phoneEnabled", true);
        ReflectionTestUtils.setField(piiRedactionService, "ssnEnabled", true);
        ReflectionTestUtils.setField(piiRedactionService, "creditCardEnabled", true);
        ReflectionTestUtils.setField(piiRedactionService, "ipAddressEnabled", true);
    }

    @Test
    void testContainsPii_EmailAddress_ReturnsTrue() {
        // Given
        String text = "Contact me at john.doe@example.com";

        // When
        boolean result = piiRedactionService.containsPii(text);

        // Then
        assertTrue(result);
    }

    @Test
    void testContainsPii_PhoneNumber_ReturnsTrue() {
        // Given
        String text = "Call me at 123-456-7890";

        // When
        boolean result = piiRedactionService.containsPii(text);

        // Then
        assertTrue(result);
    }

    @Test
    void testContainsPii_SSN_ReturnsTrue() {
        // Given
        String text = "My SSN is 123-45-6789";

        // When
        boolean result = piiRedactionService.containsPii(text);

        // Then
        assertTrue(result);
    }

    @Test
    void testContainsPii_CreditCard_ReturnsTrue() {
        // Given
        String text = "Card number: 1234 5678 9012 3456";

        // When
        boolean result = piiRedactionService.containsPii(text);

        // Then
        assertTrue(result);
    }

    @Test
    void testContainsPii_IPAddress_ReturnsTrue() {
        // Given
        String text = "Server IP: 192.168.1.1";

        // When
        boolean result = piiRedactionService.containsPii(text);

        // Then
        assertTrue(result);
    }

    @Test
    void testContainsPii_NoPII_ReturnsFalse() {
        // Given
        String text = "This is a normal text without any PII";

        // When
        boolean result = piiRedactionService.containsPii(text);

        // Then
        assertFalse(result);
    }

    @Test
    void testContainsPii_EmptyString_ReturnsFalse() {
        // Given
        String text = "";

        // When
        boolean result = piiRedactionService.containsPii(text);

        // Then
        assertFalse(result);
    }

    @Test
    void testContainsPii_NullString_ReturnsFalse() {
        // Given
        String text = null;

        // When
        boolean result = piiRedactionService.containsPii(text);

        // Then
        assertFalse(result);
    }

    @Test
    void testRedact_EmailAddress_RedactsSuccessfully() {
        // Given
        String text = "Contact me at john.doe@example.com";

        // When
        String redacted = piiRedactionService.redact(text);

        // Then
        assertFalse(redacted.contains("john.doe@example.com"));
        assertTrue(redacted.contains("[REDACTED_EMAIL]"));
    }

    @Test
    void testRedact_PhoneNumber_RedactsSuccessfully() {
        // Given
        String text = "Call me at 123-456-7890";

        // When
        String redacted = piiRedactionService.redact(text);

        // Then
        assertFalse(redacted.contains("123-456-7890"));
        assertTrue(redacted.contains("[REDACTED_PHONE]"));
    }

    @Test
    void testRedact_SSN_RedactsSuccessfully() {
        // Given
        String text = "My SSN is 123-45-6789";

        // When
        String redacted = piiRedactionService.redact(text);

        // Then
        assertFalse(redacted.contains("123-45-6789"));
        assertTrue(redacted.contains("[REDACTED_SSN]"));
    }

    @Test
    void testRedact_CreditCard_RedactsSuccessfully() {
        // Given
        String text = "Card: 1234 5678 9012 3456";

        // When
        String redacted = piiRedactionService.redact(text);

        // Then
        assertFalse(redacted.contains("1234 5678 9012 3456"));
        assertTrue(redacted.contains("[REDACTED_CARD]"));
    }

    @Test
    void testRedact_IPAddress_RedactsSuccessfully() {
        // Given
        String text = "Server: 192.168.1.1";

        // When
        String redacted = piiRedactionService.redact(text);

        // Then
        assertFalse(redacted.contains("192.168.1.1"));
        assertTrue(redacted.contains("[REDACTED_IP]"));
    }

    @Test
    void testRedact_MultiplePIITypes_RedactsAll() {
        // Given
        String text = "Email: test@example.com, Phone: 123-456-7890, SSN: 123-45-6789";

        // When
        String redacted = piiRedactionService.redact(text);

        // Then
        assertTrue(redacted.contains("[REDACTED_EMAIL]"));
        assertTrue(redacted.contains("[REDACTED_PHONE]"));
        assertTrue(redacted.contains("[REDACTED_SSN]"));
        assertFalse(redacted.contains("test@example.com"));
        assertFalse(redacted.contains("123-456-7890"));
        assertFalse(redacted.contains("123-45-6789"));
    }

    @Test
    void testRedact_NoPII_ReturnsOriginal() {
        // Given
        String text = "This is a normal text without any PII";

        // When
        String redacted = piiRedactionService.redact(text);

        // Then
        assertEquals(text, redacted);
    }

    @Test
    void testRedact_NullString_ReturnsNull() {
        // Given
        String text = null;

        // When
        String redacted = piiRedactionService.redact(text);

        // Then
        assertNull(redacted);
    }

    @Test
    void testDetectPiiTypes_MultiplePII_ReturnsAllTypes() {
        // Given
        String text = "Email: test@example.com, Phone: 123-456-7890";

        // When
        String types = piiRedactionService.detectPiiTypes(text);

        // Then
        assertTrue(types.contains("Email"));
        assertTrue(types.contains("Phone"));
    }

    @Test
    void testDetectPiiTypes_NoPII_ReturnsNone() {
        // Given
        String text = "This is a normal text";

        // When
        String types = piiRedactionService.detectPiiTypes(text);

        // Then
        assertEquals("None", types);
    }

    @Test
    void testRedact_MultipleEmails_RedactsAll() {
        // Given
        String text = "Contacts: alice@example.com and bob@example.com";

        // When
        String redacted = piiRedactionService.redact(text);

        // Then
        assertFalse(redacted.contains("alice@example.com"));
        assertFalse(redacted.contains("bob@example.com"));
        assertTrue(redacted.contains("[REDACTED_EMAIL]"));
    }
}

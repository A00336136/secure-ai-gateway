package com.secureai.pii;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PII Redaction Service Tests")
class PiiRedactionServiceTest {

    private PiiRedactionService service;

    @BeforeEach
    void setUp() {
        service = new PiiRedactionService();
        ReflectionTestUtils.setField(service, "enabled", true);
    }

    @Nested
    @DisplayName("Email Redaction")
    class EmailTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "Contact me at john@example.com for details",
            "Send to admin@company.co.uk please",
            "Email: test.user+tag@gmail.com"
        })
        @DisplayName("Should detect and redact email addresses")
        void shouldRedactEmails(String text) {
            assertThat(service.containsPii(text)).isTrue();
            String redacted = service.redact(text);
            assertThat(redacted).contains("[EMAIL_REDACTED]");
            assertThat(redacted).doesNotContain("@");
        }

        @Test
        @DisplayName("Multiple emails in one response should all be redacted")
        void shouldRedactMultipleEmails() {
            String text = "From: a@example.com To: b@test.org CC: c@domain.com";
            String redacted = service.redact(text);
            assertThat(redacted).doesNotContain("@");
            assertThat(redacted).contains("[EMAIL_REDACTED]");
        }
    }

    @Nested
    @DisplayName("SSN Redaction")
    class SsnTests {

        @ParameterizedTest
        @CsvSource({
            "'SSN: 123-45-6789','[SSN_REDACTED]'",
            "'Social security 987-65-4321','[SSN_REDACTED]'"
        })
        @DisplayName("Should detect and redact valid SSNs")
        void shouldRedactSsn(String text, String expectedToken) {
            assertThat(service.containsPii(text)).isTrue();
            assertThat(service.redact(text)).contains(expectedToken);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "000-12-3456",  // Invalid — 000 prefix
            "666-12-3456"   // Invalid — 666 prefix
        })
        @DisplayName("Invalid SSN prefixes should not be flagged")
        void invalidSsnShouldNotBeRedacted(String text) {
            assertThat(service.containsPii(text)).isFalse();
        }
    }

    @Nested
    @DisplayName("Phone Number Redaction")
    class PhoneTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "Call me at 087-123-4567",
            "Phone: 0861234567",
            "+35312345678",
            "+1-555-123-4567"
        })
        @DisplayName("Should detect and redact phone numbers")
        void shouldRedactPhone(String text) {
            assertThat(service.containsPii(text)).isTrue();
            assertThat(service.redact(text)).contains("[PHONE_REDACTED]");
        }
    }

    @Nested
    @DisplayName("Credit Card Redaction")
    class CreditCardTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "Card: 4532015112830366",        // Visa
            "4916338506082832",               // Visa
            "5425233430109903",               // MasterCard
            "Card number: 4111 1111 1111 1111"
        })
        @DisplayName("Should detect and redact credit card numbers")
        void shouldRedactCreditCard(String text) {
            assertThat(service.containsPii(text)).isTrue();
            assertThat(service.redact(text)).contains("[CREDIT_CARD_REDACTED]");
        }
    }

    @Nested
    @DisplayName("IP Address Redaction")
    class IpTests {

        @Test
        @DisplayName("Should redact IPv4 addresses")
        void shouldRedactIPv4() {
            String text = "Server at 192.168.1.100 responded";
            assertThat(service.containsPii(text)).isTrue();
            assertThat(service.redact(text)).contains("[IP_REDACTED]");
        }

        @Test
        @DisplayName("Should redact IPv6 addresses")
        void shouldRedactIPv6() {
            String text = "Connected from 2001:0db8:85a3:0000:0000:8a2e:0370:7334";
            assertThat(service.containsPii(text)).isTrue();
            assertThat(service.redact(text)).contains("[IP_REDACTED]");
        }
    }

    @Nested
    @DisplayName("IBAN Redaction")
    class IbanTests {

        @Test
        @DisplayName("Should redact IBAN numbers")
        void shouldRedactIban() {
            String text = "Transfer to IE29AIBK93115212345678";
            assertThat(service.containsPii(text)).isTrue();
            assertThat(service.redact(text)).contains("[IBAN_REDACTED]");
        }
    }

    @Nested
    @DisplayName("Combined PII Scenarios")
    class CombinedTests {

        @Test
        @DisplayName("Full patient record - all PII types should be redacted")
        void fullPatientRecord() {
            String text = "Patient John Smith, email: john@hospital.com, " +
                    "phone: 087-123-4567, SSN: 123-45-6789, " +
                    "IP: 10.0.0.1, DOB: 15/06/1985";

            assertThat(service.containsPii(text)).isTrue();
            String redacted = service.redact(text);
            assertThat(redacted).contains("[EMAIL_REDACTED]");
            assertThat(redacted).contains("[PHONE_REDACTED]");
            assertThat(redacted).contains("[SSN_REDACTED]");
            assertThat(redacted).contains("[IP_REDACTED]");
        }

        @Test
        @DisplayName("Clean text with no PII should pass through unchanged")
        void cleanTextShouldPassThrough() {
            String text = "The weather in Dublin is rainy today. Temperature: 12°C.";
            assertThat(service.containsPii(text)).isFalse();
            assertThat(service.redact(text)).isEqualTo(text);
        }

        @Test
        @DisplayName("Null input should return null, not throw")
        void nullInputShouldReturnNull() {
            assertThat(service.redact(null)).isNull();
            assertThat(service.containsPii(null)).isFalse();
        }

        @Test
        @DisplayName("Empty string should return empty string")
        void emptyStringShouldReturnEmpty() {
            assertThat(service.redact("")).isEqualTo("");
            assertThat(service.containsPii("")).isFalse();
        }
    }

    @Nested
    @DisplayName("PII Type Detection")
    class TypeDetectionTests {

        @Test
        @DisplayName("Should identify all PII types present")
        void shouldIdentifyPiiTypes() {
            String text = "email: test@example.com, SSN: 123-45-6789";
            var types = service.detectPiiTypes(text);
            assertThat(types).contains("EMAIL", "SSN");
        }

        @Test
        @DisplayName("Clean text should return empty type set")
        void cleanTextReturnsEmptySet() {
            String text = "The quick brown fox.";
            assertThat(service.detectPiiTypes(text)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Disabled Redaction")
    class DisabledTests {

        @Test
        @DisplayName("When disabled, redact() should return original text")
        void whenDisabledShouldNotRedact() {
            ReflectionTestUtils.setField(service, "enabled", false);
            String text = "Email: admin@example.com";
            assertThat(service.redact(text)).isEqualTo(text);
        }
    }
}

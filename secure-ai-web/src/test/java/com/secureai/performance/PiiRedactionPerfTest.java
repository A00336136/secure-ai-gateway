package com.secureai.performance;

import com.secureai.pii.PiiRedactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance Test — Test Pyramid Layer 3
 *
 * Measures PII Redaction Engine throughput:
 *  - 12 regex patterns per scan
 *  - Simulates real-world text with embedded PII
 */
@DisplayName("Performance Tests — PII Redaction Throughput")
class PiiRedactionPerfTest {

    private PiiRedactionService piiService;

    @BeforeEach
    void setUp() {
        piiService = new PiiRedactionService();
        try {
            var field = PiiRedactionService.class.getDeclaredField("redactionEnabled");
            field.setAccessible(true);
            field.set(piiService, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("PII scan: 1000 texts with embedded PII under 3 seconds")
    void piiScanThroughput() {
        String textWithPii = """
                Please contact John at john.doe@example.com or call +1-555-123-4567.
                His SSN is 123-45-6789 and credit card is 4111-1111-1111-1111.
                IP address: 192.168.1.100, IBAN: IE29AIBK93115212345678.
                Date of birth: 15/03/1990, Passport: AB1234567.
                """;

        int scanCount = 1000;
        Instant start = Instant.now();

        for (int i = 0; i < scanCount; i++) {
            String redacted = piiService.redact(textWithPii);
            assertThat(redacted).contains("[EMAIL_REDACTED]");
        }

        Duration elapsed = Duration.between(start, Instant.now());
        double scansPerSecond = scanCount / (elapsed.toMillis() / 1000.0);

        System.out.printf("[PERF] PII Redaction: %d scans in %d ms (%.0f scans/sec)%n",
                scanCount, elapsed.toMillis(), scansPerSecond);

        assertThat(elapsed).isLessThan(Duration.ofSeconds(3));
        assertThat(scansPerSecond).isGreaterThan(300);
    }

    @Test
    @DisplayName("PII scan on clean text: 5000 operations under 2 seconds")
    void cleanTextThroughput() {
        String cleanText = "The weather today is sunny with a high of 25 degrees. " +
                "The project deadline is next Friday and the team meeting is at 3 PM.";

        int scanCount = 5000;
        Instant start = Instant.now();

        for (int i = 0; i < scanCount; i++) {
            String result = piiService.redact(cleanText);
            assertThat(result).isEqualTo(cleanText); // No PII, no changes
        }

        Duration elapsed = Duration.between(start, Instant.now());
        double scansPerSecond = scanCount / (elapsed.toMillis() / 1000.0);

        System.out.printf("[PERF] PII Clean Text: %d scans in %d ms (%.0f scans/sec)%n",
                scanCount, elapsed.toMillis(), scansPerSecond);

        assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
    }
}

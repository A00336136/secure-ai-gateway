package com.secureai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Service for detecting and redacting Personally Identifiable Information (PII).
 * Implements OWASP data protection best practices.
 */
@Slf4j
@Service
public class PiiRedactionService {

    @Value("${pii.patterns.email:true}")
    private boolean emailEnabled;

    @Value("${pii.patterns.phone:true}")
    private boolean phoneEnabled;

    @Value("${pii.patterns.ssn:true}")
    private boolean ssnEnabled;

    @Value("${pii.patterns.credit-card:true}")
    private boolean creditCardEnabled;

    @Value("${pii.patterns.ip-address:true}")
    private boolean ipAddressEnabled;

    // Regex patterns for PII detection
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(\\+?1[\\-\\s]?)?\\(?\\d{3}\\)?[\\-\\s]?\\d{3}[\\-\\s]?\\d{4}");

    private static final Pattern SSN_PATTERN =
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");

    private static final Pattern CREDIT_CARD_PATTERN =
            Pattern.compile("\\b(?:\\d{4}[\\s-]?){3}\\d{4}\\b");

    private static final Pattern IP_ADDRESS_PATTERN =
            Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

    // Additional patterns for enhanced detection
    private static final Pattern DRIVERS_LICENSE_PATTERN =
            Pattern.compile("\\b[A-Z]{1,2}\\d{6,8}\\b");

    private static final Pattern PASSPORT_PATTERN =
            Pattern.compile("\\b[A-Z]{1,2}\\d{6,9}\\b");

    /**
     * Redact PII from the given text.
     *
     * @param text the text to redact
     * @return the redacted text
     */
    public String redact(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String redacted = text;
        int redactionCount = 0;

        if (emailEnabled && EMAIL_PATTERN.matcher(redacted).find()) {
            redacted = EMAIL_PATTERN.matcher(redacted).replaceAll("[REDACTED_EMAIL]");
            redactionCount++;
        }

        if (phoneEnabled && PHONE_PATTERN.matcher(redacted).find()) {
            redacted = PHONE_PATTERN.matcher(redacted).replaceAll("[REDACTED_PHONE]");
            redactionCount++;
        }

        if (ssnEnabled && SSN_PATTERN.matcher(redacted).find()) {
            redacted = SSN_PATTERN.matcher(redacted).replaceAll("[REDACTED_SSN]");
            redactionCount++;
        }

        if (creditCardEnabled && CREDIT_CARD_PATTERN.matcher(redacted).find()) {
            redacted = CREDIT_CARD_PATTERN.matcher(redacted).replaceAll("[REDACTED_CARD]");
            redactionCount++;
        }

        if (ipAddressEnabled && IP_ADDRESS_PATTERN.matcher(redacted).find()) {
            redacted = IP_ADDRESS_PATTERN.matcher(redacted).replaceAll("[REDACTED_IP]");
            redactionCount++;
        }

        if (redactionCount > 0) {
            log.info("Redacted {} PII patterns from text", redactionCount);
        }

        return redacted;
    }

    /**
     * Check if text contains any PII.
     *
     * @param text the text to check
     * @return true if PII is detected, false otherwise
     */
    public boolean containsPii(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        boolean hasPii = (emailEnabled && EMAIL_PATTERN.matcher(text).find()) ||
                         (phoneEnabled && PHONE_PATTERN.matcher(text).find()) ||
                         (ssnEnabled && SSN_PATTERN.matcher(text).find()) ||
                         (creditCardEnabled && CREDIT_CARD_PATTERN.matcher(text).find()) ||
                         (ipAddressEnabled && IP_ADDRESS_PATTERN.matcher(text).find());

        if (hasPii) {
            log.debug("PII detected in text");
        }

        return hasPii;
    }

    /**
     * Get detailed information about which PII types were detected.
     *
     * @param text the text to analyze
     * @return a description of detected PII types
     */
    public String detectPiiTypes(String text) {
        if (text == null || text.isBlank()) {
            return "None";
        }

        StringBuilder types = new StringBuilder();

        if (emailEnabled && EMAIL_PATTERN.matcher(text).find()) {
            types.append("Email, ");
        }
        if (phoneEnabled && PHONE_PATTERN.matcher(text).find()) {
            types.append("Phone, ");
        }
        if (ssnEnabled && SSN_PATTERN.matcher(text).find()) {
            types.append("SSN, ");
        }
        if (creditCardEnabled && CREDIT_CARD_PATTERN.matcher(text).find()) {
            types.append("Credit Card, ");
        }
        if (ipAddressEnabled && IP_ADDRESS_PATTERN.matcher(text).find()) {
            types.append("IP Address, ");
        }

        if (types.length() == 0) {
            return "None";
        }

        // Remove trailing comma and space
        return types.substring(0, types.length() - 2);
    }
}

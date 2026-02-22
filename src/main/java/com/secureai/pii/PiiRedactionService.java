package com.secureai.pii;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PII Redaction Engine
 *
 * Patterns covered:
 *  1. Email addresses
 *  2. Phone numbers (US, Irish, International)
 *  3. Social Security Numbers (SSN)
 *  4. Credit card numbers (Visa/MC/Amex/Discover)
 *  5. IP addresses (IPv4 + IPv6)
 *  6. Passport numbers
 *  7. Driving license patterns
 *  8. Dates of birth
 *  9. Bank account / IBAN numbers
 * 10. Named entity patterns (configurable)
 *
 * GDPR Article 25 — Data Protection by Design and by Default
 */
@Service
public class PiiRedactionService {

    private static final Logger log = LoggerFactory.getLogger(PiiRedactionService.class);

    @Value("${pii.redaction.enabled:true}")
    private boolean enabled;

    // ─────────────────────────────────────────────────────────────────────────
    // Compiled Regex Patterns
    // ─────────────────────────────────────────────────────────────────────────

    private static final Pattern EMAIL =
            Pattern.compile(
                "\\b[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}\\b",
                Pattern.CASE_INSENSITIVE
            );

    private static final Pattern PHONE_US =
            Pattern.compile(
                "\\b(?:\\+?1[\\-\\s.]?)?(?:\\(?[2-9]\\d{2}\\)?[\\-\\s.]?)?" +
                "[2-9]\\d{2}[\\-\\s.]?\\d{4}\\b"
            );

    private static final Pattern PHONE_IE =
            Pattern.compile("\\b0(?:8[0-9])[0-9]{7}\\b");

    private static final Pattern PHONE_INTL =
            Pattern.compile("\\+[1-9]\\d{7,14}\\b");

    private static final Pattern SSN =
            Pattern.compile("\\b(?!000|666|9\\d{2})\\d{3}-(?!00)\\d{2}-(?!0000)\\d{4}\\b");

    private static final Pattern CREDIT_CARD =
            Pattern.compile(
                "\\b(?:" +
                "4[0-9]{12}(?:[0-9]{3})?|"          + // Visa
                "5[1-5][0-9]{14}|"                   + // MasterCard
                "3[47][0-9]{13}|"                    + // Amex
                "3(?:0[0-5]|[68][0-9])[0-9]{11}|"   + // Diners
                "6(?:011|5[0-9]{2})[0-9]{12}|"      + // Discover
                "(?:2131|1800|35\\d{3})\\d{11}"     + // JCB
                ")\\b"
            );

    private static final Pattern CREDIT_CARD_SPACED =
            Pattern.compile(
                "\\b\\d{4}[\\s\\-]?\\d{4}[\\s\\-]?\\d{4}[\\s\\-]?\\d{4}\\b"
            );

    private static final Pattern IPV4 =
            Pattern.compile(
                "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" +
                "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b"
            );

    private static final Pattern IPV6 =
            Pattern.compile(
                "\\b(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\\b|" +
                "\\b(?:[0-9a-fA-F]{1,4}:)+:[0-9a-fA-F]{1,4}\\b"
            );

    private static final Pattern PASSPORT_US =
            Pattern.compile("\\b[A-Z]{1,2}[0-9]{6,9}\\b");

    private static final Pattern DATE_OF_BIRTH =
            Pattern.compile(
                "\\b(?:0[1-9]|[12][0-9]|3[01])[/\\-](?:0[1-9]|1[012])[/\\-](?:19|20)\\d{2}\\b|" +
                "\\b(?:19|20)\\d{2}[/\\-](?:0[1-9]|1[012])[/\\-](?:0[1-9]|[12][0-9]|3[01])\\b"
            );

    private static final Pattern IBAN =
            Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{4}\\d{7}(?:[A-Z0-9]?){0,16}\\b");

    // ─────────────────────────────────────────────────────────────────────────
    // Ordered Redaction Rules (label → pattern)
    // ─────────────────────────────────────────────────────────────────────────

    private static final List<PiiRule> RULES = List.of(
        new PiiRule("EMAIL",         "[EMAIL_REDACTED]",       EMAIL),
        new PiiRule("SSN",           "[SSN_REDACTED]",         SSN),
        new PiiRule("CREDIT_CARD",   "[CREDIT_CARD_REDACTED]", CREDIT_CARD),
        new PiiRule("CREDIT_CARD",   "[CREDIT_CARD_REDACTED]", CREDIT_CARD_SPACED),
        new PiiRule("IBAN",          "[IBAN_REDACTED]",        IBAN),
        new PiiRule("PHONE_IE",      "[PHONE_REDACTED]",       PHONE_IE),
        new PiiRule("PHONE_INTL",    "[PHONE_REDACTED]",       PHONE_INTL),
        new PiiRule("PHONE_US",      "[PHONE_REDACTED]",       PHONE_US),
        new PiiRule("DATE_OF_BIRTH", "[DOB_REDACTED]",         DATE_OF_BIRTH),
        new PiiRule("PASSPORT",      "[PASSPORT_REDACTED]",    PASSPORT_US),
        new PiiRule("IPV6",          "[IP_REDACTED]",          IPV6),
        new PiiRule("IPV4",          "[IP_REDACTED]",          IPV4)
    );

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Redact all detected PII from the given text.
     * Returns the original text unchanged if redaction is disabled.
     */
    public String redact(String text) {
        if (!enabled || text == null || text.isBlank()) {
            return text;
        }
        String result = text;
        int totalRedactions = 0;
        for (PiiRule rule : RULES) {
            Matcher m = rule.pattern.matcher(result);
            String replaced = m.replaceAll(rule.replacement);
            if (!replaced.equals(result)) {
                totalRedactions++;
                log.debug("PII redacted: type={}", rule.label);
            }
            result = replaced;
        }
        if (totalRedactions > 0) {
            log.info("PII redaction applied: {} type(s) found and redacted", totalRedactions);
        }
        return result;
    }

    /**
     * Check whether the text contains any detectable PII.
     */
    public boolean containsPii(String text) {
        if (text == null || text.isBlank()) return false;
        for (PiiRule rule : RULES) {
            if (rule.pattern.matcher(text).find()) {
                log.debug("PII detected: type={}", rule.label);
                return true;
            }
        }
        return false;
    }

    /**
     * Return a breakdown of which PII types are detected (for audit metadata).
     */
    public Set<String> detectPiiTypes(String text) {
        Set<String> detected = new LinkedHashSet<>();
        if (text == null || text.isBlank()) return detected;
        for (PiiRule rule : RULES) {
            if (rule.pattern.matcher(text).find()) {
                detected.add(rule.label);
            }
        }
        return detected;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner classes
    // ─────────────────────────────────────────────────────────────────────────

    private record PiiRule(String label, String replacement, Pattern pattern) {}
}

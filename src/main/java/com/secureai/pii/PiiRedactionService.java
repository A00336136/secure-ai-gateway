// Redaction tokens (avoid duplicated string literals)
private static final String PHONE_REDACTED = "[PHONE_REDACTED]";

private static final List<PiiRule> RULES = List.of(
    new PiiRule("EMAIL",         "[EMAIL_REDACTED]",       EMAIL),
    new PiiRule("SSN",           "[SSN_REDACTED]",         SSN),
    new PiiRule("CREDIT_CARD",   "[CREDIT_CARD_REDACTED]", CREDIT_CARD),
    new PiiRule("CREDIT_CARD",   "[CREDIT_CARD_REDACTED]", CREDIT_CARD_SPACED),
    new PiiRule("IBAN",          "[IBAN_REDACTED]",        IBAN),
    new PiiRule("PHONE_IE",      PHONE_REDACTED,           PHONE_IE),
    new PiiRule("PHONE_INTL",    PHONE_REDACTED,           PHONE_INTL),
    new PiiRule("PHONE_US",      PHONE_REDACTED,           PHONE_US),
    new PiiRule("DATE_OF_BIRTH", "[DOB_REDACTED]",         DATE_OF_BIRTH),
    new PiiRule("PASSPORT",      "[PASSPORT_REDACTED]",    PASSPORT_US),
    new PiiRule("IPV6",          "[IP_REDACTED]",          IPV6),
    new PiiRule("IPV4",          "[IP_REDACTED]",          IPV4)
);

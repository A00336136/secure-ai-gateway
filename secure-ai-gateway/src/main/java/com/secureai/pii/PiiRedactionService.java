package com.secureai.pii;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class PiiRedactionService {

    private static final Pattern EMAIL =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE =
            Pattern.compile("(\\+?\\d{1,3}[\\-\\.\\s]?)?\\(?\\d{3}\\)?[\\-\\.\\s]?\\d{3}[\\-\\.\\s]?\\d{4}");
    private static final Pattern SSN =
            Pattern.compile("\\d{3}-\\d{2}-\\d{4}");

    public String redact(String text) {
        String redacted = EMAIL.matcher(text).replaceAll("[REDACTED_EMAIL]");
        redacted = PHONE.matcher(redacted).replaceAll("[REDACTED_PHONE]");
        redacted = SSN.matcher(redacted).replaceAll("[REDACTED_SSN]");
        return redacted;
    }

    public boolean containsPii(String text) {
        return EMAIL.matcher(text).find()
                || PHONE.matcher(text).find()
                || SSN.matcher(text).find();
    }
}

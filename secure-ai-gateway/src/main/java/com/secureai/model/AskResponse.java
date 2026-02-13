package com.secureai.model;

import java.time.Instant;
import java.util.UUID;

public class AskResponse {

    private String response;
    private boolean redacted;
    private String requestId;
    private Instant timestamp;

    public AskResponse(String response, boolean redacted) {
        this.response = response;
        this.redacted = redacted;
        this.requestId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    public String getResponse() {
        return response;
    }

    public boolean isRedacted() {
        return redacted;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}

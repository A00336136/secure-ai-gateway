package com.secureai.exception;

/**
 * Exception thrown when rate limit is exceeded.
 */
public class RateLimitExceededException extends SecureAiException {
    
    public RateLimitExceededException(String message) {
        super(message);
    }
}

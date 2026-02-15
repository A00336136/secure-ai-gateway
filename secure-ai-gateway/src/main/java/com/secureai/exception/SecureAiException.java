package com.secureai.exception;

/**
 * Base exception for all application-specific exceptions.
 */
public class SecureAiException extends RuntimeException {
    
    public SecureAiException(String message) {
        super(message);
    }
    
    public SecureAiException(String message, Throwable cause) {
        super(message, cause);
    }
}

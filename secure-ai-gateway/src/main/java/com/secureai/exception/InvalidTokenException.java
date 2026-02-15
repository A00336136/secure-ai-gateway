package com.secureai.exception;

/**
 * Exception thrown when JWT token is invalid.
 */
public class InvalidTokenException extends SecureAiException {
    
    public InvalidTokenException(String message) {
        super(message);
    }
}

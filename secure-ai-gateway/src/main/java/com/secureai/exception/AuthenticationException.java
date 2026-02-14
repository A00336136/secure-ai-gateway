package com.secureai.exception;

/**
 * Exception thrown when authentication fails.
 */
public class AuthenticationException extends SecureAiException {
    
    public AuthenticationException(String message) {
        super(message);
    }
}

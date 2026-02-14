package com.secureai.exception;

/**
 * Exception thrown when AI service communication fails.
 */
public class AiServiceException extends SecureAiException {
    
    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.secureai.exception;

/**
 * Exception thrown when authentication fails.
 */
public class AuthenticationException extends SecureAiException {
    
    public AuthenticationException(String message) {
        super(message);
    }
}

/**
 * Exception thrown when AI service communication fails.
 */
class AiServiceException extends SecureAiException {
    
    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Exception thrown when rate limit is exceeded.
 */
class RateLimitExceededException extends SecureAiException {
    
    public RateLimitExceededException(String message) {
        super(message);
    }
}

/**
 * Exception thrown when JWT token is invalid.
 */
class InvalidTokenException extends SecureAiException {
    
    public InvalidTokenException(String message) {
        super(message);
    }
}

package com.secureai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Main application class for Secure AI Gateway.
 * Provides secure access to AI services with PII redaction and JWT authentication.
 */
@SpringBootApplication
@EnableCaching
public class SecureAiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecureAiGatewayApplication.class, args);
    }
}

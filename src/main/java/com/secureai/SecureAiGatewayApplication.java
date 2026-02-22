package com.secureai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Secure AI Gateway — Enterprise-Grade Security for AI Model Interactions
 *
 * Components:
 *  - JWT Authentication (HMAC-SHA256, BCrypt cost=12)
 *  - PII Redaction Engine (Email, Phone, SSN, Credit Card, IP, Date-of-Birth)
 *  - Rate Limiting via Bucket4j (100 req/hr per user)
 *  - ReAct Agent (Think → Act → Observe, max 10 steps)
 *  - Ollama Local LLM (LLaMA 3.1 / Mistral)
 *  - PostgreSQL Audit Logging
 *  - Spring Security Filter Chain
 *  - DevSecOps: SonarQube · OWASP · SpotBugs · Trivy · Jenkins 12-Stage CI/CD
 */
@SpringBootApplication
@EnableScheduling
public class SecureAiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecureAiGatewayApplication.class, args);
    }
}

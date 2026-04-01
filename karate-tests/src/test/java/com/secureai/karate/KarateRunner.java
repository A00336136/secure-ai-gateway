package com.secureai.karate;

import com.intuit.karate.junit5.Karate;

/**
 * Karate E2E Test Runner — Secure AI Gateway
 *
 * Runs BDD-style API tests against a live SecureAI Gateway instance.
 * Feature files are on the classpath under src/test/resources/features/.
 *
 * Test coverage:
 *   1. Auth    — JWT login/register/logout, invalid credentials, unauthenticated access
 *   2. Ask     — Authorised request flow, input validation, health status
 *   3. Guards  — Jailbreak detection (OWASP LLM01) → HTTP 403
 *   4. PII     — PII-in-prompt redaction (OWASP LLM02 / GDPR Art.25)
 *   5. Rate    — Rate-limit headers and enforcement (OWASP LLM10)
 *
 * Run:  cd karate-tests && mvn test -Dkarate.env=local
 * CI:   mvn test -pl karate-tests -Dkarate.base.url=http://localhost:8100
 * Skip slow (Ollama) tests: mvn test -Dkarate.options="--tags ~@slow"
 */
class KarateRunner {

    @Karate.Test
    Karate testAuth() {
        return Karate.run("classpath:features/auth/auth.feature");
    }

    @Karate.Test
    Karate testAsk() {
        return Karate.run("classpath:features/ask/ask.feature");
    }

    @Karate.Test
    Karate testGuardrails() {
        return Karate.run("classpath:features/guardrails/guardrails.feature");
    }

    @Karate.Test
    Karate testPiiMasking() {
        return Karate.run("classpath:features/pii/pii-masking.feature");
    }

    @Karate.Test
    Karate testRateLimiting() {
        return Karate.run("classpath:features/ratelimit/rate-limiting.feature");
    }
}

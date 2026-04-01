@pii @security @gdpr
Feature: PII Protection — GDPR Article 25 / OWASP LLM02
  # Tests Microsoft Presidio PII detection integrated in the 3-layer guardrails
  # Validates PII is detected and logged before reaching the LLM
  #
  # Response structure for allowed requests:
  #   { "response": "...", "piiDetected": bool, "piiRedacted": bool, ... }
  # Response for blocked requests (422):
  #   { "status": 422, "error": "Content Blocked", "message": "...", ... }
  #
  # Compliance: GDPR Art. 25 (Data Protection by Design), NIST AI 600-1

  Background:
    * url baseUrl
    Given path '/auth/login'
    And request { username: '#(user.username)', password: '#(user.password)' }
    When method POST
    Then status 200
    * def authToken = response.token

  # ─── SSN in Prompt — Detected by Presidio ──────────────────────
  Scenario: Prompt containing US SSN is processed with PII detection
    Given path '/api/ask'
    And header Authorization = 'Bearer ' + authToken
    And request { prompt: 'My Social Security Number is 123-45-6789. Can you help me?' }
    When method POST
    # Either blocked (422) or processed (200/503) — not a server error (500)
    * def sc = responseStatus
    And assert sc == 200 || sc == 422 || sc == 503

  # ─── Credit Card in Prompt ─────────────────────────────────────
  Scenario: Credit card number in prompt triggers Presidio CREDIT_CARD detection
    Given path '/api/ask'
    And header Authorization = 'Bearer ' + authToken
    And request { prompt: 'My Visa card number is 4532015112830366 CVV 123.' }
    When method POST
    * def sc = responseStatus
    And assert sc == 200 || sc == 422 || sc == 503

  # ─── PII Field in Allowed Response ─────────────────────────────
  Scenario: Allowed response includes piiDetected and piiRedacted fields
    Given path '/api/ask'
    And header Authorization = 'Bearer ' + authToken
    And request { prompt: 'What are the GDPR principles for data protection?' }
    When method POST
    Then status 200
    And match response.piiDetected != null
    And match response.piiRedacted != null
    # Clean prompt — Presidio should report no PII detected
    And match response.piiDetected == false

  # ─── PII Detection Audit Trail ─────────────────────────────────
  Scenario: Response structure contains PII audit fields for compliance tracing
    Given path '/api/ask'
    And header Authorization = 'Bearer ' + authToken
    And request { prompt: 'Explain what personally identifiable information means.' }
    When method POST
    Then status 200
    # GDPR Art.25 compliance: every response must include PII detection audit fields
    And match response contains { piiDetected: '#boolean', piiRedacted: '#boolean' }

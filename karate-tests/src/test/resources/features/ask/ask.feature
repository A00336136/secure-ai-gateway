@ask
Feature: Ask Endpoint — Secure AI Gateway
  # Tests: authenticated LLM requests, status endpoint, input validation

  Background:
    * url baseUrl
    Given path '/auth/login'
    And request { username: '#(user.username)', password: '#(user.password)' }
    When method POST
    Then status 200
    * def authToken = response.token

  # ─── Status Endpoint ───────────────────────────────────────────
  Scenario: Authenticated status endpoint returns gateway information
    Given path '/api/status'
    And header Authorization = 'Bearer ' + authToken
    When method GET
    Then status 200
    And match response.rateLimitRemaining != null
    And match response.ollamaHealthy != null
    And match response.user == '#(user.username)'

  # ─── Status — Shows Rate Limit Remaining ────────────────────────
  Scenario: Status endpoint reports remaining rate limit tokens
    Given path '/api/status'
    And header Authorization = 'Bearer ' + authToken
    When method GET
    Then status 200
    * def remaining = response.rateLimitRemaining
    And assert remaining > 0
    And assert remaining <= 100

  # ─── Unauthenticated Ask — Returns 403 ──────────────────────────
  Scenario: Request to /api/ask without Bearer token returns 403 Forbidden
    Given path '/api/ask'
    And request { prompt: 'What is 2+2?' }
    When method POST
    Then status 403

  # ─── Empty Prompt — Input Validation ────────────────────────────
  Scenario: Ask with empty prompt returns 400 Bad Request
    Given path '/api/ask'
    And header Authorization = 'Bearer ' + authToken
    And request { prompt: '' }
    When method POST
    Then status 400

  # ─── Prompt Too Long — Input Validation ─────────────────────────
  Scenario: Ask with prompt over 4000 characters returns 400
    * def longPrompt = 'A'.repeat(4001)
    Given path '/api/ask'
    And header Authorization = 'Bearer ' + authToken
    And request { prompt: '#(longPrompt)' }
    When method POST
    Then status 400

  # ─── Valid Ask ───────────────────────────────────────────────────
  @slow
  Scenario: Safe question returns 200 with LLM response fields
    Given path '/api/ask'
    And header Authorization = 'Bearer ' + authToken
    And request { prompt: 'What is the capital of France? Answer in one word only.' }
    When method POST
    Then status 200
    And match response.response != null
    And match response.model != null
    And match response.durationMs != null

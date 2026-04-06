@ratelimit @security
Feature: Rate Limiting — OWASP LLM10 (Unbounded Consumption)
  # Tests Token Bucket rate limiting via Bucket4j (Redis-backed in prod)
  # Policy: 100 requests per 60 minutes per user
  # Rate limit info returned in /api/status response body:
  #   { "rateLimitRemaining": N, "ollamaHealthy": bool, "model": "...", "user": "..." }
  #
  # OWASP LLM10: Unbounded Consumption — prevents DoS via request flooding
  # SOC 2 CC12: Resource availability protection

  Background:
    * url baseUrl
    Given path '/auth/login'
    And request { username: '#(user.username)', password: '#(user.password)' }
    When method POST
    Then status 200
    * def authToken = response.token

  # ─── Rate Limit Info Present in Status ─────────────────────────
  Scenario: Status endpoint includes rateLimitRemaining in response body
    Given path '/api/status'
    And header Authorization = 'Bearer ' + authToken
    When method GET
    Then status 200
    And match response.rateLimitRemaining != null

  # ─── Remaining Tokens Greater Than Zero ────────────────────────
  Scenario: Fresh authenticated user has remaining rate limit tokens
    Given path '/api/status'
    And header Authorization = 'Bearer ' + authToken
    When method GET
    Then status 200
    * def remaining = response.rateLimitRemaining
    And assert remaining > 0
    And assert remaining <= 100

  # ─── Rate Limit is a Valid Bounded Integer ──────────────────────
  # Note: /api/status endpoint reports the remaining count but is itself
  # a lightweight read — actual consumption happens on /api/ask calls
  Scenario: Rate limit remaining is a valid integer between 0 and capacity (100)
    Given path '/api/status'
    And header Authorization = 'Bearer ' + authToken
    When method GET
    Then status 200
    * def remaining = response.rateLimitRemaining
    And assert remaining >= 0
    And assert remaining <= 100

  # ─── Separate Buckets Per User ──────────────────────────────────
  Scenario: Admin user has independent rate limit from regular user
    Given path '/auth/login'
    And request { username: '#(admin.username)', password: '#(admin.password)' }
    When method POST
    Then status 200
    * def adminToken = response.token

    # Check admin rate limit
    Given path '/api/status'
    And header Authorization = 'Bearer ' + adminToken
    When method GET
    Then status 200
    * def adminRemaining = response.rateLimitRemaining

    # Check user rate limit
    Given path '/api/status'
    And header Authorization = 'Bearer ' + authToken
    When method GET
    Then status 200
    * def userRemaining = response.rateLimitRemaining

    # Both users have independent non-zero buckets
    And assert adminRemaining > 0
    And assert userRemaining > 0

  # ─── Unauthenticated — Rejected Before Rate Limit ───────────────
  Scenario: Unauthenticated requests are blocked before rate limit applies
    Given path '/api/ask'
    And request { prompt: 'test' }
    When method POST
    # Spring Security rejects before rate limiter runs
    Then status 403

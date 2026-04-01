@auth
Feature: JWT Authentication — Secure AI Gateway
  # Tests JWT authentication, registration, logout and access control
  # OWASP LLM Security: Validates auth & authorisation layer

  Background:
    * url baseUrl

  # ─── Health Check ──────────────────────────────────────────────
  Scenario: Auth service health check returns UP
    Given path '/auth/health'
    When method GET
    Then status 200
    And match response.status == 'UP'
    And match response.service == 'auth'

  # ─── Login — Valid User Credentials ────────────────────────────
  Scenario: Login with valid credentials returns JWT token
    Given path '/auth/login'
    And request { username: '#(user.username)', password: '#(user.password)' }
    When method POST
    Then status 200
    And match response.token != null
    And match response.tokenType == 'Bearer'
    And match response.username == '#(user.username)'
    And match response.expiresIn == 3600
    And match response.token contains 'eyJ'

  # ─── Login — Admin Role ─────────────────────────────────────────
  Scenario: Admin login returns ADMIN role in response
    Given path '/auth/login'
    And request { username: '#(admin.username)', password: '#(admin.password)' }
    When method POST
    Then status 200
    And match response.role == 'ADMIN'
    And match response.token != null

  # ─── Login — Wrong Password ─────────────────────────────────────
  Scenario: Login with incorrect password returns 401
    Given path '/auth/login'
    And request { username: '#(user.username)', password: 'WrongPass@999' }
    When method POST
    Then status 401
    And match response.error == 'Unauthorized'

  # ─── Login — Non-existent User ──────────────────────────────────
  Scenario: Login with unknown username returns 401
    Given path '/auth/login'
    And request { username: 'nobody_exists_xyz', password: 'AnyPass@123' }
    When method POST
    Then status 401

  # ─── Login — Empty Username Validation ──────────────────────────
  Scenario: Login with blank username returns 400 Bad Request
    Given path '/auth/login'
    And request { username: '', password: 'SomePass@1' }
    When method POST
    Then status 400

  # ─── Register — New User ────────────────────────────────────────
  Scenario: Register a new user account returns 201 Created
    * def timestamp = java.lang.System.currentTimeMillis()
    * def newUser = 'karate_new_' + timestamp
    Given path '/auth/register'
    And request { username: '#(newUser)', password: 'NewUser@123', email: '#(newUser + "@test.local")' }
    When method POST
    Then status 201
    And match response.message == 'User registered successfully'
    And match response.username == '#(newUser)'

  # ─── Register — Duplicate Username ──────────────────────────────
  Scenario: Register with existing username returns 401
    Given path '/auth/register'
    And request { username: '#(user.username)', password: 'NewPass@123', email: 'dup@test.local' }
    When method POST
    Then status 401
    And match response.message contains 'already taken'

  # ─── Logout + Token Invalidation ────────────────────────────────
  Scenario: Logout invalidates JWT — subsequent requests with same token return 403
    # Step 1: Login to get token
    Given path '/auth/login'
    And request { username: '#(user.username)', password: '#(user.password)' }
    When method POST
    Then status 200
    * def logoutToken = response.token

    # Step 2: Logout — token is blacklisted in Redis
    Given path '/auth/logout'
    And header Authorization = 'Bearer ' + logoutToken
    When method POST
    Then status 200
    And match response.message == 'Logged out successfully'

    # Step 3: Blacklisted token should now be rejected (403)
    Given path '/api/status'
    And header Authorization = 'Bearer ' + logoutToken
    When method GET
    Then status 403

  # ─── Unauthenticated Access Rejected ────────────────────────────
  Scenario: Request to /api/ask without any token returns 403
    Given path '/api/ask'
    And request { prompt: 'Hello' }
    When method POST
    Then status 403

  # ─── Admin Endpoint — USER Role Denied ──────────────────────────
  Scenario: Regular USER cannot access admin dashboard (returns 403)
    Given path '/auth/login'
    And request { username: '#(user.username)', password: '#(user.password)' }
    When method POST
    Then status 200
    * def userToken = response.token

    Given path '/admin/dashboard'
    And header Authorization = 'Bearer ' + userToken
    When method GET
    Then status 403

  # ─── Admin Endpoint — ADMIN Role Allowed ────────────────────────
  Scenario: ADMIN user can access admin dashboard
    Given path '/auth/login'
    And request { username: '#(admin.username)', password: '#(admin.password)' }
    When method POST
    Then status 200
    * def adminToken = response.token

    Given path '/admin/dashboard'
    And header Authorization = 'Bearer ' + adminToken
    When method GET
    Then status 200

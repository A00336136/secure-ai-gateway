-- ═══════════════════════════════════════════════════════════════════════════
-- V2 Migration: Seed Karate E2E Test Users
-- Purpose: Provide pre-known credentials for the Karate BDD test suite
--          so tests can run without dynamic user registration.
-- ═══════════════════════════════════════════════════════════════════════════

-- Karate test user (USER role) — password: Karate@123
-- BCrypt hash generated with strength 12 (Spring Boot default)
INSERT INTO users (username, password, role, email, enabled)
VALUES (
    'karatetest',
    '$2a$12$dTVPnpohkEsZ4/yxvKfqc.IwER.NPX28Q/Q24oQIulWrmVC3NtPOC',
    'USER',
    'karatetest@secureai.local',
    true
) ON CONFLICT (username) DO NOTHING;

-- Karate admin user (ADMIN role) — password: Karate@123
-- Used for admin endpoint access control tests in karate-tests
INSERT INTO users (username, password, role, email, enabled)
VALUES (
    'karateadmin',
    '$2a$12$dTVPnpohkEsZ4/yxvKfqc.IwER.NPX28Q/Q24oQIulWrmVC3NtPOC',
    'ADMIN',
    'karateadmin@secureai.local',
    true
) ON CONFLICT (username) DO NOTHING;

-- NOTE: These passwords are intentionally simple test credentials.
--       They are NOT used in production. The .env file controls
--       production credentials and is gitignored.

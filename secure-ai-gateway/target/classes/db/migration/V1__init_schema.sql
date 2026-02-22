-- ═══════════════════════════════════════════════════════
-- V1__init_schema.sql
-- Secure AI Gateway — Initial Schema
-- ═══════════════════════════════════════════════════════

-- Users table (stores credentials for JWT auth)
CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(100) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,   -- BCrypt hash
    email       VARCHAR(255),
    role        VARCHAR(50)  NOT NULL DEFAULT 'USER',
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Audit log table (all AI requests + PII-redacted responses)
CREATE TABLE IF NOT EXISTS audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(100),
    prompt          TEXT,
    response        TEXT,       -- Always PII-redacted
    model           VARCHAR(100),
    pii_detected    BOOLEAN     NOT NULL DEFAULT FALSE,
    rate_limited    BOOLEAN     NOT NULL DEFAULT FALSE,
    react_steps     INT,
    status_code     INT,
    duration_ms     BIGINT,
    ip_address      VARCHAR(50),
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_audit_username   ON audit_logs(username);
CREATE INDEX IF NOT EXISTS idx_audit_created_at ON audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_pii        ON audit_logs(pii_detected) WHERE pii_detected = TRUE;

-- Seed admin user (password: Admin@123 — BCrypt cost 12)
INSERT INTO users (username, password, role, email)
VALUES (
    'admin',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj4J/ky3uh2a',
    'ADMIN',
    'admin@secureai.local'
) ON CONFLICT (username) DO NOTHING;

-- Seed regular test user (password: User@123)
INSERT INTO users (username, password, role, email)
VALUES (
    'testuser',
    '$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'USER',
    'test@secureai.local'
) ON CONFLICT (username) DO NOTHING;

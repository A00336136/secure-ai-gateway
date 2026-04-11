-- ═══════════════════════════════════════════════════════════════
-- PostgreSQL Init Script — Create SonarQube Enterprise Database
-- Runs automatically on first container startup via
-- /docker-entrypoint-initdb.d/
-- ═══════════════════════════════════════════════════════════════

-- SonarQube Enterprise requires its own dedicated database
CREATE DATABASE sonarqube
    WITH OWNER = secureai_user
         ENCODING = 'UTF8'
         LC_COLLATE = 'en_US.utf8'
         LC_CTYPE = 'en_US.utf8'
         TEMPLATE = template0;

-- Grant full privileges to the application user
GRANT ALL PRIVILEGES ON DATABASE sonarqube TO secureai_user;

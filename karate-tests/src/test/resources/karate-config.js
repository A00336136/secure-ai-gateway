/**
 * Karate Global Configuration — SecureAI Gateway E2E Tests
 *
 * Environment resolution order:
 *   1. System property: -Dkarate.base.url=...
 *   2. karate.env (local | docker | ci)
 *   3. Default: http://localhost:8100
 *
 * Usage:
 *   local:  mvn test -Dkarate.env=local
 *   docker: mvn test -Dkarate.env=docker
 *   ci:     mvn test -Dkarate.base.url=http://localhost:8100
 */
function fn() {
    var env = karate.env || 'local';

    var config = {
        env: env,
        baseUrl: 'http://localhost:8100',

        // Pre-seeded test credentials (see V1 migration + V2 seed)
        user: {
            username: 'karatetest',
            password: 'Karate@123'
        },
        admin: {
            username: 'karateadmin',
            password: 'Karate@123'
        },

        // Timeouts — /api/ask runs the full NeMo+LlamaGuard+Presidio chain
        // which takes 12–14s happy-path and can spike above 30s under load
        // (LlamaGuard inference alone observed at 20–30s). 120s leaves headroom.
        connectTimeout: 10000,
        readTimeout: 120000
    };

    // Allow base URL override via system property
    var sysUrl = java.lang.System.getProperty('karate.base.url');
    if (sysUrl) {
        config.baseUrl = sysUrl;
    } else if (env === 'docker') {
        // When running inside Docker network
        config.baseUrl = 'http://secureai-gateway:8080';
    } else if (env === 'ci') {
        config.baseUrl = 'http://localhost:8100';
    }

    // Allow credential overrides via system properties
    var userOverride = java.lang.System.getProperty('karate.user');
    var passOverride = java.lang.System.getProperty('karate.user.password');
    var adminOverride = java.lang.System.getProperty('karate.admin');
    var adminPassOverride = java.lang.System.getProperty('karate.admin.password');

    if (userOverride) config.user.username = userOverride;
    if (passOverride) config.user.password = passOverride;
    if (adminOverride) config.admin.username = adminOverride;
    if (adminPassOverride) config.admin.password = adminPassOverride;

    karate.configure('connectTimeout', config.connectTimeout);
    karate.configure('readTimeout', config.readTimeout);

    karate.log('=== Karate Config ===');
    karate.log('  env     :', env);
    karate.log('  baseUrl :', config.baseUrl);
    karate.log('  user    :', config.user.username);
    karate.log('  admin   :', config.admin.username);
    karate.log('=====================');

    return config;
}

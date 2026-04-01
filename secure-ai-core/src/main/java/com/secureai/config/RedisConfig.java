package com.secureai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;

/**
 * Redis Configuration — Distributed State Management
 *
 * Activated when {@code redis.enabled=true} (set in application-prod.yml
 * or via REDIS_ENABLED environment variable). Falls back to in-memory
 * implementations in dev/test profiles automatically.
 *
 * Provides distributed equivalents for:
 *  - JWT blacklist (JwtUtil): per-JTI TTL keys → auto-expire on token expiry
 *  - Rate limiter (RateLimiterService): Bucket4j Redis → multi-node consistent
 *
 * SOC 2 CC7: system availability — Redis enables horizontal scaling without
 * losing JWT revocation or rate-limit state across restarts/replicas.
 *
 * Standalone mode only. For clustering, switch to
 * RedisClusterConfiguration with appropriate LettuceClusterClientConfiguration.
 */
@Configuration
@ConditionalOnProperty(name = "redis.enabled", havingValue = "true", matchIfMissing = false)
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6380}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.timeout:2000}")
    private long timeoutMs;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        log.info("Configuring Redis connection: {}:{}", host, port);

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        if (password != null && !password.isBlank()) {
            config.setPassword(password);
        }

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(timeoutMs))
                .build();

        return new LettuceConnectionFactory(config, clientConfig);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        log.info("Redis StringRedisTemplate configured — JWT blacklist and rate-limit state will be distributed");
        return new StringRedisTemplate(connectionFactory);
    }
}

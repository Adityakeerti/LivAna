package com.livana.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.servlet.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Infrastructure beans shared across the security layer.
 *
 * Both RateLimitFilter and JwtAuthFilter are @Component beans AND
 * registered in the SecurityFilterChain. To prevent Spring Boot from
 * also auto-registering them as standalone servlet filters (which would
 * cause double-execution or incorrect ordering), we disable their
 * FilterRegistrationBeans here.
 */
@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    // ── Jackson ───────────────────────────────────────────────────────────────

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    // ── Bucket4j ProxyManager (Valkey / Redis-backed rate limiting) ───────────

    /**
     * Lettuce-backed Bucket4j ProxyManager used by RateLimitFilter.
     * Codec: String keys + byte[] values — required by Bucket4j's CAS operations.
     */
    @Bean
    public ProxyManager<String> bucketProxyManager() {
        RedisClient redisClient = RedisClient.create(
                RedisURI.builder()
                        .withHost(redisHost)
                        .withPort(redisPort)
                        .build()
        );

        StatefulRedisConnection<String, byte[]> connection =
                redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

        // Verify Valkey is actually reachable on startup
        try {
            String pong = connection.sync().ping();
            log.info("✅ Valkey connected at {}:{} — PING → {}", redisHost, redisPort, pong);
        } catch (Exception e) {
            log.error("❌ Valkey NOT reachable at {}:{} — rate limiting will fail-open: {}",
                    redisHost, redisPort, e.getMessage());
        }

        return LettuceBasedProxyManager.builderFor(connection)
                .build();
    }

    // ── Prevent double-registration of security filters ───────────────────────

    /**
     * Disable Spring Boot's auto-registration of RateLimitFilter as a servlet filter.
     * It is already added to the SecurityFilterChain in SecurityConfig.
     * Without this, the filter would run TWICE per request.
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitFilter rateLimitFilter) {
        FilterRegistrationBean<RateLimitFilter> bean = new FilterRegistrationBean<>(rateLimitFilter);
        bean.setEnabled(false);
        return bean;
    }

    /**
     * Disable Spring Boot's auto-registration of JwtAuthFilter as a servlet filter.
     * It is already added to the SecurityFilterChain in SecurityConfig.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(
            JwtAuthFilter jwtAuthFilter) {
        FilterRegistrationBean<JwtAuthFilter> bean = new FilterRegistrationBean<>(jwtAuthFilter);
        bean.setEnabled(false);
        return bean;
    }
}


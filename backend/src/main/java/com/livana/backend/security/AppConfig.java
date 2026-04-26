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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Infrastructure beans shared across the security layer.
 * Kept separate from SecurityConfig to avoid circular dependency:
 *   SecurityConfig → RateLimitFilter → ProxyManager (was defined in SecurityConfig)
 */
@Configuration
public class AppConfig {

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

        return LettuceBasedProxyManager.builderFor(connection)
                .build();
    }
}

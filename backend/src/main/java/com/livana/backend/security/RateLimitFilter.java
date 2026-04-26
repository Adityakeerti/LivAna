package com.livana.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Per-IP rate limiting filter using Bucket4j + Valkey.
 *
 * Limit  : 100 requests per 60 seconds (sliding refill)
 * Key    : rate_limit:<client-IP>
 * Storage: Valkey (Redis-compatible)
 *
 * On limit exceeded → 429 Too Many Requests with Retry-After header.
 * This filter runs BEFORE JwtAuthFilter so even unauthenticated request floods are stopped.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final ProxyManager<String> bucketProxyManager;
    private final ObjectMapper         objectMapper;

    private static final int      CAPACITY         = 100;
    private static final Duration REFILL_DURATION  = Duration.ofMinutes(1);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String clientIp = getClientIp(request);
        String bucketKey = "rate_limit:" + clientIp;

        Supplier<BucketConfiguration> configSupplier = () ->
                BucketConfiguration.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(CAPACITY)
                                .refillGreedy(CAPACITY, REFILL_DURATION)
                                .build())
                        .build();

        var bucket = bucketProxyManager.builder().build(bucketKey, configSupplier);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            writeTooManyRequests(response, clientIp);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        // Respect X-Forwarded-For for requests behind a reverse proxy (Nginx / Cloudflare)
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response, String ip) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");

        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status",    429,
                "message",   "Too many requests — please wait 60 seconds"
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

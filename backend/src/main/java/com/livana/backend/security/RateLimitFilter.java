package com.livana.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Multi-tier Bucket4j rate limiting filter backed by Valkey.
 *
 * Key strategy:
 *   Authenticated  →  rl:user:<userId>:<tier>
 *   Unauthenticated →  rl:ip:<clientIp>:<tier>
 *
 * Tiers (per spec):
 *   AUTH_ATTEMPT  — POST /api/auth/**        →  10 req / 15 min  per IP
 *   IMAGE_UPLOAD  — POST /api/**image**      →  20 req / 1 hour  per user
 *   SEARCH        — GET /api/listings/**
 *                   GET /api/businesses/**   →  60 req / 1 min   per user
 *   UNAUTH        — unauthenticated callers  →  30 req / 1 min   per IP
 *   GENERAL       — everything else          → 200 req / 1 min   per user
 *
 * On limit exceeded → HTTP 429 with Retry-After header (exact seconds until refill).
 * This filter runs BEFORE JwtAuthFilter so unauthenticated floods are also caught.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final ProxyManager<String> bucketProxyManager;
    private final ObjectMapper         objectMapper;

    // ── Tier limits ────────────────────────────────────────────────────────────
    private static final Supplier<BucketConfiguration> AUTH_ATTEMPT_CFG = () ->
            BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(10)
                            .refillGreedy(10, Duration.ofMinutes(15))
                            .build())
                    .build();

    private static final Supplier<BucketConfiguration> IMAGE_UPLOAD_CFG = () ->
            BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(20)
                            .refillGreedy(20, Duration.ofHours(1))
                            .build())
                    .build();

    private static final Supplier<BucketConfiguration> SEARCH_CFG = () ->
            BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(60)
                            .refillGreedy(60, Duration.ofMinutes(1))
                            .build())
                    .build();

    private static final Supplier<BucketConfiguration> UNAUTH_CFG = () ->
            BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(30)
                            .refillGreedy(30, Duration.ofMinutes(1))
                            .build())
                    .build();

    private static final Supplier<BucketConfiguration> GENERAL_CFG = () ->
            BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(200)
                            .refillGreedy(200, Duration.ofMinutes(1))
                            .build())
                    .build();

    // ── Filter logic ──────────────────────────────────────────────────────────

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Tier tier = resolveTier(request);
        String key = resolveKey(request, tier);

        log.debug("[RateLimit] {} {} → tier={} key={}",
                request.getMethod(), request.getRequestURI(), tier, key);

        try {
            Supplier<BucketConfiguration> cfg = configFor(tier);
            Bucket bucket = bucketProxyManager.builder().build(key, cfg);

            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (probe.isConsumed()) {
                response.setHeader("X-Rate-Limit-Remaining",
                        String.valueOf(probe.getRemainingTokens()));
                log.debug("[RateLimit] allowed — remaining={}", probe.getRemainingTokens());
                filterChain.doFilter(request, response);
            } else {
                long retryAfterSeconds = Math.max(1,
                        (probe.getNanosToWaitForRefill() / 1_000_000_000L));
                log.warn("[RateLimit] BLOCKED key={} retryAfter={}s", key, retryAfterSeconds);
                writeTooManyRequests(response, retryAfterSeconds);
            }
        } catch (Exception e) {
            // Fail-open: if Valkey is unreachable, log the error and let the request through.
            // This prevents a Redis outage from taking down the entire API.
            log.error("[RateLimit] Valkey error for key={} — failing open: {}", key, e.getMessage());
            filterChain.doFilter(request, response);
        }
    }

    // ── Key resolution ────────────────────────────────────────────────────────

    /**
     * For authenticated requests the key is user-scoped; otherwise IP-scoped.
     * Format: rl:user:<userId>:<tier> | rl:ip:<ip>:<tier>
     */
    private String resolveKey(HttpServletRequest request, Tier tier) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof Long userId) {
            return "rl:user:" + userId + ":" + tier.name();
        }
        return "rl:ip:" + getClientIp(request) + ":" + tier.name();
    }

    // ── Tier resolution ───────────────────────────────────────────────────────

    private enum Tier {
        AUTH_ATTEMPT, IMAGE_UPLOAD, SEARCH, UNAUTH, GENERAL
    }

    private Tier resolveTier(HttpServletRequest request) {
        String method = request.getMethod();
        String path   = request.getRequestURI();

        // Auth attempt: POST /api/auth/** — rate limited per IP regardless of JWT
        if ("POST".equalsIgnoreCase(method) && path.startsWith("/api/auth/")) {
            return Tier.AUTH_ATTEMPT;
        }

        // Image upload: any POST whose path contains "image"
        if ("POST".equalsIgnoreCase(method) && path.contains("image")) {
            return Tier.IMAGE_UPLOAD;
        }

        // Search / browse listings & businesses (public GET endpoints)
        if ("GET".equalsIgnoreCase(method) &&
                (path.startsWith("/api/listings") || path.startsWith("/api/businesses"))) {
            return Tier.SEARCH;
        }

        // Unauthenticated: no valid JWT in SecurityContext yet
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = auth != null
                && auth.isAuthenticated()
                && auth.getPrincipal() instanceof Long;
        if (!isAuthenticated) {
            return Tier.UNAUTH;
        }

        // Default: general authenticated
        return Tier.GENERAL;
    }

    private Supplier<BucketConfiguration> configFor(Tier tier) {
        return switch (tier) {
            case AUTH_ATTEMPT  -> AUTH_ATTEMPT_CFG;
            case IMAGE_UPLOAD  -> IMAGE_UPLOAD_CFG;
            case SEARCH        -> SEARCH_CFG;
            case UNAUTH        -> UNAUTH_CFG;
            case GENERAL       -> GENERAL_CFG;
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response,
                                      long retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

        Map<String, Object> body = Map.of(
                "timestamp",   Instant.now().toString(),
                "status",      429,
                "error",       "Too Many Requests",
                "message",     "Rate limit exceeded — retry after " + retryAfterSeconds + " seconds",
                "retryAfter",  retryAfterSeconds
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

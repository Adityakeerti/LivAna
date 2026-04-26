package com.livana.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Core JWT service — signs access tokens and validates incoming tokens.
 * All protected endpoints depend on this class. Do NOT change signing parameters
 * without bumping a token version; existing tokens will instantly become invalid.
 *
 * Secret must be ≥ 32 chars (HS256). Use 64-char random string in production
 * via environment variable: APP_JWT_SECRET.
 */
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-token-expiry-ms}")
    private long accessTokenExpiry;     // default 900_000 ms = 15 minutes

    // ── Token Generation ──────────────────────────────────────────────────────

    /**
     * Generate a signed JWT access token.
     *
     * @param userId the user's DB primary key
     * @param role   the user's role (STUDENT | BUSINESS_OWNER | ADMIN)
     * @return compact serialized JWT string
     */
    public String generateAccessToken(Long userId, String role) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiry))
                .signWith(getKey())
                .compact();
    }

    // ── Token Validation ──────────────────────────────────────────────────────

    /**
     * Validate the token signature and expiry, then return the claims payload.
     * Throws JwtException (subclass) on invalid/expired token —
     * caught in JwtAuthFilter and converted to 401.
     *
     * @param token raw JWT string (no "Bearer " prefix)
     * @return parsed Claims
     */
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public Long extractUserId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    public String extractRole(Claims claims) {
        return claims.get("role", String.class);
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}

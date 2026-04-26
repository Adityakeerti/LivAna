package com.livana.backend.auth;

import com.livana.backend.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Manages refresh tokens stored in Valkey (Redis-compatible).
 *
 * Key pattern : refresh:<uuid>   →   <userId>
 * TTL         : 7 days
 *
 * Storing in Valkey (not DB) gives us O(1) revocation:
 * to log a user out, just delete the key — no DB writes required.
 * On horizontal scaling, all nodes share the same Valkey cluster.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;

    private static final Duration TTL = Duration.ofDays(7);
    private static final String PREFIX = "refresh:";

    /**
     * Create a new refresh token for the given user and store it in Valkey.
     *
     * @param userId the user's DB primary key
     * @return the opaque UUID refresh token string
     */
    public String create(Long userId) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue()
                .set(PREFIX + token, userId.toString(), TTL);
        return token;
    }

    /**
     * Validate the refresh token and return the associated user ID.
     *
     * @param token the UUID refresh token
     * @return userId stored for this token
     * @throws UnauthorizedException if token is missing or expired
     */
    public Long validateAndGetUserId(String token) {
        String val = redisTemplate.opsForValue().get(PREFIX + token);
        if (val == null) {
            throw new UnauthorizedException("Refresh token invalid or expired");
        }
        return Long.parseLong(val);
    }

    /**
     * Revoke (delete) the refresh token — immediate logout.
     *
     * @param token the UUID refresh token to delete
     */
    public void revoke(String token) {
        redisTemplate.delete(PREFIX + token);
    }
}

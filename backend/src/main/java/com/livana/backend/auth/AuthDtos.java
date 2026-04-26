package com.livana.backend.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Request / Response DTOs for all auth endpoints.
 * Kept in one file to reduce boilerplate for small records.
 */
public final class AuthDtos {

    private AuthDtos() {}

    // ── Requests ──────────────────────────────────────────────────────────────

    public record GoogleAuthRequest(
            @NotBlank(message = "idToken is required")
            String idToken
    ) {}

    public record RefreshRequest(
            @NotBlank(message = "refreshToken is required")
            String refreshToken
    ) {}

    public record LogoutRequest(
            @NotBlank(message = "refreshToken is required")
            String refreshToken
    ) {}

    // ── Responses ─────────────────────────────────────────────────────────────

    public record TokenPairResponse(
            String accessToken,
            String refreshToken,
            UserPayload user
    ) {}

    public record AccessTokenResponse(
            String accessToken
    ) {}

    public record UserPayload(
            Long id,
            String name,
            String email,
            String role,
            String avatarUrl
    ) {}
}

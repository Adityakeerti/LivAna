package com.livana.backend.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.livana.backend.user.User;
import com.livana.backend.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Auth endpoints — the only endpoints that are permit-all in Spring Security.
 *
 * POST /api/auth/google   — exchange Google ID token for LivAna JWT pair
 * POST /api/auth/refresh  — exchange refresh token for new access token
 * POST /api/auth/logout   — revoke refresh token (requires valid JWT)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final GoogleTokenValidator googleTokenValidator;
    private final JwtService           jwtService;
    private final RefreshTokenService  refreshTokenService;
    private final UserService          userService;

    // ── POST /api/auth/google ─────────────────────────────────────────────────

    @PostMapping("/google")
    public ResponseEntity<AuthDtos.TokenPairResponse> loginWithGoogle(
            @Valid @RequestBody AuthDtos.GoogleAuthRequest req) {

        // 1. Validate Google ID token cryptographically
        GoogleIdToken.Payload payload = googleTokenValidator.validate(req.idToken());

        // 2. Upsert user (INSERT ON CONFLICT-style via find-or-create)
        User user = userService.upsertFromGoogle(payload);

        // 3. Issue JWT access token (15 min)
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole().name());

        // 4. Issue refresh token (7 days, stored in Valkey)
        String refreshToken = refreshTokenService.create(user.getId());

        // 5. Build response
        AuthDtos.UserPayload userPayload = new AuthDtos.UserPayload(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                user.getAvatarUrl()
        );

        return ResponseEntity.ok(new AuthDtos.TokenPairResponse(accessToken, refreshToken, userPayload));
    }

    // ── POST /api/auth/refresh ────────────────────────────────────────────────

    @PostMapping("/refresh")
    public ResponseEntity<AuthDtos.AccessTokenResponse> refresh(
            @Valid @RequestBody AuthDtos.RefreshRequest req) {

        // 1. Validate refresh token → get userId (throws 401 if expired/invalid)
        Long userId = refreshTokenService.validateAndGetUserId(req.refreshToken());

        // 2. Load user to get current role (role may have changed since token was issued)
        User user = userService.getById(userId);

        // 3. Generate new access token
        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getRole().name());

        return ResponseEntity.ok(new AuthDtos.AccessTokenResponse(newAccessToken));
    }

    // ── POST /api/auth/logout ─────────────────────────────────────────────────

    /**
     * Logout requires a valid JWT (enforced by SecurityConfig).
     * The userId from the JWT is available via @AuthenticationPrincipal.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody AuthDtos.LogoutRequest req,
            @AuthenticationPrincipal Long userId) {

        refreshTokenService.revoke(req.refreshToken());
        return ResponseEntity.ok().build();
    }
}

package com.livana.backend.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * User self-service endpoints — all require a valid JWT.
 *
 * GET    /api/users/me  — return current user's profile
 * PUT    /api/users/me  — full update: full_name, phone, avatar_url, city_id
 * PATCH  /api/users/me  — partial update (same service method, omit-null fields)
 * DELETE /api/users/me  — soft delete (sets is_active = false)
 *
 * The authenticated user ID is injected via @AuthenticationPrincipal — populated
 * by JwtAuthFilter which stores the userId (Long) as the Spring Security principal.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ── GET /api/users/me ─────────────────────────────────────────────────────

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal Long userId) {
        User user = userService.getById(userId);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    // ── PUT /api/users/me ─────────────────────────────────────────────────────
    // Full update — caller should send all editable fields; omitted fields clear to null.

    @PutMapping("/me")
    public ResponseEntity<UserResponse> putMe(
            @AuthenticationPrincipal Long userId,
            @RequestBody UserService.UpdateProfileRequest req) {

        User updated = userService.updateProfile(userId, req);
        return ResponseEntity.ok(UserResponse.from(updated));
    }

    // ── PATCH /api/users/me ────────────────────────────────────────────────────
    // Partial update — only non-null fields are applied.

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> patchMe(
            @AuthenticationPrincipal Long userId,
            @RequestBody UserService.UpdateProfileRequest req) {

        User updated = userService.updateProfile(userId, req);
        return ResponseEntity.ok(UserResponse.from(updated));
    }

    // ── DELETE /api/users/me ───────────────────────────────────────────────────
    // Soft delete: sets is_active = false. Account data is retained.

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal Long userId) {
        userService.softDelete(userId);
        return ResponseEntity.noContent().build();
    }
}

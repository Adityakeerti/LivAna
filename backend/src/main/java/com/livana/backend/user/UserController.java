package com.livana.backend.user;

import com.livana.backend.exception.UnauthorizedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * User self-service endpoints — all require a valid JWT.
 *
 * GET   /api/users/me       — return current user's profile
 * PATCH /api/users/me       — update phone, cityId, fullName
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

    // ── PATCH /api/users/me ────────────────────────────────────────────────────

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserService.UpdateProfileRequest req) {

        User updated = userService.updateProfile(userId, req);
        return ResponseEntity.ok(UserResponse.from(updated));
    }
}

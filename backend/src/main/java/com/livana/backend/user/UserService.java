package com.livana.backend.user;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.livana.backend.exception.UnauthorizedException;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for user management.
 *
 * upsertFromGoogle is the core auth operation:
 *   - If user exists by google_id → update profile fields (name, avatar may change on re-login)
 *   - If user does not exist      → create new STUDENT account
 *   - Uses @Transactional to ensure the upsert is atomic
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // ── Auth upsert ───────────────────────────────────────────────────────────

    /**
     * Find-or-create a user from a verified Google ID token payload.
     * Updates name and avatar on every login so profile stays in sync with Google.
     *
     * @param payload verified GoogleIdToken.Payload
     * @return the saved User entity
     */
    @Transactional
    public User upsertFromGoogle(GoogleIdToken.Payload payload) {
        String googleId = payload.getSubject();
        String email    = payload.getEmail();
        String fullName = (String) payload.get("name");
        String avatar   = (String) payload.get("picture");

        return userRepository.findByGoogleId(googleId)
                .map(existing -> {
                    // Update mutable fields on re-login
                    existing.setFullName(fullName != null ? fullName : existing.getFullName());
                    existing.setAvatarUrl(avatar);
                    return userRepository.save(existing);
                })
                .orElseGet(() -> {
                    User newUser = new User(googleId, email, fullName != null ? fullName : email, avatar);
                    return userRepository.save(newUser);
                });
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
    }

    /**
     * Update mutable profile fields. Phone and cityId are user-editable.
     * Role, google_id, email are immutable from this endpoint.
     */
    @Transactional
    public User updateProfile(Long userId, UpdateProfileRequest req) {
        User user = getById(userId);
        if (req.fullName()  != null && !req.fullName().isBlank()) user.setFullName(req.fullName());
        if (req.phone()     != null) user.setPhone(req.phone());
        if (req.avatarUrl() != null) user.setAvatarUrl(req.avatarUrl());
        if (req.cityId()    != null) user.setCityId(req.cityId());
        return userRepository.save(user);
    }

    /**
     * Soft-delete: mark account inactive instead of dropping the row.
     * No data is removed — profile remains for audit/compliance purposes.
     */
    @Transactional
    public void softDelete(Long userId) {
        User user = getById(userId);
        user.setActive(false);
        userRepository.save(user);
    }

    // ── Inner DTO ─────────────────────────────────────────────────────────────

    public record UpdateProfileRequest(
            String fullName,
            String phone,
            String avatarUrl,
            Long   cityId
    ) {}
}

package com.livana.backend.user;

/**
 * Public-facing user DTO — never exposes internal fields like google_id or is_active.
 */
public record UserResponse(
        Long id,
        String name,
        String email,
        String role,
        String avatarUrl,
        Long cityId
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                user.getAvatarUrl(),
                user.getCityId()
        );
    }
}

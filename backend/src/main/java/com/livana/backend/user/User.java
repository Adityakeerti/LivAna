package com.livana.backend.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * JPA entity mapping to the `users` table.
 * Column names are explicitly specified to match the snake_case schema exactly —
 * because ddl-auto=validate means Hibernate checks the schema on startup.
 *
 * UserRole ENUM matches the PostgreSQL user_role ENUM:
 *   STUDENT | BUSINESS_OWNER | ADMIN
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_id", nullable = false, unique = true, length = 100)
    private String googleId;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "city_id")
    private Long cityId;

    /**
     * Mapped to the PostgreSQL user_role ENUM type.
     * @JdbcTypeCode(SqlTypes.NAMED_ENUM) tells Hibernate 6+/7 to use
     * the native PostgreSQL enum cast (::user_role) instead of treating
     * it as a plain varchar — without this, inserts fail with a type error.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role", nullable = false, columnDefinition = "user_role")
    private UserRole role = UserRole.STUDENT;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Convenience constructor ────────────────────────────────────────────────

    public User(String googleId, String email, String fullName, String avatarUrl) {
        this.googleId = googleId;
        this.email = email;
        this.fullName = fullName;
        this.avatarUrl = avatarUrl;
        this.role = UserRole.STUDENT;
        this.isActive = true;
    }
}

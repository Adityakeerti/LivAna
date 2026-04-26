package com.livana.backend.user;

/**
 * Mirrors the PostgreSQL user_role ENUM.
 * Values must match exactly — Hibernate writes these strings to the DB.
 */
public enum UserRole {
    STUDENT,
    BUSINESS_OWNER,
    ADMIN
}

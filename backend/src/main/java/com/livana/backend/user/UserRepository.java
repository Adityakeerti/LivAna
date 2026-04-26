package com.livana.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data access layer for users. Backed by Spring Data JPA.
 *
 * google_id is the primary identity anchor — auth upserts always use findByGoogleId.
 * email lookup is used as a fallback / uniqueness check.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}

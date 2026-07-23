package com.mls.logistics.security.repository;

import com.mls.logistics.security.domain.AppUser;
import com.mls.logistics.security.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for AppUser persistence.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /**
     * Finds a user by username.
     * Used by Spring Security during authentication.
     *
     * @param username the username to look up
     * @return the user if found
     */
    Optional<AppUser> findByUsername(String username);

    /**
     * Checks if a username is already registered.
     * Used during registration to prevent duplicates.
     *
     * @param username the username to check
     * @return true if username exists
     */
    boolean existsByUsername(String username);

    /**
     * Checks whether any user exists for the given role.
     *
     * @param role role to check
     * @return true if at least one user exists with the given role
     */
    boolean existsByRole(Role role);

    long countByRoleAndEnabledTrue(Role role);

    long countByEnabledTrue();

    List<AppUser> findAllByRole(Role role);

    /**
     * Looks up a user by email, for the self-service "forgot password" flow.
     * Safe against {@code NonUniqueResultException}: {@code idx_app_users_email}
     * (V9) enforces uniqueness among non-null emails.
     */
    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Recipients for the low-stock/stale-order digest job (Sprint 19):
     * every enabled ADMIN. Callers still filter out accounts with no email
     * set — this query can't do that itself without excluding them from the
     * count elsewhere too.
     */
    List<AppUser> findAllByRoleAndEnabledTrue(Role role);
}
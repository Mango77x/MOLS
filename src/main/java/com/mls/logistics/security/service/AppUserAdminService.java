package com.mls.logistics.security.service;

import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.security.domain.AppUser;
import com.mls.logistics.security.domain.Role;
import com.mls.logistics.security.repository.AppUserRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Admin-only user management operations.
 */
@Service
@Transactional(readOnly = true)
public class AppUserAdminService {

    /**
     * Minimum password length, mirrored from the {@code @Size(min = 12, ...)}
     * on CreateUserRequest/ResetPasswordRequest so this service enforces the
     * same floor even if ever called from somewhere that bypasses DTO
     * validation (previously this was a stale, unreachable 6 here).
     */
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AppUserAdminService(AppUserRepository appUserRepository,
                               PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<AppUser> getAllUsers(Sort sort) {
        return appUserRepository.findAll(sort);
    }

    public Optional<AppUser> getUserById(Long id) {
        return appUserRepository.findById(id);
    }

    @Transactional
    public AppUser createUser(String username, String rawPassword, Role role, String email) {
        String normalizedUsername = username == null ? "" : username.trim();
        if (normalizedUsername.isBlank()) {
            throw new InvalidRequestException("Username is required.");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new InvalidRequestException("Password is required.");
        }
        if (rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidRequestException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (role == null) {
            throw new InvalidRequestException("Role is required.");
        }

        if (appUserRepository.existsByUsername(normalizedUsername)) {
            throw new InvalidRequestException("Username already exists.");
        }

        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail != null && appUserRepository.existsByEmail(normalizedEmail)) {
            throw new InvalidRequestException("A user with this email already exists.");
        }

        try {
            AppUser user = new AppUser(normalizedUsername, passwordEncoder.encode(rawPassword), role);
            user.setEnabledFlag(true);
            user.setEmail(normalizedEmail);
            return appUserRepository.save(user);
        } catch (DataAccessException ex) {
            throw ex;
        }
    }

    @Transactional
    public AppUser updateRole(Long id, Role newRole) {
        if (newRole == null) {
            throw new InvalidRequestException("Role is required.");
        }

        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (user.getRole() == Role.ADMIN && newRole != Role.ADMIN && user.isEnabled()) {
            long enabledAdmins = appUserRepository.countByRoleAndEnabledTrue(Role.ADMIN);
            if (enabledAdmins <= 1) {
                throw new InvalidRequestException(
                    "USER_LAST_ADMIN_REMOVE", null, "You can't remove the last enabled ADMIN user.");
            }
        }

        user.setRole(newRole);
        return appUserRepository.save(user);
    }

    @Transactional
    public AppUser resetPassword(Long id, String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new InvalidRequestException("Password is required.");
        }
        if (rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidRequestException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }

        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        user.setPassword(passwordEncoder.encode(rawPassword));
        // Revokes any token issued before this reset — see JwtAuthFilter
        // and AppUser.passwordVersion's javadoc for why this is a counter
        // bump rather than a timestamp comparison.
        user.setPasswordVersion(user.getPasswordVersion() + 1);
        return appUserRepository.save(user);
    }

    @Transactional
    public AppUser updateEmail(Long id, String email) {
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail != null
                && !normalizedEmail.equalsIgnoreCase(user.getEmail())
                && appUserRepository.existsByEmail(normalizedEmail)) {
            throw new InvalidRequestException("A user with this email already exists.");
        }

        user.setEmail(normalizedEmail);
        return appUserRepository.save(user);
    }

    /** Blank clears the field, mirroring how optional fields are treated everywhere else in the app. */
    private static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    @Transactional
    public AppUser setEnabled(Long id, boolean enabled) {
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (user.getRole() == Role.ADMIN && user.isEnabled() && !enabled) {
            long enabledAdmins = appUserRepository.countByRoleAndEnabledTrue(Role.ADMIN);
            if (enabledAdmins <= 1) {
                throw new InvalidRequestException(
                    "USER_LAST_ADMIN_DISABLE", null, "You can't disable the last enabled ADMIN user.");
            }
        }

        user.setEnabledFlag(enabled);
        return appUserRepository.save(user);
    }
}

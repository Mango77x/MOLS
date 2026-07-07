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
import java.util.Optional;

/**
 * Admin-only user management operations.
 */
@Service
@Transactional(readOnly = true)
public class AppUserAdminService {

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
    public AppUser createUser(String username, String rawPassword, Role role) {
        String normalizedUsername = username == null ? "" : username.trim();
        if (normalizedUsername.isBlank()) {
            throw new InvalidRequestException("Username is required.");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new InvalidRequestException("Password is required.");
        }
        if (rawPassword.length() < 6) {
            throw new InvalidRequestException("Password must be at least 6 characters.");
        }
        if (role == null) {
            throw new InvalidRequestException("Role is required.");
        }

        if (appUserRepository.existsByUsername(normalizedUsername)) {
            throw new InvalidRequestException("Username already exists.");
        }

        try {
            AppUser user = new AppUser(normalizedUsername, passwordEncoder.encode(rawPassword), role);
            user.setEnabledFlag(true);
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
                throw new InvalidRequestException("You can't remove the last enabled ADMIN user.");
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
        if (rawPassword.length() < 6) {
            throw new InvalidRequestException("Password must be at least 6 characters.");
        }

        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        user.setPassword(passwordEncoder.encode(rawPassword));
        return appUserRepository.save(user);
    }

    @Transactional
    public AppUser setEnabled(Long id, boolean enabled) {
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (user.getRole() == Role.ADMIN && user.isEnabled() && !enabled) {
            long enabledAdmins = appUserRepository.countByRoleAndEnabledTrue(Role.ADMIN);
            if (enabledAdmins <= 1) {
                throw new InvalidRequestException("You can't disable the last enabled ADMIN user.");
            }
        }

        user.setEnabledFlag(enabled);
        return appUserRepository.save(user);
    }
}

package com.mls.logistics.dto.response;

import com.mls.logistics.security.domain.AppUser;

/**
 * Data Transfer Object for application user responses.
 *
 * Never exposes the password hash.
 */
public class UserResponse {

    private Long id;
    private String username;
    private String role;
    private boolean enabled;
    private String email;

    public UserResponse() {
    }

    public UserResponse(Long id, String username, String role, boolean enabled, String email) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.enabled = enabled;
        this.email = email;
    }

    public static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole() != null ? user.getRole().name() : null,
                user.isEnabledFlag(),
                user.getEmail()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}

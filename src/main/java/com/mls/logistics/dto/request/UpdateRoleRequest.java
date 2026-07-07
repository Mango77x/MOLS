package com.mls.logistics.dto.request;

import com.mls.logistics.security.domain.Role;
import jakarta.validation.constraints.NotNull;

/**
 * Data Transfer Object for changing a user's role (admin-only).
 */
public class UpdateRoleRequest {

    @NotNull(message = "Role is required")
    private Role role;

    public UpdateRoleRequest() {
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}

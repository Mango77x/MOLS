package com.mls.logistics.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Data Transfer Object for changing a user's role (admin-only).
 *
 * Role is kept as a raw String and parsed via {@code Role.from(String)} —
 * see CreateUserRequest for why.
 */
public class UpdateRoleRequest {

    @NotBlank(message = "Role is required")
    private String role;

    public UpdateRoleRequest() {
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}

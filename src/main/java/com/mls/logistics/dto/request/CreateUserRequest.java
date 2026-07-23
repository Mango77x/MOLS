package com.mls.logistics.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for creating a new application user (admin-only).
 *
 * Role is kept as a raw String, like every other status/enum field in the
 * API, and parsed via {@code Role.from(String)} — a plain enum-typed field
 * would fail Jackson deserialization on an unknown value with a generic
 * error instead of the app's usual friendly "Unknown X: valid values are…"
 * message.
 */
public class CreateUserRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
    private String password;

    @NotBlank(message = "Role is required")
    private String role;

    /**
     * Optional — enables this user for the low-stock/stale-order digest job
     * and the self-service password-reset flow (Sprint 19). Not required at
     * creation; can be set later via {@code PATCH /api/users/{id}/email}.
     */
    @Email(message = "Must be a valid email address")
    private String email;

    public CreateUserRequest() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}

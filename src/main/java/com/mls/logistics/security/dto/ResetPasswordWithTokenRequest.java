package com.mls.logistics.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for {@code POST /api/auth/reset-password} — the
 * self-service flow, redeeming a token minted by {@code forgot-password}.
 * Not to be confused with {@link com.mls.logistics.dto.request.ResetPasswordRequest},
 * the admin-only {@code PATCH /api/users/{id}/password} counterpart.
 */
public class ResetPasswordWithTokenRequest {

    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank(message = "Password is required")
    @Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
    private String newPassword;

    public ResetPasswordWithTokenRequest() {
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}

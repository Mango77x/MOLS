package com.mls.logistics.dto.request;

import jakarta.validation.constraints.Email;

/**
 * Data Transfer Object for setting or clearing a user's email (admin-only).
 *
 * Blank/null clears it — there's no separate "remove email" endpoint, mirroring
 * how the rest of the app treats an empty optional field.
 */
public class UpdateEmailRequest {

    @Email(message = "Must be a valid email address")
    private String email;

    public UpdateEmailRequest() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}

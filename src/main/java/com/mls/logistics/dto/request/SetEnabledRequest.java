package com.mls.logistics.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Data Transfer Object for enabling/disabling a user account (admin-only).
 */
public class SetEnabledRequest {

    @NotNull(message = "Enabled flag is required")
    private Boolean enabled;

    public SetEnabledRequest() {
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}

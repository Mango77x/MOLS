package com.mls.logistics.security.dto;

/**
 * Whether the application still needs its first-run setup (no application
 * users exist yet).
 */
public class SetupStatusResponse {

    private boolean needsSetup;

    public SetupStatusResponse() {
    }

    public SetupStatusResponse(boolean needsSetup) {
        this.needsSetup = needsSetup;
    }

    public boolean isNeedsSetup() {
        return needsSetup;
    }

    public void setNeedsSetup(boolean needsSetup) {
        this.needsSetup = needsSetup;
    }
}

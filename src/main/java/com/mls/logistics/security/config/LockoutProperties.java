package com.mls.logistics.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Brute-force protection settings for login attempts.
 *
 * <p>After {@code maxAttempts} consecutive failed logins for the same
 * username, further attempts are rejected for {@code lockDurationMs}
 * milliseconds. A successful login resets the counter.</p>
 */
@ConfigurationProperties(prefix = "security.lockout")
public class LockoutProperties {

    /** Consecutive failures before the account is temporarily locked. */
    private int maxAttempts = 5;

    /** How long a lockout lasts, in milliseconds (default 15 minutes). */
    private long lockDurationMs = 900_000;

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getLockDurationMs() {
        return lockDurationMs;
    }

    public void setLockDurationMs(long lockDurationMs) {
        this.lockDurationMs = lockDurationMs;
    }
}

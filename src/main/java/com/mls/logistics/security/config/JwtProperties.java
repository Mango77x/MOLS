package com.mls.logistics.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    /**
     * Name of the HttpOnly cookie that can carry the JWT for browser
     * clients (the SPA). API clients keep using the Authorization header.
     */
    public static final String AUTH_COOKIE = "MOLS_AUTH";

    /**
     * Secret key used to sign JWT tokens.
     *
     * Note: JwtService currently expects this value to be Base64 encoded.
     */
    private String secretKey;

    /** Token expiration duration in milliseconds. */
    private long expirationMs;

    /**
     * Password-reset token lifetime in milliseconds — deliberately much
     * shorter than {@link #expirationMs}, since this token grants a
     * password change rather than continued access.
     */
    private long resetTokenExpirationMs;

    /**
     * Whether the auth cookie is flagged {@code Secure} (HTTPS-only).
     * Keep {@code false} only for plain-HTTP local development.
     */
    private boolean cookieSecure;

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }

    public long getResetTokenExpirationMs() {
        return resetTokenExpirationMs;
    }

    public void setResetTokenExpirationMs(long resetTokenExpirationMs) {
        this.resetTokenExpirationMs = resetTokenExpirationMs;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }
}

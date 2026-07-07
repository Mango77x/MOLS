package com.mls.logistics.security.dto;

/**
 * Current-session descriptor returned by {@code GET /api/auth/me}.
 *
 * <p>Lets browser clients restore their session on page load: the JWT lives
 * in an HttpOnly cookie the SPA cannot read, so this endpoint is the only way
 * for it to learn who is logged in and with which role.</p>
 */
public record MeResponse(String username, String role) {
}

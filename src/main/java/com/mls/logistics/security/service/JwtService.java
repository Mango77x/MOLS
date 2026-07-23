package com.mls.logistics.security.service;

import com.mls.logistics.security.config.JwtProperties;
import com.mls.logistics.security.domain.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Service responsible for JWT token generation and validation.
 *
 * Handles all JWT operations:
 * - Token generation with claims (username, role, expiration)
 * - Token validation against user details
 * - Claims extraction (username, expiration)
 */
@Service
public class JwtService {

    /** Marks a token as a password-reset link rather than a session credential. */
    private static final String RESET_PURPOSE_CLAIM = "purpose";
    private static final String RESET_PURPOSE_VALUE = "password_reset";

    /**
     * Deliberately a different claim key than the ordinary session token's
     * {@code pwdVersion} (see {@link #generateToken}) — a reset token must
     * never pass {@link com.mls.logistics.security.filter.JwtAuthFilter}'s
     * revocation check as if it were a session credential. Using a
     * differently-named claim means {@code JwtAuthFilter.extractPasswordVersion}
     * simply never finds it there, so a reset token is always treated as
     * revoked/invalid for that purpose — regardless of this class's own,
     * separate validation in {@link #isPasswordResetTokenValid}.
     */
    private static final String RESET_PWD_VERSION_CLAIM = "resetPwdVersion";

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * Generates a JWT token for the given user.
     *
     * <p>Embeds the user's {@code passwordVersion} at the moment of
     * issuance, so JwtAuthFilter can later reject the token if that no
     * longer matches the current value — i.e. the password was
     * changed/reset since. This is an integer-equality check rather than a
     * timestamp comparison specifically because a timestamp can only be
     * compared at whatever precision it's embedded at (a JWT's {@code iat}
     * is whole-seconds), so two password-set events within the same second
     * — e.g. account creation immediately followed by a reset in a fast
     * test/script — would be indistinguishable; an incrementing counter
     * can't collide like that no matter how fast the events happen.</p>
     *
     * @param userDetails the authenticated user
     * @return signed JWT token string
     */
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", userDetails.getAuthorities()
                .iterator().next().getAuthority());
        if (userDetails instanceof AppUser appUser) {
            claims.put("pwdVersion", appUser.getPasswordVersion());
        }
        return buildToken(claims, userDetails);
    }

    /**
     * Extracts the username from a JWT token.
     *
     * @param token the JWT token string
     * @return username stored in the token
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the {@code pwdVersion} claim embedded at token issuance —
     * see {@link #generateToken}. {@code null} if the token predates this
     * claim existing.
     *
     * @param token the JWT token string
     * @return the embedded passwordVersion, or null if absent
     */
    public Integer extractPasswordVersion(String token) {
        return extractClaim(token, claims -> claims.get("pwdVersion", Integer.class));
    }

    /**
     * Validates a JWT token against the user details.
     *
     * Checks that the username matches and the token is not expired.
     *
     * @param token       the JWT token to validate
     * @param userDetails the user details to validate against
     * @return true if token is valid
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    /**
     * Mints a short-lived, single-use password-reset token for the
     * self-service "forgot password" flow.
     *
     * <p>Embeds the user's current {@code passwordVersion} under a
     * dedicated claim name (not the session token's {@code pwdVersion}) so
     * redeeming it — which bumps {@code passwordVersion}, same as an
     * admin-driven reset — makes any other outstanding copy of this exact
     * token (e.g. from clicking an old email twice) fail
     * {@link #isPasswordResetTokenValid} afterward.</p>
     */
    public String generatePasswordResetToken(AppUser user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(RESET_PURPOSE_CLAIM, RESET_PURPOSE_VALUE);
        claims.put(RESET_PWD_VERSION_CLAIM, user.getPasswordVersion());
        return Jwts.builder()
                .claims(claims)
                .subject(user.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getResetTokenExpirationMs()))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Validates a password-reset token end to end: well-formed and
     * correctly signed, not expired, carries the password-reset purpose
     * claim (not some other token type), names this exact user, and its
     * embedded password version still matches the user's current one
     * (single-use — see {@link #generatePasswordResetToken}).
     */
    public boolean isPasswordResetTokenValid(String token, AppUser user) {
        try {
            Claims claims = extractAllClaims(token);
            boolean correctPurpose = RESET_PURPOSE_VALUE.equals(claims.get(RESET_PURPOSE_CLAIM, String.class));
            boolean correctUser = user.getUsername().equals(claims.getSubject());
            Integer tokenVersion = claims.get(RESET_PWD_VERSION_CLAIM, Integer.class);
            boolean correctVersion = tokenVersion != null && tokenVersion == user.getPasswordVersion();
            boolean notExpired = claims.getExpiration().after(new Date());
            return correctPurpose && correctUser && correctVersion && notExpired;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    // Private helpers

    private String buildToken(Map<String, Object> claims, UserDetails userDetails) {
        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getExpirationMs()))
                .signWith(getSigningKey())
                .compact();
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecretKey());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
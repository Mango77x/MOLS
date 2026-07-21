package com.mls.logistics.security.service;

import com.mls.logistics.security.config.JwtProperties;
import com.mls.logistics.security.domain.AppUser;
import io.jsonwebtoken.Claims;
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
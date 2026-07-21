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
import java.time.ZoneOffset;
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
     * <p>Embeds the user's {@code passwordChangedAt} (as epoch seconds) at
     * the moment of issuance, so JwtAuthFilter can later reject the token if
     * that no longer matches the current value — i.e. the password was
     * changed/reset since. This is an equality check rather than a
     * before/after timestamp comparison specifically to avoid a same-second
     * ordering ambiguity: two events (e.g. login then reset) happening
     * within the same wall-clock second can't be reliably ordered once
     * either timestamp loses sub-second precision, but "did the DB value
     * change at all" has no such ambiguity.</p>
     *
     * @param userDetails the authenticated user
     * @return signed JWT token string
     */
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", userDetails.getAuthorities()
                .iterator().next().getAuthority());
        if (userDetails instanceof AppUser appUser && appUser.getPasswordChangedAt() != null) {
            claims.put("pwdChangedAt", appUser.getPasswordChangedAt().toEpochSecond(ZoneOffset.UTC));
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
     * Extracts the {@code pwdChangedAt} claim (epoch seconds) embedded at
     * token issuance — see {@link #generateToken}. {@code null} if the
     * token predates this claim existing.
     *
     * @param token the JWT token string
     * @return the embedded passwordChangedAt, or null if absent
     */
    public Long extractPasswordChangedAt(String token) {
        return extractClaim(token, claims -> claims.get("pwdChangedAt", Long.class));
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
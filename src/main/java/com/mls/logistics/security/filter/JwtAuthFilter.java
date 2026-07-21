package com.mls.logistics.security.filter;

import com.mls.logistics.security.config.JwtProperties;
import com.mls.logistics.security.domain.AppUser;
import com.mls.logistics.security.service.AppUserService;
import com.mls.logistics.security.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.ZoneOffset;

/**
 * JWT authentication filter.
 *
 * Intercepts every HTTP request exactly once.
 * Extracts the JWT from the Authorization header (API clients) or, when the
 * header is absent, from the HttpOnly auth cookie (browser clients such as
 * the SPA), validates it, and sets the authentication in the SecurityContext
 * so Spring Security knows the request is authenticated.
 *
 * Flow:
 * Request → JwtAuthFilter → validate token → set SecurityContext → Controller
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserService appUserService;

    public JwtAuthFilter(JwtService jwtService, AppUserService appUserService) {
        this.jwtService = jwtService;
        this.appUserService = appUserService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        final String headerToken = extractHeaderToken(request);
        final String jwt = headerToken != null ? headerToken : extractCookieToken(request);

        // Skip filter if the request carries no token at all
        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String username = jwtService.extractUsername(jwt);

            // Only authenticate if username found and not already authenticated
            if (username != null &&
                    SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = appUserService.loadUserByUsername(username);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    if (isNotRevoked(jwt, userDetails)) {
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities());

                        authToken.setDetails(
                                new WebAuthenticationDetailsSource()
                                        .buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    } else if (headerToken != null) {
                        // Syntactically valid, but the account was disabled or
                        // the password was changed/reset after this token was
                        // issued: same 401 treatment as an invalid token for
                        // API clients presenting it explicitly (cookie clients
                        // just fall through to anonymous below).
                        writeUnauthorized(response);
                        return;
                    }
                }
            }
        } catch (JwtException | IllegalArgumentException | AuthenticationException ex) {
            // Expired, malformed, tampered, or otherwise unusable token —
            // or a user that no longer resolves (unknown / temporarily
            // locked account).
            SecurityContextHolder.clearContext();
            if (headerToken != null) {
                // The client explicitly presented credentials: fail fast with
                // a clean 401 instead of letting the exception propagate
                // (which would surface as a 500, since filter exceptions
                // bypass @RestControllerAdvice).
                writeUnauthorized(response);
                return;
            }
            // Cookie tokens are sent implicitly by the browser: treat an
            // unusable one as anonymous so the user can still reach
            // /api/auth/login and start a fresh session (an expired cookie
            // must never lock the user out of logging in again).
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Checks whether a valid-looking token should still be honored: the
     * account must be enabled, and the token's embedded
     * {@code pwdChangedAt} claim (set at issuance, see
     * {@link com.mls.logistics.security.service.JwtService#generateToken})
     * must still match the user's current {@code passwordChangedAt}.
     *
     * <p>{@code isTokenValid} only checks username + expiration, so without
     * this a disabled user or a just-reset password wouldn't actually revoke
     * a token already in the wild — it would keep working until it expired
     * naturally (up to {@code security.jwt.expiration-ms}, 24h by default).
     * This is an equality check against a claim captured at issuance, not a
     * timestamp comparison — an earlier before/after version of this check
     * broke under fast sequential requests (e.g. login immediately followed
     * by a reset) because a JWT's {@code iat} is whole-seconds precision and
     * two events in the same wall-clock second can't be reliably ordered
     * once that precision is lost, but "does the claim still match the
     * current DB value" has no such ambiguity.</p>
     */
    private boolean isNotRevoked(String jwt, UserDetails userDetails) {
        if (!userDetails.isEnabled()) {
            return false;
        }
        if (userDetails instanceof AppUser appUser && appUser.getPasswordChangedAt() != null) {
            Long tokenPasswordChangedAt = jwtService.extractPasswordChangedAt(jwt);
            long currentPasswordChangedAt = appUser.getPasswordChangedAt().toEpochSecond(ZoneOffset.UTC);
            if (tokenPasswordChangedAt == null || tokenPasswordChangedAt != currentPasswordChangedAt) {
                return false;
            }
        }
        return true;
    }

    /**
     * Extracts the Bearer token from the Authorization header (API clients).
     */
    private String extractHeaderToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    /**
     * Extracts the JWT from the HttpOnly auth cookie (browser clients).
     */
    private String extractCookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (JwtProperties.AUTH_COOKIE.equals(cookie.getName())
                        && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Writes a minimal, safe 401 JSON body without leaking token internals.
     */
    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Invalid or expired token\"}");
    }
}
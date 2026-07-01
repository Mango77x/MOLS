package com.mls.logistics.security.filter;

import com.mls.logistics.security.service.AppUserService;
import com.mls.logistics.security.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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

/**
 * JWT authentication filter.
 *
 * Intercepts every HTTP request exactly once.
 * Extracts the JWT from the Authorization header,
 * validates it, and sets the authentication in the SecurityContext
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

        final String authHeader = request.getHeader("Authorization");

        // Skip filter if no Authorization header or not a Bearer token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7); // Remove "Bearer " prefix

        try {
            final String username = jwtService.extractUsername(jwt);

            // Only authenticate if username found and not already authenticated
            if (username != null &&
                    SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = appUserService.loadUserByUsername(username);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities());

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (JwtException | IllegalArgumentException | AuthenticationException ex) {
            // Expired, malformed, tampered, or otherwise unusable token —
            // or a user that no longer resolves (unknown / temporarily
            // locked account). Fail fast with a clean 401 instead of letting
            // the exception propagate (which would surface as a 500 with a
            // stack trace, since filter exceptions bypass @RestControllerAdvice).
            SecurityContextHolder.clearContext();
            writeUnauthorized(response);
            return;
        }

        filterChain.doFilter(request, response);
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
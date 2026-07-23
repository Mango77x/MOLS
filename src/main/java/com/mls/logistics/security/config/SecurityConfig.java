package com.mls.logistics.security.config;

import com.mls.logistics.security.filter.JwtAuthFilter;
import com.mls.logistics.security.service.AppUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Central Spring Security configuration.
 *
 * Defines:
 * - Which endpoints are public vs protected
 * - Role-based access rules per HTTP method
 * - Stateless REST API security (JWT)
 * - The public SPA-shell chain (static assets + client-side routes; the SPA
 *   itself authenticates purely via the JWT-cookie API chain above)
 * - Password encoding with BCrypt
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AppUserService appUserService;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          AppUserService appUserService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.appUserService = appUserService;
    }

    @Bean
        @Order(1)
        public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http)
            throws Exception {
        http
            .securityMatcher(
                "/api/**",
                "/swagger-ui.html", "/swagger-ui/**",
                "/v3/api-docs/**",
                "/actuator/**")
            // Disable CSRF — not needed for stateless REST APIs
            .csrf(AbstractHttpConfigurer::disable)
            // Stateless sessions — JWT handles auth, no server sessions
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints — no token required
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                // First-run setup: by definition no one can be authenticated
                // yet, and the status check itself must be reachable to know
                // whether to show the setup page or the login page.
                .requestMatchers(HttpMethod.GET, "/api/auth/setup-status").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/setup").permitAll()
                // Registration is ADMIN-only (no public self-signup)
                .requestMatchers(HttpMethod.POST, "/api/auth/register").hasRole("ADMIN")
                // Logout only clears the HttpOnly auth cookie — safe to allow
                .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                // Self-service password reset: by definition reachable by
                // someone who isn't authenticated yet.
                .requestMatchers(HttpMethod.POST, "/api/auth/forgot-password").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/reset-password").permitAll()
                // Session descriptor for browser clients (SPA session restore)
                .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                // Block any other auth endpoints by default
                .requestMatchers("/api/auth/**").denyAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                    "/v3/api-docs/**").permitAll()
                // Health/info are public (used by container/orchestrator probes);
                // every other actuator endpoint is ADMIN-only.
                .requestMatchers("/actuator/health", "/actuator/health/**",
                    "/actuator/info").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                // User administration is ADMIN-only end to end, including reads
                // (unlike every other /api/** resource) — account lists/roles
                // are sensitive. Must come before the generic GET rule below.
                .requestMatchers("/api/users/**").hasRole("ADMIN")
                // Read operations — any authenticated user (ADMIN/OPERATOR/AUDITOR)
                .requestMatchers(HttpMethod.GET, "/api/**").authenticated()
                // Operational writes — parity with the UI role model: OPERATOR
                // manages orders (incl. line items) and shipments. Deleting a
                // whole order or shipment stays ADMIN-only (matches the UI's
                // /ui/*/delete rules), but removing a line item is part of
                // editing an order, so OPERATOR may do it.
                .requestMatchers(HttpMethod.POST,
                    "/api/orders/**", "/api/order-items/**", "/api/shipments/**")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers(HttpMethod.PUT,
                    "/api/orders/**", "/api/order-items/**", "/api/shipments/**")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers(HttpMethod.PATCH,
                    "/api/orders/**", "/api/order-items/**", "/api/shipments/**")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers(HttpMethod.DELETE, "/api/order-items/**")
                    .hasAnyRole("ADMIN", "OPERATOR")
                // Remaining write operations — ADMIN only
                .requestMatchers(HttpMethod.POST, "/api/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                // Secure default: anything not explicitly matched above
                // (e.g. HEAD/OPTIONS on /api/**) is denied, not allowed.
                .anyRequest().denyAll())
            // Register JWT filter before Spring's default auth filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

        @Bean
        @Order(2)
        public SecurityFilterChain spaSecurityFilterChain(HttpSecurity http)
            throws Exception {
        http
            .securityMatcher(
                "/",
                "/ui/**",
                "/app",
                "/app/**",
                "/css/**",
                "/js/**",
                "/images/**",
                "/webjars/**",
                "/favicon.ico")
            // No forms, no server-rendered pages, nothing to protect with a
            // session anymore now that Thymeleaf is gone (Sprint 6 cutover) —
            // this chain only serves the public SPA shell/static assets and
            // /ui/** courtesy redirects (LegacyUiRedirectController). Real
            // protection for everything the SPA does is the JWT-cookie API
            // chain above.
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            // Content-Security-Policy for the SPA (Sprint 6, once Thymeleaf's
            // CDN-loaded Bootstrap/Icons were removed there was nothing left
            // to accommodate — everything the React build needs is bundled
            // by Vite). style-src needs 'unsafe-inline': Leaflet positions
            // map tiles via inline `style="transform:..."` attributes for
            // performance, not stylesheets. img-src allows the OpenStreetMap
            // tile hosts the logistics map fetches tiles from.
            .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; "
                    + "script-src 'self'; "
                    + "style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data: https://*.tile.openstreetmap.org; "
                    + "font-src 'self'; "
                    + "connect-src 'self'; "
                    + "object-src 'none'; "
                    + "base-uri 'self'; "
                    + "frame-ancestors 'self'")));

        return http.build();
        }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(
                appUserService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
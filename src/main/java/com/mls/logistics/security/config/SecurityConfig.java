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
 * - Stateful UI security (form login + session)
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
                // Registration is ADMIN-only (no public self-signup)
                .requestMatchers(HttpMethod.POST, "/api/auth/register").hasRole("ADMIN")
                // Logout only clears the HttpOnly auth cookie — safe to allow
                .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
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
        public SecurityFilterChain uiSecurityFilterChain(HttpSecurity http)
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
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/ui/login").permitAll()
                .requestMatchers("/ui/setup").permitAll()
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico").permitAll()
                .requestMatchers("/").permitAll()

                // React SPA shell + static assets: public — the SPA handles
                // login client-side and every API call it makes is protected
                // by the JWT-cookie API chain above.
                .requestMatchers("/app", "/app/**").permitAll()

                // Admin-only module
                .requestMatchers("/ui/users/**").hasRole("ADMIN")

                // Admin-only master data + stock operations (pages)
                .requestMatchers(
                    "/ui/warehouses/new", "/ui/warehouses/*/edit",
                    "/ui/resources/new", "/ui/resources/*/edit",
                    "/ui/vehicles/new", "/ui/vehicles/*/edit",
                    "/ui/units/new", "/ui/units/*/edit",
                    "/ui/stocks/new", "/ui/stocks/*/adjust"
                ).hasRole("ADMIN")

                // Operational screens (pages)
                .requestMatchers(
                    "/ui/orders/new", "/ui/orders/*/edit",
                    "/ui/shipments/new", "/ui/shipments/*/edit"
                ).hasAnyRole("ADMIN", "OPERATOR")

                // UI write actions: allow OPERATOR only for Orders/Shipments
                .requestMatchers(HttpMethod.POST, "/ui/orders/*/delete").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/ui/shipments/*/delete").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/ui/orders/**").hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers(HttpMethod.POST, "/ui/shipments/**").hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers(HttpMethod.POST, "/ui/logout").authenticated()
                .requestMatchers(HttpMethod.POST, "/ui/**").hasRole("ADMIN")

                // Everything else under UI requires authentication
                .requestMatchers("/ui/**").authenticated()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/ui/login")
                .loginProcessingUrl("/ui/login")
                .defaultSuccessUrl("/ui", true)
                .failureUrl("/ui/login?error"))
            .logout(logout -> logout
                .logoutUrl("/ui/logout")
                .logoutSuccessUrl("/ui/login?logout"));

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
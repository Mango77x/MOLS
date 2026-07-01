package com.mls.logistics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Enables JPA auditing so entities can capture WHO performed a change.
 *
 * <p>The auditor is resolved from the Spring Security context: the
 * authenticated username for both API (JWT) and UI (session) requests.
 * Changes made outside a request (startup runners, scheduled jobs) are
 * attributed to {@code system}.</p>
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

    /** Auditor recorded when no authenticated user is present. */
    public static final String SYSTEM_AUDITOR = "system";

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication authentication =
                    SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null
                    || !authentication.isAuthenticated()
                    || authentication instanceof AnonymousAuthenticationToken) {
                return Optional.of(SYSTEM_AUDITOR);
            }
            return Optional.of(authentication.getName());
        };
    }
}

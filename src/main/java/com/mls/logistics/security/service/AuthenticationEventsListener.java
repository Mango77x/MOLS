package com.mls.logistics.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Security event log + brute-force accounting.
 *
 * <p>Listens to Spring Security authentication events (published for both
 * the JWT login endpoint and the UI form login, since both flow through the
 * shared {@code AuthenticationManager}) and:</p>
 * <ul>
 *   <li>writes an auditable log line for every success/failure, and</li>
 *   <li>feeds {@link LoginAttemptService} so repeated failures trigger a
 *       temporary lockout.</li>
 * </ul>
 *
 * <p>Only password logins are counted: JWT-validated API requests do not
 * publish these events.</p>
 */
@Component
public class AuthenticationEventsListener {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationEventsListener.class);

    private final LoginAttemptService loginAttemptService;

    public AuthenticationEventsListener(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        // Only reset the counter for interactive (password) logins.
        if (event.getAuthentication() instanceof UsernamePasswordAuthenticationToken) {
            String username = event.getAuthentication().getName();
            loginAttemptService.recordSuccess(username);
            log.info("SECURITY: successful login for user '{}'", username);
        }
    }

    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        String username = event.getAuthentication().getName();
        loginAttemptService.recordFailure(username);
        log.warn("SECURITY: failed login attempt for user '{}'", username);
    }

    @EventListener
    public void onLockedAccount(AuthenticationFailureLockedEvent event) {
        log.warn("SECURITY: rejected login for locked account '{}'",
                event.getAuthentication().getName());
    }
}

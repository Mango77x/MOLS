package com.mls.logistics.security.service;

import com.mls.logistics.security.repository.AppUserRepository;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Implements UserDetailsService so Spring Security can load
 * users from the database during authentication.
 */
@Service
public class AppUserService implements UserDetailsService {

    private final AppUserRepository appUserRepository;
    private final LoginAttemptService loginAttemptService;

    public AppUserService(AppUserRepository appUserRepository,
                          LoginAttemptService loginAttemptService) {
        this.appUserRepository = appUserRepository;
        this.loginAttemptService = loginAttemptService;
    }

    /**
     * Loads a user by username for Spring Security authentication.
     *
     * <p>Rejects the lookup while the username is under a brute-force
     * lockout — this applies to both password logins and JWT-authenticated
     * requests, so a locked account cannot keep using an existing token.</p>
     *
     * @param username the username to look up
     * @return UserDetails for the found user
     * @throws LockedException if the account is temporarily locked
     * @throws UsernameNotFoundException if user does not exist
     */
    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {
        if (loginAttemptService.isLocked(username)) {
            throw new LockedException(
                    "Account temporarily locked due to repeated failed login attempts");
        }
        return appUserRepository
                .findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + username));
    }
}

package com.mls.logistics.security.service;

import com.mls.logistics.security.config.LockoutProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks consecutive failed login attempts per username and applies a
 * temporary lockout (brute-force protection).
 *
 * <p>State is kept in memory: it protects against online password guessing
 * and resets on application restart, which is an accepted trade-off for a
 * single-instance deployment. Moving this state to the database (or a shared
 * cache) is the natural upgrade for multi-instance deployments.</p>
 *
 * <p>Failures are counted for unknown usernames too, so the lockout does not
 * reveal whether an account exists.</p>
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final LockoutProperties lockoutProperties;
    private final Clock clock;
    private final Map<String, Attempt> attemptsByUsername = new ConcurrentHashMap<>();

    // @Autowired disambiguates: the Clock overload below exists only for tests.
    @Autowired
    public LoginAttemptService(LockoutProperties lockoutProperties) {
        this(lockoutProperties, Clock.systemUTC());
    }

    /** Visible for tests: allows injecting a fixed clock. */
    LoginAttemptService(LockoutProperties lockoutProperties, Clock clock) {
        this.lockoutProperties = lockoutProperties;
        this.clock = clock;
    }

    /**
     * Records a failed login attempt and triggers a lockout once the
     * configured threshold is reached.
     */
    public void recordFailure(String username) {
        String key = normalize(username);
        attemptsByUsername.compute(key, (k, attempt) -> {
            Attempt current = (attempt == null || isExpired(attempt)) ? new Attempt() : attempt;
            current.failures++;
            if (current.failures >= lockoutProperties.getMaxAttempts()) {
                current.lockedUntil = clock.instant()
                        .plusMillis(lockoutProperties.getLockDurationMs());
                log.warn("SECURITY: account '{}' temporarily locked after {} failed login attempts",
                        k, current.failures);
            }
            return current;
        });
    }

    /**
     * Clears the failure counter after a successful login.
     */
    public void recordSuccess(String username) {
        attemptsByUsername.remove(normalize(username));
    }

    /**
     * Returns true while the username is under an active lockout.
     */
    public boolean isLocked(String username) {
        Attempt attempt = attemptsByUsername.get(normalize(username));
        if (attempt == null || attempt.lockedUntil == null) {
            return false;
        }
        if (clock.instant().isAfter(attempt.lockedUntil)) {
            attemptsByUsername.remove(normalize(username));
            return false;
        }
        return true;
    }

    private boolean isExpired(Attempt attempt) {
        return attempt.lockedUntil != null && clock.instant().isAfter(attempt.lockedUntil);
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private static final class Attempt {
        private int failures;
        private Instant lockedUntil;
    }
}

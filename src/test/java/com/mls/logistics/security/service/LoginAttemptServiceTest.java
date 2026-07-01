package com.mls.logistics.security.service;

import com.mls.logistics.security.config.LockoutProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the brute-force lockout accounting.
 */
class LoginAttemptServiceTest {

    private LockoutProperties properties;

    @BeforeEach
    void setUp() {
        properties = new LockoutProperties();
        properties.setMaxAttempts(3);
        properties.setLockDurationMs(60_000);
    }

    @Test
    void failuresBelowThreshold_DoNotLock() {
        LoginAttemptService service = new LoginAttemptService(properties);

        service.recordFailure("alice");
        service.recordFailure("alice");

        assertThat(service.isLocked("alice")).isFalse();
    }

    @Test
    void reachingThreshold_LocksTheAccount() {
        LoginAttemptService service = new LoginAttemptService(properties);

        service.recordFailure("alice");
        service.recordFailure("alice");
        service.recordFailure("alice");

        assertThat(service.isLocked("alice")).isTrue();
        // Other accounts are unaffected
        assertThat(service.isLocked("bob")).isFalse();
    }

    @Test
    void lockout_IsCaseInsensitiveOnUsername() {
        LoginAttemptService service = new LoginAttemptService(properties);

        service.recordFailure("Alice");
        service.recordFailure("ALICE");
        service.recordFailure("alice ");

        assertThat(service.isLocked("alice")).isTrue();
    }

    @Test
    void successfulLogin_ResetsTheCounter() {
        LoginAttemptService service = new LoginAttemptService(properties);

        service.recordFailure("alice");
        service.recordFailure("alice");
        service.recordSuccess("alice");
        service.recordFailure("alice");

        assertThat(service.isLocked("alice")).isFalse();
    }

    @Test
    void lockout_ExpiresAfterConfiguredDuration() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        MutableClock clock = new MutableClock(start);
        LoginAttemptService service = new LoginAttemptService(properties, clock);

        service.recordFailure("alice");
        service.recordFailure("alice");
        service.recordFailure("alice");
        assertThat(service.isLocked("alice")).isTrue();

        clock.advance(Duration.ofMillis(61_000));
        assertThat(service.isLocked("alice")).isFalse();
    }

    /** Simple test clock that can be advanced manually. */
    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }
    }
}

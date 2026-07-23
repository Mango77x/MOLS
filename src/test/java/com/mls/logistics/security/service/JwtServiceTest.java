package com.mls.logistics.security.service;

import com.mls.logistics.security.config.JwtProperties;
import com.mls.logistics.security.domain.AppUser;
import com.mls.logistics.security.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Sprint 19 password-reset token methods — the ordinary
 * session-token methods (generateToken/isTokenValid) already had indirect
 * coverage via JwtAuthFilter's behavior before this, so this focuses on
 * what's new: single-use, purpose-scoped, never-a-session-credential.
 */
class JwtServiceTest {

    private JwtService jwtService;
    private AppUser user;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecretKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        properties.setExpirationMs(86_400_000L);
        properties.setResetTokenExpirationMs(1_800_000L);
        jwtService = new JwtService(properties);

        user = new AppUser("alice", "hash", Role.OPERATOR);
        user.setId(1L);
    }

    @Test
    void isPasswordResetTokenValid_FreshToken_ShouldBeValid() {
        String token = jwtService.generatePasswordResetToken(user);

        assertThat(jwtService.isPasswordResetTokenValid(token, user)).isTrue();
    }

    @Test
    void isPasswordResetTokenValid_ForADifferentUser_ShouldBeInvalid() {
        String token = jwtService.generatePasswordResetToken(user);
        AppUser otherUser = new AppUser("bob", "hash", Role.OPERATOR);
        otherUser.setId(2L);

        assertThat(jwtService.isPasswordResetTokenValid(token, otherUser)).isFalse();
    }

    @Test
    void isPasswordResetTokenValid_AfterPasswordVersionBumps_ShouldBeInvalid() {
        // Simulates redeeming the token once (AppUserAdminService.resetPassword
        // bumps passwordVersion) — a second attempt with the same token, e.g.
        // from clicking an old email link twice, must be rejected.
        String token = jwtService.generatePasswordResetToken(user);
        user.setPasswordVersion(user.getPasswordVersion() + 1);

        assertThat(jwtService.isPasswordResetTokenValid(token, user)).isFalse();
    }

    @Test
    void isPasswordResetTokenValid_MalformedToken_ShouldBeInvalidNotThrow() {
        assertThat(jwtService.isPasswordResetTokenValid("not-a-real-token", user)).isFalse();
    }

    @Test
    void isPasswordResetTokenValid_OrdinarySessionToken_ShouldBeInvalid() {
        // A session token carries no "purpose" claim at all — must not be
        // usable to redeem a password reset just because it's a valid,
        // correctly-signed, unexpired JWT for the right user.
        String sessionToken = jwtService.generateToken(user);

        assertThat(jwtService.isPasswordResetTokenValid(sessionToken, user)).isFalse();
    }

    @Test
    void generatePasswordResetToken_ShouldNeverValidateAsAnOrdinarySessionToken() {
        // The inverse of the above: a reset token must not carry a `pwdVersion`
        // claim matching the user's current version, since JwtAuthFilter
        // would otherwise authenticate its bearer as a full session — see
        // JwtService's own class-level doc on RESET_PWD_VERSION_CLAIM.
        String resetToken = jwtService.generatePasswordResetToken(user);

        assertThat(jwtService.extractPasswordVersion(resetToken)).isNull();
        // isTokenValid only checks username + expiration, so it WOULD pass —
        // it's JwtAuthFilter's separate pwdVersion check (exercised via
        // extractPasswordVersion returning null above) that actually blocks it.
        assertThat(jwtService.isTokenValid(resetToken, user)).isTrue();
    }
}

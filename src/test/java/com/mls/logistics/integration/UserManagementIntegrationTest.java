package com.mls.logistics.integration;

import com.mls.logistics.security.domain.AppUser;
import com.mls.logistics.security.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage for the admin-only /api/users/** endpoints added in
 * Sprint 6: role gating (ADMIN-only including reads, unlike other
 * resources) and the "can't touch the last enabled ADMIN" business rules.
 */
class UserManagementIntegrationTest extends AbstractIntegrationTest {

    @Test
    void operatorAndAuditor_CannotAccessUsers_EvenForReads() {
        String admin = createUserAndLogin("admin-users-seed", Role.ADMIN);
        String operator = createUserAndLogin("operator-users", Role.OPERATOR);
        String auditor = createUserAndLogin("auditor-users", Role.AUDITOR);

        for (String token : new String[]{operator, auditor}) {
            assertThat(restTemplate.exchange("/api/users", HttpMethod.GET,
                    jsonEntity(null, token), String.class).getStatusCode().value()).isEqualTo(403);
            assertThat(restTemplate.postForEntity("/api/users",
                    jsonEntity("{\"username\":\"intruder\",\"password\":\"" + TEST_PASSWORD + "\",\"role\":\"OPERATOR\"}", token),
                    String.class).getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void admin_CanListCreateAndManageUsers() {
        String admin = createUserAndLogin("admin-users-crud", Role.ADMIN);

        long createdId = postForId("/api/users",
                "{\"username\":\"newbie\",\"password\":\"" + TEST_PASSWORD + "\",\"role\":\"AUDITOR\"}", admin);

        var list = getJson("/api/users", admin);
        assertThat(list.isArray()).isTrue();
        assertThat(list.toString()).contains("newbie");

        assertThat(restTemplate.exchange("/api/users/" + createdId + "/role", HttpMethod.PATCH,
                jsonEntity("{\"role\":\"OPERATOR\"}", admin), String.class).getStatusCode().value())
                .isEqualTo(200);

        assertThat(restTemplate.exchange("/api/users/" + createdId + "/password", HttpMethod.PATCH,
                jsonEntity("{\"password\":\"a-brand-new-long-password\"}", admin), String.class)
                .getStatusCode().value()).isEqualTo(200);

        assertThat(restTemplate.exchange("/api/users/" + createdId + "/enabled", HttpMethod.PATCH,
                jsonEntity("{\"enabled\":false}", admin), String.class).getStatusCode().value())
                .isEqualTo(200);

        // The disabled user can no longer log in
        var loginAttempt = restTemplate.postForEntity("/api/auth/login",
                jsonEntity("{\"username\":\"newbie\",\"password\":\"a-brand-new-long-password\"}", null),
                String.class);
        assertThat(loginAttempt.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void admin_CannotDemoteOrDisable_TheLastEnabledAdmin() {
        var admin = createUser("sole-admin", Role.ADMIN);
        String adminToken = loginToken("sole-admin", TEST_PASSWORD);

        assertThat(restTemplate.exchange("/api/users/" + admin.getId() + "/role", HttpMethod.PATCH,
                jsonEntity("{\"role\":\"OPERATOR\"}", adminToken), String.class).getStatusCode().value())
                .isEqualTo(400);

        assertThat(restTemplate.exchange("/api/users/" + admin.getId() + "/enabled", HttpMethod.PATCH,
                jsonEntity("{\"enabled\":false}", adminToken), String.class).getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    void admin_CanDemoteAnAdmin_WhenAnotherAdminRemains() {
        var firstAdmin = createUser("admin-one", Role.ADMIN);
        createUser("admin-two", Role.ADMIN);
        String token = loginToken("admin-one", TEST_PASSWORD);

        assertThat(restTemplate.exchange("/api/users/" + firstAdmin.getId() + "/role", HttpMethod.PATCH,
                jsonEntity("{\"role\":\"AUDITOR\"}", token), String.class).getStatusCode().value())
                .isEqualTo(200);
    }

    @Test
    void disablingAUser_RevokesTheirAlreadyIssuedToken() {
        AppUser target = createUser("revoke-on-disable", Role.OPERATOR);
        String targetToken = loginToken("revoke-on-disable", TEST_PASSWORD);

        // The token works normally before the account is disabled.
        assertThat(restTemplate.exchange("/api/warehouses", HttpMethod.GET,
                jsonEntity(null, targetToken), String.class).getStatusCode().value()).isEqualTo(200);

        String admin = createUserAndLogin("admin-revoke-disable", Role.ADMIN);
        assertThat(restTemplate.exchange("/api/users/" + target.getId() + "/enabled", HttpMethod.PATCH,
                jsonEntity("{\"enabled\":false}", admin), String.class).getStatusCode().value())
                .isEqualTo(200);

        // The same, still-unexpired token must stop working immediately —
        // not just future login attempts.
        assertThat(restTemplate.exchange("/api/warehouses", HttpMethod.GET,
                jsonEntity(null, targetToken), String.class).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void resettingAUsersPassword_RevokesTheirAlreadyIssuedToken() {
        AppUser target = createUser("revoke-on-reset", Role.OPERATOR);
        String targetToken = loginToken("revoke-on-reset", TEST_PASSWORD);

        assertThat(restTemplate.exchange("/api/warehouses", HttpMethod.GET,
                jsonEntity(null, targetToken), String.class).getStatusCode().value()).isEqualTo(200);

        String admin = createUserAndLogin("admin-revoke-reset", Role.ADMIN);
        assertThat(restTemplate.exchange("/api/users/" + target.getId() + "/password", HttpMethod.PATCH,
                jsonEntity("{\"password\":\"a-brand-new-long-password\"}", admin), String.class)
                .getStatusCode().value()).isEqualTo(200);

        // The token minted under the old password must stop working, even
        // though it hasn't expired.
        assertThat(restTemplate.exchange("/api/warehouses", HttpMethod.GET,
                jsonEntity(null, targetToken), String.class).getStatusCode().value()).isEqualTo(401);

        // The new password logs in fine and gets a token that works.
        String freshToken = loginToken("revoke-on-reset", "a-brand-new-long-password");
        assertThat(restTemplate.exchange("/api/warehouses", HttpMethod.GET,
                jsonEntity(null, freshToken), String.class).getStatusCode().value()).isEqualTo(200);
    }
}

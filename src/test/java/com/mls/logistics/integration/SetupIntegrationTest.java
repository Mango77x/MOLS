package com.mls.logistics.integration;

import com.mls.logistics.security.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * First-run setup flow (Sprint 6 React replacement for the old Thymeleaf
 * {@code /ui/setup} page): reports whether the app needs setup, and lets the
 * very first ADMIN be created exactly once.
 */
class SetupIntegrationTest extends AbstractIntegrationTest {

    @Test
    void setupStatus_ReportsNeedsSetup_WhenNoUsersExist() {
        var status = restTemplate.getForEntity("/api/auth/setup-status", String.class);

        assertThat(status.getStatusCode().value()).isEqualTo(200);
        assertThat(readJson(status.getBody()).get("needsSetup").asBoolean()).isTrue();
    }

    @Test
    void setupStatus_ReportsSetupComplete_OnceAUserExists() {
        createUser("existing-admin", Role.ADMIN);

        var status = restTemplate.getForEntity("/api/auth/setup-status", String.class);

        assertThat(readJson(status.getBody()).get("needsSetup").asBoolean()).isFalse();
    }

    @Test
    void setup_WithValidData_CreatesTheFirstAdmin_AndAllowsLogin() {
        var response = restTemplate.postForEntity("/api/auth/setup",
                jsonEntity("{\"username\":\"first-admin\",\"password\":\"" + TEST_PASSWORD + "\"}", null),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(readJson(response.getBody()).get("role").asText()).isEqualTo("ADMIN");

        // The freshly created admin can log in like any other user
        String token = loginToken("first-admin", TEST_PASSWORD);
        assertThat(restTemplate.exchange("/api/users", HttpMethod.GET,
                jsonEntity(null, token), String.class).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void setup_WhenAUserAlreadyExists_IsRejected() {
        createUser("existing-admin", Role.ADMIN);

        var response = restTemplate.postForEntity("/api/auth/setup",
                jsonEntity("{\"username\":\"second-admin\",\"password\":\"" + TEST_PASSWORD + "\"}", null),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void setup_WithWeakPassword_IsRejected() {
        var response = restTemplate.postForEntity("/api/auth/setup",
                jsonEntity("{\"username\":\"first-admin\",\"password\":\"short\"}", null),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }
}

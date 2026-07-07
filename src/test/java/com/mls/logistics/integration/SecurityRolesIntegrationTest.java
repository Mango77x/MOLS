package com.mls.logistics.integration;

import com.mls.logistics.security.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end authorization matrix against the real security filter chains:
 * verifies what each role can and cannot do through the API, plus the
 * brute-force lockout behavior.
 */
class SecurityRolesIntegrationTest extends AbstractIntegrationTest {

    private static final String WAREHOUSE_BODY = "{\"name\":\"Depot\",\"location\":\"Toledo\"}";

    @Test
    void anonymousRequests_AreRejected() {
        assertThat(restTemplate.exchange("/api/warehouses", HttpMethod.GET,
                jsonEntity(null, null), String.class).getStatusCode().value()).isEqualTo(403);

        assertThat(restTemplate.postForEntity("/api/warehouses",
                jsonEntity(WAREHOUSE_BODY, null), String.class).getStatusCode().value()).isEqualTo(403);

        // Registration is not public — no self-signup
        assertThat(restTemplate.postForEntity("/api/auth/register",
                jsonEntity("{\"username\":\"intruder\",\"password\":\"whatever-long-pw\",\"role\":\"ADMIN\"}", null),
                String.class).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void operatorAndAuditor_CanRead_ButNotWrite() {
        String operator = createUserAndLogin("operator-matrix", Role.OPERATOR);
        String auditor = createUserAndLogin("auditor-matrix", Role.AUDITOR);

        for (String token : new String[]{operator, auditor}) {
            assertThat(restTemplate.exchange("/api/warehouses", HttpMethod.GET,
                    jsonEntity(null, token), String.class).getStatusCode().value()).isEqualTo(200);
            assertThat(restTemplate.exchange("/api/movements", HttpMethod.GET,
                    jsonEntity(null, token), String.class).getStatusCode().value()).isEqualTo(200);

            assertThat(restTemplate.postForEntity("/api/warehouses",
                    jsonEntity(WAREHOUSE_BODY, token), String.class).getStatusCode().value()).isEqualTo(403);
            assertThat(restTemplate.exchange("/api/warehouses/1", HttpMethod.DELETE,
                    jsonEntity(null, token), String.class).getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void operator_CanManageOrdersAndShipments_ButNotDeleteThem() {
        String admin = createUserAndLogin("admin-ops-seed", Role.ADMIN);
        String operator = createUserAndLogin("operator-ops", Role.OPERATOR);
        long unitId = seedUnit(admin);
        long warehouseId = seedWarehouse(admin);

        // OPERATOR creates and edits orders (parity with the UI role model)
        var created = restTemplate.postForEntity("/api/orders",
                jsonEntity(orderBody(unitId, warehouseId, "CREATED"), operator), String.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        long orderId = readJson(created.getBody()).get("id").asLong();

        assertThat(restTemplate.exchange("/api/orders/" + orderId, HttpMethod.PUT,
                jsonEntity("{\"status\":\"VALIDATED\"}", operator),
                String.class).getStatusCode().value()).isEqualTo(200);

        // ...but deleting a whole order remains ADMIN-only
        assertThat(restTemplate.exchange("/api/orders/" + orderId, HttpMethod.DELETE,
                jsonEntity(null, operator), String.class).getStatusCode().value()).isEqualTo(403);

        // Master data stays out of reach for OPERATOR
        assertThat(restTemplate.postForEntity("/api/warehouses",
                jsonEntity(WAREHOUSE_BODY, operator), String.class).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void auditor_CannotWriteOrdersOrShipments() {
        String admin = createUserAndLogin("admin-auditor-seed", Role.ADMIN);
        String auditor = createUserAndLogin("auditor-ops", Role.AUDITOR);
        long unitId = seedUnit(admin);
        long warehouseId = seedWarehouse(admin);

        assertThat(restTemplate.postForEntity("/api/orders",
                jsonEntity(orderBody(unitId, warehouseId, "CREATED"), auditor), String.class)
                .getStatusCode().value()).isEqualTo(403);
        assertThat(restTemplate.postForEntity("/api/shipments",
                jsonEntity("{}", auditor), String.class).getStatusCode().value()).isEqualTo(403);
    }

    /** Seeds a unit through the API (as ADMIN) and returns its id. */
    private long seedUnit(String adminToken) {
        var response = restTemplate.postForEntity("/api/units",
                jsonEntity("{\"name\":\"Ops Unit\",\"location\":\"Zaragoza\"}", adminToken), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return readJson(response.getBody()).get("id").asLong();
    }

    /** Seeds a warehouse through the API (as ADMIN) and returns its id. */
    private long seedWarehouse(String adminToken) {
        var response = restTemplate.postForEntity("/api/warehouses",
                jsonEntity(WAREHOUSE_BODY, adminToken), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return readJson(response.getBody()).get("id").asLong();
    }

    private static String orderBody(long unitId, long warehouseId, String status) {
        return "{\"unitId\":" + unitId + ",\"warehouseId\":" + warehouseId +
                ",\"dateCreated\":\"2026-07-06\",\"status\":\"" + status + "\"}";
    }

    @Test
    void admin_CanWrite_AndRegisterUsers() {
        String admin = createUserAndLogin("admin-matrix", Role.ADMIN);

        assertThat(restTemplate.postForEntity("/api/warehouses",
                jsonEntity(WAREHOUSE_BODY, admin), String.class).getStatusCode().value()).isEqualTo(201);

        assertThat(restTemplate.postForEntity("/api/auth/register",
                jsonEntity("{\"username\":\"new-operator\",\"password\":\"" + TEST_PASSWORD + "\",\"role\":\"OPERATOR\"}", admin),
                String.class).getStatusCode().value()).isEqualTo(201);

        // The freshly registered user can log in and read
        String newToken = loginToken("new-operator", TEST_PASSWORD);
        assertThat(restTemplate.exchange("/api/warehouses", HttpMethod.GET,
                jsonEntity(null, newToken), String.class).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void register_RejectsWeakPasswords() {
        String admin = createUserAndLogin("admin-pwpolicy", Role.ADMIN);

        var response = restTemplate.postForEntity("/api/auth/register",
                jsonEntity("{\"username\":\"weak-user\",\"password\":\"short\",\"role\":\"OPERATOR\"}", admin),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void invalidToken_Gets401() {
        var response = restTemplate.exchange("/api/warehouses", HttpMethod.GET,
                jsonEntity(null, "not-a-real-jwt"), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void repeatedFailedLogins_LockTheAccount_AndInvalidateItsTokens() {
        String username = "locky";
        createUser(username, Role.ADMIN);

        // Grab a valid token before the lockout to prove it stops working too
        String preLockToken = loginToken(username, TEST_PASSWORD);

        // 5 wrong passwords -> temporary lockout (each one a generic 401)
        for (int i = 0; i < 5; i++) {
            var attempt = restTemplate.postForEntity("/api/auth/login",
                    jsonEntity("{\"username\":\"" + username + "\",\"password\":\"wrong-password-" + i + "\"}", null),
                    String.class);
            assertThat(attempt.getStatusCode().value()).isEqualTo(401);
        }

        // Even the CORRECT password is now rejected (still a generic 401)
        var lockedLogin = restTemplate.postForEntity("/api/auth/login",
                jsonEntity("{\"username\":\"" + username + "\",\"password\":\"" + TEST_PASSWORD + "\"}", null),
                String.class);
        assertThat(lockedLogin.getStatusCode().value()).isEqualTo(401);

        // And the pre-lockout JWT no longer grants access
        var apiCall = restTemplate.exchange("/api/warehouses", HttpMethod.GET,
                jsonEntity(null, preLockToken), String.class);
        assertThat(apiCall.getStatusCode().value()).isEqualTo(401);
    }
}

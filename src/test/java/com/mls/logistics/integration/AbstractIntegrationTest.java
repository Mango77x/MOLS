package com.mls.logistics.integration;

import com.mls.logistics.repository.MovementRepository;
import com.mls.logistics.repository.OrderItemRepository;
import com.mls.logistics.repository.OrderRepository;
import com.mls.logistics.repository.ResourceRepository;
import com.mls.logistics.repository.ShipmentRepository;
import com.mls.logistics.repository.StockRepository;
import com.mls.logistics.repository.UnitRepository;
import com.mls.logistics.repository.VehicleRepository;
import com.mls.logistics.repository.WarehouseRepository;
import com.mls.logistics.security.domain.AppUser;
import com.mls.logistics.security.domain.Role;
import com.mls.logistics.security.repository.AppUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for end-to-end integration tests.
 *
 * <p>Boots the full application (real Spring context, real Spring Security
 * filter chains, real Flyway migrations) against a disposable PostgreSQL
 * started by Testcontainers. The container follows the singleton pattern:
 * it starts once for the whole test run and is shared by every subclass, so
 * the JVM pays the container cost a single time. {@code @ServiceConnection}
 * wires the datasource automatically — no URL/credentials properties needed.</p>
 *
 * <p>Each test method starts from a clean database (see {@link #cleanDatabase()}),
 * and subclasses seed exactly the data they need through the helpers below.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Boot 4: the TestRestTemplate bean is opt-in (spring-boot-resttestclient module)
@AutoConfigureTestRestTemplate
public abstract class AbstractIntegrationTest {

    /** Password used for all test users — satisfies the 12+ chars policy. */
    protected static final String TEST_PASSWORD = "integration-test-pw";

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    protected final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    protected AppUserRepository appUserRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected MovementRepository movementRepository;
    @Autowired
    protected ShipmentRepository shipmentRepository;
    @Autowired
    protected OrderItemRepository orderItemRepository;
    @Autowired
    protected OrderRepository orderRepository;
    @Autowired
    protected StockRepository stockRepository;
    @Autowired
    protected VehicleRepository vehicleRepository;
    @Autowired
    protected ResourceRepository resourceRepository;
    @Autowired
    protected WarehouseRepository warehouseRepository;
    @Autowired
    protected UnitRepository unitRepository;

    /**
     * Wipes all application data in FK-dependency order so every test method
     * starts from a known-empty state. (This is a test database — the
     * append-only audit rule applies to application behavior, not test setup.)
     */
    @BeforeEach
    void cleanDatabase() {
        movementRepository.deleteAll();
        shipmentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        stockRepository.deleteAll();
        vehicleRepository.deleteAll();
        resourceRepository.deleteAll();
        warehouseRepository.deleteAll();
        unitRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    /**
     * Creates an enabled application user directly in the database.
     */
    protected AppUser createUser(String username, Role role) {
        AppUser user = new AppUser(username, passwordEncoder.encode(TEST_PASSWORD), role);
        return appUserRepository.save(user);
    }

    /**
     * Logs in through the real endpoint and returns the issued JWT.
     */
    protected String loginToken(String username, String password) {
        var response = restTemplate.postForEntity(
                "/api/auth/login",
                jsonEntity("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}", null),
                String.class);
        assertThat(response.getStatusCode().value())
                .as("login for user '%s'", username)
                .isEqualTo(200);
        return readJson(response.getBody()).get("token").asText();
    }

    /**
     * Convenience: creates the user and returns a fresh JWT for it.
     */
    protected String createUserAndLogin(String username, Role role) {
        createUser(username, role);
        return loginToken(username, TEST_PASSWORD);
    }

    /**
     * Wraps a JSON body (may be null) with content-type and optional bearer token.
     */
    protected HttpEntity<String> jsonEntity(String body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(body, headers);
    }

    /**
     * Parses a JSON response body, failing the test on malformed content.
     */
    protected JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new AssertionError("Response body is not valid JSON: " + body, e);
        }
    }
}

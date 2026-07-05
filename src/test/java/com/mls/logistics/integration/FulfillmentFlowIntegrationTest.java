package com.mls.logistics.integration;

import com.mls.logistics.security.domain.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of the core business flow against a real database:
 *
 * <pre>
 * master data → stock → order + items → shipment → DELIVERED
 * </pre>
 *
 * and verifies the resulting state the way an auditor would:
 * stock deducted, order completed, movements recorded with actor and
 * traceability links, and the audit trail impossible to rewrite.
 */
class FulfillmentFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void deliveringAShipment_FulfillsTheOrder_AndAuditsEveryStep() {
        String admin = "admin-fulfill";
        String token = createUserAndLogin(admin, Role.ADMIN);

        // --- Master data ---
        long warehouseId = postForId("/api/warehouses",
                "{\"name\":\"Central\",\"location\":\"Madrid\"}", token);
        long unitId = postForId("/api/units",
                "{\"name\":\"Alpha Unit\",\"location\":\"Sevilla\"}", token);
        long resourceId = postForId("/api/resources",
                "{\"name\":\"Field ration\",\"type\":\"SUPPLY\",\"criticality\":\"HIGH\"}", token);
        long vehicleId = postForId("/api/vehicles",
                "{\"type\":\"LAND\",\"capacity\":1000,\"status\":\"AVAILABLE\"}", token);

        // --- Stock: creating with quantity > 0 must record an ENTRY movement ---
        long stockId = postForId("/api/stocks",
                "{\"resourceId\":" + resourceId + ",\"warehouseId\":" + warehouseId + ",\"quantity\":100}", token);

        // --- Order with one item ---
        long orderId = postForId("/api/orders",
                "{\"unitId\":" + unitId + ",\"dateCreated\":\"2026-07-01\",\"status\":\"CREATED\"}", token);
        postForId("/api/order-items",
                "{\"orderId\":" + orderId + ",\"resourceId\":" + resourceId + ",\"quantity\":30}", token);

        // --- Shipment: planned, then delivered ---
        long shipmentId = postForId("/api/shipments",
                "{\"orderId\":" + orderId + ",\"vehicleId\":" + vehicleId +
                        ",\"warehouseId\":" + warehouseId + ",\"status\":\"PLANNED\"}", token);

        var deliver = restTemplate.exchange("/api/shipments/" + shipmentId, HttpMethod.PUT,
                jsonEntity("{\"status\":\"DELIVERED\"}", token), String.class);
        assertThat(deliver.getStatusCode().value()).isEqualTo(200);

        // --- Stock was deducted ---
        var stock = getJson("/api/stocks/" + stockId, token);
        assertThat(stock.get("quantity").asInt()).isEqualTo(70);

        // --- Order was completed ---
        var order = getJson("/api/orders/" + orderId, token);
        assertThat(order.get("status").asText()).isEqualTo("COMPLETED");

        // --- Audit trail: ENTRY (initial stock) + EXIT (fulfillment), with actor ---
        var movements = getJson("/api/movements", token);
        assertThat(movements.size()).isEqualTo(2);

        JsonNode entry = findByType(movements, "ENTRY");
        assertThat(entry.get("quantity").asInt()).isEqualTo(100);
        assertThat(entry.get("createdBy").asText()).isEqualTo(admin);

        JsonNode exit = findByType(movements, "EXIT");
        assertThat(exit.get("quantity").asInt()).isEqualTo(30);
        assertThat(exit.get("createdBy").asText()).isEqualTo(admin);
        assertThat(exit.get("orderId").asLong()).isEqualTo(orderId);
        assertThat(exit.get("shipmentId").asLong()).isEqualTo(shipmentId);
        assertThat(exit.get("reason").asText()).isEqualTo("Shipment delivered");

        // --- The trail cannot be rewritten, even by ADMIN ---
        long exitId = exit.get("id").asLong();
        assertThat(restTemplate.exchange("/api/movements/" + exitId, HttpMethod.DELETE,
                jsonEntity(null, token), String.class).getStatusCode().value()).isEqualTo(405);

        assertThat(restTemplate.exchange("/api/shipments/" + shipmentId, HttpMethod.PUT,
                jsonEntity("{\"status\":\"PLANNED\"}", token), String.class)
                .getStatusCode().value()).isEqualTo(400);

        assertThat(restTemplate.exchange("/api/stocks/" + stockId, HttpMethod.DELETE,
                jsonEntity(null, token), String.class).getStatusCode().value()).isEqualTo(400);

        assertThat(restTemplate.exchange("/api/orders/" + orderId, HttpMethod.DELETE,
                jsonEntity(null, token), String.class).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void orderItemExceedingAvailableStock_IsRejectedWith409() {
        String token = createUserAndLogin("admin-overbook", Role.ADMIN);

        long warehouseId = postForId("/api/warehouses",
                "{\"name\":\"Depot\",\"location\":\"Burgos\"}", token);
        long unitId = postForId("/api/units",
                "{\"name\":\"Bravo Unit\",\"location\":\"Burgos\"}", token);
        long resourceId = postForId("/api/resources",
                "{\"name\":\"Spare part\",\"type\":\"PART\",\"criticality\":\"LOW\"}", token);
        postForId("/api/stocks",
                "{\"resourceId\":" + resourceId + ",\"warehouseId\":" + warehouseId + ",\"quantity\":5}", token);
        long orderId = postForId("/api/orders",
                "{\"unitId\":" + unitId + ",\"dateCreated\":\"2026-07-01\",\"status\":\"CREATED\"}", token);

        var response = restTemplate.postForEntity("/api/order-items",
                jsonEntity("{\"orderId\":" + orderId + ",\"resourceId\":" + resourceId + ",\"quantity\":6}", token),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void invalidStatusTransition_IsRejectedWith400() {
        String token = createUserAndLogin("admin-transitions", Role.ADMIN);

        long unitId = postForId("/api/units",
                "{\"name\":\"Charlie Unit\",\"location\":\"Zaragoza\"}", token);
        long orderId = postForId("/api/orders",
                "{\"unitId\":" + unitId + ",\"dateCreated\":\"2026-07-01\",\"status\":\"COMPLETED\"}", token);

        // COMPLETED is terminal: reopening must be rejected by the state machine
        var response = restTemplate.exchange("/api/orders/" + orderId, HttpMethod.PUT,
                jsonEntity("{\"status\":\"CREATED\"}", token), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("Invalid order status transition");
    }

    // --- helpers ---

    private long postForId(String path, String body, String token) {
        var response = restTemplate.postForEntity(path, jsonEntity(body, token), String.class);
        assertThat(response.getStatusCode().value())
                .as("POST %s -> %s", path, response.getBody())
                .isEqualTo(201);
        return readJson(response.getBody()).get("id").asLong();
    }

    private JsonNode getJson(String path, String token) {
        var response = restTemplate.exchange(path, HttpMethod.GET, jsonEntity(null, token), String.class);
        assertThat(response.getStatusCode().value()).as("GET %s", path).isEqualTo(200);
        return readJson(response.getBody());
    }

    private JsonNode findByType(JsonNode movements, String type) {
        for (JsonNode movement : movements) {
            if (type.equals(movement.get("type").asText())) {
                return movement;
            }
        }
        throw new AssertionError("No movement of type " + type + " in: " + movements);
    }
}

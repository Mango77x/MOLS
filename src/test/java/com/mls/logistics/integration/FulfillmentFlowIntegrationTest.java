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
                "{\"unitId\":" + unitId + ",\"warehouseId\":" + warehouseId +
                        ",\"dateCreated\":\"2026-07-01\",\"status\":\"CREATED\"}", token);
        long orderItemId = postForId("/api/order-items",
                "{\"orderId\":" + orderId + ",\"resourceId\":" + resourceId + ",\"quantity\":30}", token);

        // --- Shipment: planned, then delivered. Inherits its warehouse from
        // the order automatically — no warehouseId in the request. Carries
        // the order item in full. ---
        long shipmentId = postForId("/api/shipments",
                "{\"orderId\":" + orderId + ",\"vehicleId\":" + vehicleId + ",\"status\":\"PLANNED\"," +
                        "\"items\":[{\"orderItemId\":" + orderItemId + ",\"quantity\":30}]}", token);

        var deliver = restTemplate.exchange("/api/shipments/" + shipmentId, HttpMethod.PUT,
                jsonEntity("{\"status\":\"DELIVERED\"}", token), String.class);
        assertThat(deliver.getStatusCode().value()).isEqualTo(200);

        // --- Shipment reads (single and filtered list) serialize `items` without
        // a LazyInitializationException — regression coverage for a real bug found
        // manually: the session used to build the response had already closed. ---
        var shipmentById = getJson("/api/shipments/" + shipmentId, token);
        assertThat(shipmentById.get("items").size()).isEqualTo(1);
        assertThat(shipmentById.get("items").get(0).get("quantity").asInt()).isEqualTo(30);

        var shipmentsByOrder = getJson("/api/shipments?orderId=" + orderId, token);
        assertThat(shipmentsByOrder.get(0).get("items").size()).isEqualTo(1);

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
    void deliveringOneOfTwoShipments_PartiallyShipsTheOrder_ThenCompletesOnTheSecond() {
        String admin = "admin-partial";
        String token = createUserAndLogin(admin, Role.ADMIN);

        long warehouseId = postForId("/api/warehouses",
                "{\"name\":\"North Depot\",\"location\":\"Bilbao\"}", token);
        long unitId = postForId("/api/units",
                "{\"name\":\"Delta Unit\",\"location\":\"Bilbao\"}", token);
        long resourceAId = postForId("/api/resources",
                "{\"name\":\"Rations\",\"type\":\"SUPPLY\",\"criticality\":\"HIGH\"}", token);
        long resourceBId = postForId("/api/resources",
                "{\"name\":\"Ammo\",\"type\":\"SUPPLY\",\"criticality\":\"HIGH\"}", token);
        long vehicleId = postForId("/api/vehicles",
                "{\"type\":\"LAND\",\"capacity\":1000,\"status\":\"AVAILABLE\"}", token);

        long stockAId = postForId("/api/stocks",
                "{\"resourceId\":" + resourceAId + ",\"warehouseId\":" + warehouseId + ",\"quantity\":50}", token);
        long stockBId = postForId("/api/stocks",
                "{\"resourceId\":" + resourceBId + ",\"warehouseId\":" + warehouseId + ",\"quantity\":50}", token);

        long orderId = postForId("/api/orders",
                "{\"unitId\":" + unitId + ",\"warehouseId\":" + warehouseId +
                        ",\"dateCreated\":\"2026-07-01\",\"status\":\"CREATED\"}", token);
        long itemAId = postForId("/api/order-items",
                "{\"orderId\":" + orderId + ",\"resourceId\":" + resourceAId + ",\"quantity\":10}", token);
        long itemBId = postForId("/api/order-items",
                "{\"orderId\":" + orderId + ",\"resourceId\":" + resourceBId + ",\"quantity\":20}", token);

        // Two shipments: the first carries only item A.
        long shipment1Id = postForId("/api/shipments",
                "{\"orderId\":" + orderId + ",\"vehicleId\":" + vehicleId + ",\"status\":\"PLANNED\"," +
                        "\"items\":[{\"orderItemId\":" + itemAId + ",\"quantity\":10}]}", token);
        long shipment2Id = postForId("/api/shipments",
                "{\"orderId\":" + orderId + ",\"vehicleId\":" + vehicleId + ",\"status\":\"PLANNED\"," +
                        "\"items\":[{\"orderItemId\":" + itemBId + ",\"quantity\":20}]}", token);

        // Deliver the first shipment only.
        var deliver1 = restTemplate.exchange("/api/shipments/" + shipment1Id, HttpMethod.PUT,
                jsonEntity("{\"status\":\"DELIVERED\"}", token), String.class);
        assertThat(deliver1.getStatusCode().value()).isEqualTo(200);

        // Only item A's stock moved; item B's is untouched.
        assertThat(getJson("/api/stocks/" + stockAId, token).get("quantity").asInt()).isEqualTo(40);
        assertThat(getJson("/api/stocks/" + stockBId, token).get("quantity").asInt()).isEqualTo(50);

        // The order reflects partial progress, not completion.
        assertThat(getJson("/api/orders/" + orderId, token).get("status").asText()).isEqualTo("PARTIALLY_SHIPPED");

        // Deliver the second (and last) shipment.
        var deliver2 = restTemplate.exchange("/api/shipments/" + shipment2Id, HttpMethod.PUT,
                jsonEntity("{\"status\":\"DELIVERED\"}", token), String.class);
        assertThat(deliver2.getStatusCode().value()).isEqualTo(200);

        assertThat(getJson("/api/stocks/" + stockBId, token).get("quantity").asInt()).isEqualTo(30);
        assertThat(getJson("/api/orders/" + orderId, token).get("status").asText()).isEqualTo("COMPLETED");

        // Both deliveries recorded their own EXIT movement, scoped to their own shipment.
        var movements = getJson("/api/movements", token);
        assertThat(movements.size()).isEqualTo(4); // 2 ENTRY (initial stock) + 2 EXIT (deliveries)
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
                "{\"unitId\":" + unitId + ",\"warehouseId\":" + warehouseId +
                        ",\"dateCreated\":\"2026-07-01\",\"status\":\"CREATED\"}", token);

        var response = restTemplate.postForEntity("/api/order-items",
                jsonEntity("{\"orderId\":" + orderId + ",\"resourceId\":" + resourceId + ",\"quantity\":6}", token),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void invalidStatusTransition_IsRejectedWith400() {
        String token = createUserAndLogin("admin-transitions", Role.ADMIN);

        long warehouseId = postForId("/api/warehouses",
                "{\"name\":\"Zaragoza Depot\",\"location\":\"Zaragoza\"}", token);
        long unitId = postForId("/api/units",
                "{\"name\":\"Charlie Unit\",\"location\":\"Zaragoza\"}", token);
        long orderId = postForId("/api/orders",
                "{\"unitId\":" + unitId + ",\"warehouseId\":" + warehouseId +
                        ",\"dateCreated\":\"2026-07-01\",\"status\":\"COMPLETED\"}", token);

        // COMPLETED is terminal: reopening must be rejected by the state machine
        var response = restTemplate.exchange("/api/orders/" + orderId, HttpMethod.PUT,
                jsonEntity("{\"status\":\"CREATED\"}", token), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("Invalid order status transition");
    }

    // --- helpers ---

    private JsonNode findByType(JsonNode movements, String type) {
        for (JsonNode movement : movements) {
            if (type.equals(movement.get("type").asText())) {
                return movement;
            }
        }
        throw new AssertionError("No movement of type " + type + " in: " + movements);
    }
}

package com.mls.logistics.integration;

import com.mls.logistics.security.domain.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of {@code GET /api/map} against a real database: pins and
 * routes must reflect seeded coordinates, entries without coordinates must
 * be omitted, and the endpoint must be readable by every authenticated role
 * (it is a GET like any other).
 */
class MapIntegrationTest extends AbstractIntegrationTest {

    @Test
    void map_ResolvesPinsAndRoutes_AndOmitsEntriesWithoutCoordinates() {
        String admin = createUserAndLogin("admin-map", Role.ADMIN);

        // Origin warehouse with coordinates and a stock row below the critical threshold (5)
        long madridWarehouseId = postForId("/api/warehouses",
                "{\"name\":\"Central Depot\",\"location\":\"Madrid\","
                        + "\"latitude\":40.4168,\"longitude\":-3.7038}", admin);
        long resourceId = postForId("/api/resources",
                "{\"name\":\"Field ration\",\"type\":\"SUPPLY\",\"criticality\":\"HIGH\"}", admin);
        postForId("/api/stocks",
                "{\"resourceId\":" + resourceId + ",\"warehouseId\":" + madridWarehouseId + ",\"quantity\":3}", admin);

        // A second warehouse with no coordinates — must not appear as a pin
        postForId("/api/warehouses", "{\"name\":\"Uncharted Depot\",\"location\":\"Unknown\"}", admin);

        // Destination unit with coordinates
        long sevillaUnitId = postForId("/api/units",
                "{\"name\":\"Alpha Unit\",\"location\":\"Sevilla\","
                        + "\"latitude\":37.3891,\"longitude\":-5.9845}", admin);
        // A second unit with no coordinates — must not appear as a pin
        long uncoordinatedUnitId = postForId("/api/units", "{\"name\":\"Beta Unit\",\"location\":\"Unknown\"}", admin);

        long vehicleId = postForId("/api/vehicles",
                "{\"type\":\"TRUCK\",\"capacity\":100,\"status\":\"AVAILABLE\"}", admin);

        long orderToSevilla = postForId("/api/orders",
                "{\"unitId\":" + sevillaUnitId + ",\"warehouseId\":" + madridWarehouseId +
                        ",\"dateCreated\":\"2026-07-01\",\"status\":\"CREATED\"}", admin);
        long itemToSevillaId = postForId("/api/order-items",
                "{\"orderId\":" + orderToSevilla + ",\"resourceId\":" + resourceId + ",\"quantity\":1}", admin);
        postForId("/api/shipments",
                "{\"orderId\":" + orderToSevilla + ",\"vehicleId\":" + vehicleId + ",\"status\":\"IN_TRANSIT\"," +
                        "\"items\":[{\"orderItemId\":" + itemToSevillaId + ",\"quantity\":1}]}", admin);

        // A shipment to the uncoordinated unit — its route must be omitted
        long orderToUncoordinated = postForId("/api/orders",
                "{\"unitId\":" + uncoordinatedUnitId + ",\"warehouseId\":" + madridWarehouseId +
                        ",\"dateCreated\":\"2026-07-01\",\"status\":\"CREATED\"}", admin);
        long itemToUncoordinatedId = postForId("/api/order-items",
                "{\"orderId\":" + orderToUncoordinated + ",\"resourceId\":" + resourceId + ",\"quantity\":1}", admin);
        postForId("/api/shipments",
                "{\"orderId\":" + orderToUncoordinated + ",\"vehicleId\":" + vehicleId + ",\"status\":\"PLANNED\"," +
                        "\"items\":[{\"orderItemId\":" + itemToUncoordinatedId + ",\"quantity\":1}]}", admin);

        String auditor = createUserAndLogin("auditor-map", Role.AUDITOR);
        var map = getJson("/api/map", auditor);

        // Only the warehouse/unit with coordinates become pins
        assertThat(map.get("warehouses").size()).isEqualTo(1);
        assertThat(map.get("warehouses").get(0).get("name").asText()).isEqualTo("Central Depot");
        assertThat(map.get("warehouses").get(0).get("latitude").asDouble()).isEqualTo(40.4168);
        assertThat(map.get("warehouses").get(0).get("stockStatus").asText()).isEqualTo("CRITICAL");

        assertThat(map.get("units").size()).isEqualTo(1);
        assertThat(map.get("units").get(0).get("name").asText()).isEqualTo("Alpha Unit");

        // Only the route with both endpoints resolved appears
        assertThat(map.get("shipments").size()).isEqualTo(1);
        var route = map.get("shipments").get(0);
        assertThat(route.get("status").asText()).isEqualTo("IN_TRANSIT");
        assertThat(route.get("origin").get("name").asText()).isEqualTo("Central Depot");
        assertThat(route.get("destination").get("name").asText()).isEqualTo("Alpha Unit");
    }
}

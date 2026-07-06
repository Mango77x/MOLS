package com.mls.logistics.integration;

import com.mls.logistics.security.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end checks of the opt-in pagination and filtering contract of the
 * list endpoints against a real database: plain-array legacy behavior,
 * envelope shape, filter semantics, and parameter validation.
 */
class ListFilteringIntegrationTest extends AbstractIntegrationTest {

    @Test
    void listEndpoints_FilterAndPaginate_AgainstRealData() {
        String admin = createUserAndLogin("admin-filtering", Role.ADMIN);

        // --- Seed: two warehouses, one unit, two orders in different states ---
        long centralId = postForId("/api/warehouses",
                "{\"name\":\"Central Depot\",\"location\":\"Madrid\",\"latitude\":40.4168,\"longitude\":-3.7038}", admin);
        postForId("/api/warehouses",
                "{\"name\":\"Forward Base\",\"location\":\"Burgos\"}", admin);
        long unitId = postForId("/api/units",
                "{\"name\":\"Alpha Unit\",\"location\":\"Sevilla\"}", admin);
        long resourceId = postForId("/api/resources",
                "{\"name\":\"Field ration\",\"type\":\"SUPPLY\",\"criticality\":\"HIGH\"}", admin);
        postForId("/api/stocks",
                "{\"resourceId\":" + resourceId + ",\"warehouseId\":" + centralId + ",\"quantity\":50}", admin);
        long createdOrderId = postForId("/api/orders",
                "{\"unitId\":" + unitId + ",\"dateCreated\":\"2026-07-01\",\"status\":\"CREATED\"}", admin);
        long cancelledOrderId = postForId("/api/orders",
                "{\"unitId\":" + unitId + ",\"dateCreated\":\"2026-07-02\",\"status\":\"CREATED\"}", admin);
        restTemplate.exchange("/api/orders/" + cancelledOrderId, HttpMethod.PUT,
                jsonEntity("{\"status\":\"CANCELLED\"}", admin), String.class);

        // --- Legacy contract: no params -> plain JSON array ---
        var plain = readJson(restTemplate.exchange("/api/warehouses", HttpMethod.GET,
                jsonEntity(null, admin), String.class).getBody());
        assertThat(plain.isArray()).isTrue();
        assertThat(plain.size()).isEqualTo(2);

        // --- Coordinates (V3) survive the roundtrip through the database ---
        var central = getJson("/api/warehouses/" + centralId, admin);
        assertThat(central.get("latitude").asDouble()).isEqualTo(40.4168);
        assertThat(central.get("longitude").asDouble()).isEqualTo(-3.7038);

        // --- Pagination: envelope shape and page math ---
        var page = readJson(restTemplate.exchange("/api/warehouses?page=0&size=1&sort=name,asc",
                HttpMethod.GET, jsonEntity(null, admin), String.class).getBody());
        assertThat(page.get("content").size()).isEqualTo(1);
        assertThat(page.get("content").get(0).get("name").asText()).isEqualTo("Central Depot");
        assertThat(page.get("totalElements").asLong()).isEqualTo(2);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);

        // --- Name filter alone also returns the envelope ---
        var filtered = readJson(restTemplate.exchange("/api/warehouses?name=forward",
                HttpMethod.GET, jsonEntity(null, admin), String.class).getBody());
        assertThat(filtered.get("totalElements").asLong()).isEqualTo(1);
        assertThat(filtered.get("content").get(0).get("name").asText()).isEqualTo("Forward Base");

        // --- Status filter on orders ---
        var created = readJson(restTemplate.exchange("/api/orders?status=CREATED",
                HttpMethod.GET, jsonEntity(null, admin), String.class).getBody());
        assertThat(created.get("totalElements").asLong()).isEqualTo(1);
        assertThat(created.get("content").get(0).get("id").asLong()).isEqualTo(createdOrderId);

        // --- Movement type filter: the initial stock recorded one ENTRY ---
        var entries = readJson(restTemplate.exchange("/api/movements?type=ENTRY",
                HttpMethod.GET, jsonEntity(null, admin), String.class).getBody());
        assertThat(entries.get("totalElements").asLong()).isEqualTo(1);
        var exits = readJson(restTemplate.exchange("/api/movements?type=EXIT",
                HttpMethod.GET, jsonEntity(null, admin), String.class).getBody());
        assertThat(exits.get("totalElements").asLong()).isZero();

        // --- Invalid parameters are rejected with 400 ---
        assertThat(restTemplate.exchange("/api/orders?status=BOGUS", HttpMethod.GET,
                jsonEntity(null, admin), String.class).getStatusCode().value()).isEqualTo(400);
        assertThat(restTemplate.exchange("/api/warehouses?sort=stockItems.quantity", HttpMethod.GET,
                jsonEntity(null, admin), String.class).getStatusCode().value()).isEqualTo(400);
        assertThat(restTemplate.exchange("/api/warehouses?size=1000", HttpMethod.GET,
                jsonEntity(null, admin), String.class).getStatusCode().value()).isEqualTo(400);
    }
}

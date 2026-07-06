package com.mls.logistics.integration;

import com.mls.logistics.security.domain.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of {@code GET /api/dashboard} against a real database:
 * the aggregates must reflect seeded data, and the endpoint must be readable
 * by every authenticated role (it is a GET like any other).
 */
class DashboardIntegrationTest extends AbstractIntegrationTest {

    @Test
    void dashboard_AggregatesSeededData_AndIsReadableByAuditor() {
        String admin = createUserAndLogin("admin-dashboard", Role.ADMIN);

        long warehouseId = postForId("/api/warehouses",
                "{\"name\":\"Central Depot\",\"location\":\"Madrid\"}", admin);
        long unitId = postForId("/api/units",
                "{\"name\":\"Alpha Unit\",\"location\":\"Sevilla\"}", admin);
        long resourceId = postForId("/api/resources",
                "{\"name\":\"Field ration\",\"type\":\"SUPPLY\",\"criticality\":\"HIGH\"}", admin);
        // Quantity 3 sits below both the low (10) and critical (5) test thresholds
        postForId("/api/stocks",
                "{\"resourceId\":" + resourceId + ",\"warehouseId\":" + warehouseId + ",\"quantity\":3}", admin);
        postForId("/api/orders",
                "{\"unitId\":" + unitId + ",\"dateCreated\":\"2026-07-01\",\"status\":\"CREATED\"}", admin);

        String auditor = createUserAndLogin("auditor-dashboard", Role.AUDITOR);
        var dash = getJson("/api/dashboard", auditor);

        // KPIs
        assertThat(dash.get("kpis").get("totalOrders").asLong()).isEqualTo(1);
        assertThat(dash.get("kpis").get("pendingOrders").asLong()).isEqualTo(1);
        assertThat(dash.get("kpis").get("totalStockQuantity").asLong()).isEqualTo(3);
        assertThat(dash.get("kpis").get("lowStockCount").asLong()).isEqualTo(1);
        assertThat(dash.get("kpis").get("recentMovementsCount").asLong()).isEqualTo(1);

        // Charts
        var stockChart = dash.get("charts").get("stockByWarehouse");
        assertThat(stockChart.get("labels").get(0).asText()).isEqualTo("Central Depot");
        assertThat(stockChart.get("values").get(0).asLong()).isEqualTo(3);
        var movementChart = dash.get("charts").get("movementsByType");
        assertThat(movementChart.get("values").get(0).asLong()).isEqualTo(1); // ENTRY
        assertThat(movementChart.get("values").get(1).asLong()).isZero();     // EXIT

        // Alerts: the 3-unit stock is low AND critical
        var lowStock = dash.get("alerts").get("lowStock");
        assertThat(lowStock.size()).isEqualTo(1);
        assertThat(lowStock.get(0).get("resourceName").asText()).isEqualTo("Field ration");
        assertThat(lowStock.get(0).get("warehouseName").asText()).isEqualTo("Central Depot");
        assertThat(lowStock.get(0).get("critical").asBoolean()).isTrue();

        // Recent movements include the initial stock ENTRY
        assertThat(dash.get("recentMovements").size()).isEqualTo(1);
        assertThat(dash.get("recentMovements").get(0).get("type").asText()).isEqualTo("ENTRY");

        // Thresholds echo the configuration used
        assertThat(dash.get("thresholds").get("lowStockThreshold").asInt()).isEqualTo(10);
        assertThat(dash.get("thresholds").get("criticalStockThreshold").asInt()).isEqualTo(5);
    }
}

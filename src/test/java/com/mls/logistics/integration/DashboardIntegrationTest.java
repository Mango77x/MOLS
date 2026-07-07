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

    @Test
    void stockByWarehouseChart_SumsAndOrdersAcrossMultipleWarehousesInTheDatabase() {
        // Proves the SUM(...) GROUP BY ... ORDER BY done in
        // StockRepository.sumQuantityByWarehouse (not in Java) is correct:
        // highest total first, ties broken alphabetically.
        String admin = createUserAndLogin("admin-dashboard-multi", Role.ADMIN);

        long depotA = postForId("/api/warehouses", "{\"name\":\"Alpha Depot\",\"location\":\"Madrid\"}", admin);
        long depotB = postForId("/api/warehouses", "{\"name\":\"Bravo Depot\",\"location\":\"Burgos\"}", admin);
        long depotC = postForId("/api/warehouses", "{\"name\":\"Charlie Depot\",\"location\":\"Leon\"}", admin);
        long resourceOne = postForId("/api/resources",
                "{\"name\":\"Ration pack\",\"type\":\"SUPPLY\",\"criticality\":\"LOW\"}", admin);
        long resourceTwo = postForId("/api/resources",
                "{\"name\":\"Fuel can\",\"type\":\"SUPPLY\",\"criticality\":\"LOW\"}", admin);

        // Alpha: 40 total (two stock rows, same warehouse) — highest, must be first
        postForId("/api/stocks",
                "{\"resourceId\":" + resourceOne + ",\"warehouseId\":" + depotA + ",\"quantity\":25}", admin);
        postForId("/api/stocks",
                "{\"resourceId\":" + resourceTwo + ",\"warehouseId\":" + depotA + ",\"quantity\":15}", admin);
        // Bravo and Charlie tie at 10 — alphabetical tie-break
        postForId("/api/stocks",
                "{\"resourceId\":" + resourceOne + ",\"warehouseId\":" + depotC + ",\"quantity\":10}", admin);
        postForId("/api/stocks",
                "{\"resourceId\":" + resourceOne + ",\"warehouseId\":" + depotB + ",\"quantity\":10}", admin);

        var dash = getJson("/api/dashboard", admin);
        var chart = dash.get("charts").get("stockByWarehouse");

        assertThat(chart.get("labels").get(0).asText()).isEqualTo("Alpha Depot");
        assertThat(chart.get("values").get(0).asLong()).isEqualTo(40);
        assertThat(chart.get("labels").get(1).asText()).isEqualTo("Bravo Depot");
        assertThat(chart.get("values").get(1).asLong()).isEqualTo(10);
        assertThat(chart.get("labels").get(2).asText()).isEqualTo("Charlie Depot");
        assertThat(chart.get("values").get(2).asLong()).isEqualTo(10);
    }
}

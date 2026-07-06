package com.mls.logistics.dto.response;

import java.util.List;

/**
 * Aggregated operational snapshot returned by {@code GET /api/dashboard}.
 *
 * <p>Mirrors the data behind the server-rendered {@code /ui} dashboard so API
 * clients (e.g. the React frontend) can render KPIs, charts and alerts from a
 * single call instead of aggregating client-side.</p>
 */
public record DashboardResponse(
        Kpis kpis,
        Charts charts,
        Alerts alerts,
        List<MovementResponse> recentMovements,
        Thresholds thresholds) {

    /** Headline counters shown as KPI cards. */
    public record Kpis(
            long totalOrders,
            long pendingOrders,
            long completedOrders,
            long cancelledOrders,
            long activeShipments,
            long totalStockQuantity,
            int stockWarehouseCount,
            long lowStockCount,
            long recentMovementsCount,
            double fulfillmentRatePercent,
            double fulfillmentTargetPercent,
            boolean fulfillmentTargetMet) {
    }

    /** A label/value series ready for charting. */
    public record ChartSeries(List<String> labels, List<Long> values) {
    }

    /** Chart datasets: stock per warehouse, movements by type, orders by status. */
    public record Charts(
            ChartSeries stockByWarehouse,
            ChartSeries movementsByType,
            ChartSeries ordersByStatus) {
    }

    /** A stock row at or below the low-stock threshold. */
    public record LowStockAlert(
            Long stockId,
            String resourceName,
            String warehouseName,
            int quantity,
            boolean critical) {
    }

    /** An open order that has been pending longer than the stale threshold. */
    public record StaleOrderAlert(Long orderId, String unitName, long daysPending) {
    }

    /** Actionable alert lists (both truncated to their configured limits). */
    public record Alerts(List<LowStockAlert> lowStock, List<StaleOrderAlert> staleOrders) {
    }

    /** The configured thresholds the aggregates were computed with. */
    public record Thresholds(
            int lowStockThreshold,
            int criticalStockThreshold,
            int staleOrderDays,
            int recentActivityHours,
            int movementChartDays) {
    }
}

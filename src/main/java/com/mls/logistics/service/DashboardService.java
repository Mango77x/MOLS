package com.mls.logistics.service;

import com.mls.logistics.config.DashboardProperties;
import com.mls.logistics.domain.Order;
import com.mls.logistics.domain.OrderStatus;
import com.mls.logistics.domain.ShipmentStatus;
import com.mls.logistics.domain.Stock;
import com.mls.logistics.dto.response.DashboardResponse;
import com.mls.logistics.dto.response.MovementResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Assembles the aggregated operational snapshot served by
 * {@code GET /api/dashboard}.
 *
 * <p>Delegates every metric to the owning domain service and only composes
 * the result.</p>
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final OrderService orderService;
    private final ShipmentService shipmentService;
    private final StockService stockService;
    private final MovementService movementService;
    private final DashboardProperties properties;

    public DashboardService(OrderService orderService,
                            ShipmentService shipmentService,
                            StockService stockService,
                            MovementService movementService,
                            DashboardProperties properties) {
        this.orderService = orderService;
        this.shipmentService = shipmentService;
        this.stockService = stockService;
        this.movementService = movementService;
        this.properties = properties;
    }

    /**
     * Computes the full dashboard snapshot with the configured thresholds.
     */
    public DashboardResponse getDashboard() {
        LocalDateTime now = LocalDateTime.now();

        long totalOrders = orderService.getTotalOrdersCount();
        long completedOrders = orderService.countByStatus(OrderStatus.COMPLETED);
        long cancelledOrders = orderService.countByStatus(OrderStatus.CANCELLED);
        long pendingOrders = orderService.countByStatus(OrderStatus.CREATED)
                + orderService.countByStatus(OrderStatus.VALIDATED);

        long activeShipments = shipmentService.countByStatus(ShipmentStatus.IN_TRANSIT);

        Map<String, Long> stockByWarehouse = stockService.getStockQuantityByWarehouse();
        long totalStockQuantity = stockByWarehouse.values().stream().mapToLong(Long::longValue).sum();
        long lowStockCount = stockService.countByQuantityLessThan(properties.getLowStockThreshold());

        long recentMovementsCount = movementService.countByDateTimeAfter(
                now.minusHours(properties.getRecentActivityHours()));

        double fulfillmentRatePercent = orderService.getFulfillmentRate();
        boolean fulfillmentTargetMet =
                fulfillmentRatePercent >= properties.getFulfillmentTargetPercent();

        DashboardResponse.Kpis kpis = new DashboardResponse.Kpis(
                totalOrders, pendingOrders, completedOrders, cancelledOrders,
                activeShipments, totalStockQuantity, stockByWarehouse.size(),
                lowStockCount, recentMovementsCount,
                fulfillmentRatePercent, properties.getFulfillmentTargetPercent(),
                fulfillmentTargetMet);

        Map<String, Long> movementsByType = movementService.getMovementCountByType(
                now.minusDays(properties.getMovementChartDays()));
        long entryCount = movementsByType.getOrDefault("ENTRY", 0L);
        long exitCount = movementsByType.getOrDefault("EXIT", 0L);

        List<String> stockLabels = new ArrayList<>(stockByWarehouse.keySet());
        DashboardResponse.Charts charts = new DashboardResponse.Charts(
                new DashboardResponse.ChartSeries(
                        stockLabels,
                        stockLabels.stream().map(stockByWarehouse::get).toList()),
                new DashboardResponse.ChartSeries(
                        List.of("ENTRY", "EXIT"),
                        List.of(entryCount, exitCount)),
                new DashboardResponse.ChartSeries(
                        List.of("PENDING", "COMPLETED", "CANCELLED"),
                        List.of(pendingOrders, completedOrders, cancelledOrders)));

        DashboardResponse.Alerts alerts = new DashboardResponse.Alerts(
                lowStockAlerts(), staleOrderAlerts());

        List<MovementResponse> recentMovements = movementService.getRecentMovements()
                .stream()
                .map(MovementResponse::from)
                .toList();

        DashboardResponse.Thresholds thresholds = new DashboardResponse.Thresholds(
                properties.getLowStockThreshold(),
                properties.getCriticalStockThreshold(),
                properties.getStaleOrderDays(),
                properties.getRecentActivityHours(),
                properties.getMovementChartDays());

        return new DashboardResponse(kpis, charts, alerts, recentMovements, thresholds);
    }

    private List<DashboardResponse.LowStockAlert> lowStockAlerts() {
        List<Stock> lowStock = stockService.getLowStockItems(properties.getLowStockThreshold());
        lowStock.sort(Comparator
                .comparingInt(Stock::getQuantity)
                .thenComparing(Stock::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return lowStock.stream()
                .limit(properties.getLowStockListLimit())
                .map(stock -> new DashboardResponse.LowStockAlert(
                        stock.getId(),
                        stock.getResource() != null ? stock.getResource().getName() : null,
                        stock.getWarehouse() != null ? stock.getWarehouse().getName() : null,
                        stock.getQuantity(),
                        stock.getQuantity() < properties.getCriticalStockThreshold()))
                .toList();
    }

    private List<DashboardResponse.StaleOrderAlert> staleOrderAlerts() {
        LocalDate today = LocalDate.now();
        return orderService.getStaleOrders(properties.getStaleOrderDays()).stream()
                .map(order -> new DashboardResponse.StaleOrderAlert(
                        order.getId(),
                        unitNameOf(order),
                        order.getDateCreated() != null
                                ? ChronoUnit.DAYS.between(order.getDateCreated(), today)
                                : 0))
                .sorted(Comparator
                        .comparingLong(DashboardResponse.StaleOrderAlert::daysPending).reversed()
                        .thenComparing(DashboardResponse.StaleOrderAlert::orderId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(properties.getStaleOrdersListLimit())
                .toList();
    }

    private static String unitNameOf(Order order) {
        return order.getUnit() != null ? order.getUnit().getName() : null;
    }
}

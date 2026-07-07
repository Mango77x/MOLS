package com.mls.logistics.service;

import com.mls.logistics.config.DashboardProperties;
import com.mls.logistics.domain.Shipment;
import com.mls.logistics.domain.Unit;
import com.mls.logistics.domain.Warehouse;
import com.mls.logistics.dto.response.MapResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Assembles the geo snapshot served by {@code GET /api/map}: warehouse and
 * unit pins with coordinates, and shipment routes from origin warehouse to
 * destination unit.
 *
 * <p>Reuses {@link DashboardProperties}' low/critical stock thresholds so the
 * map's warehouse pin colors agree with the dashboard's low-stock alerts.</p>
 */
@Service
@Transactional(readOnly = true)
public class MapService {

    private final WarehouseService warehouseService;
    private final UnitService unitService;
    private final ShipmentService shipmentService;
    private final StockService stockService;
    private final DashboardProperties properties;

    public MapService(WarehouseService warehouseService,
                      UnitService unitService,
                      ShipmentService shipmentService,
                      StockService stockService,
                      DashboardProperties properties) {
        this.warehouseService = warehouseService;
        this.unitService = unitService;
        this.shipmentService = shipmentService;
        this.stockService = stockService;
        this.properties = properties;
    }

    /**
     * Computes the full map snapshot.
     */
    public MapResponse getMap() {
        Map<Long, Integer> minQuantityByWarehouseId = stockService.getMinQuantityByWarehouseId();

        var warehousePins = warehouseService.getAllWarehouses().stream()
                .filter(warehouse -> warehouse.getLatitude() != null && warehouse.getLongitude() != null)
                .map(warehouse -> new MapResponse.WarehousePin(
                        warehouse.getId(),
                        warehouse.getName(),
                        warehouse.getLatitude(),
                        warehouse.getLongitude(),
                        stockStatus(minQuantityByWarehouseId.get(warehouse.getId()))))
                .toList();

        var unitPins = unitService.getAllUnits().stream()
                .filter(unit -> unit.getLatitude() != null && unit.getLongitude() != null)
                .map(unit -> new MapResponse.UnitPin(
                        unit.getId(), unit.getName(), unit.getLatitude(), unit.getLongitude()))
                .toList();

        var routes = shipmentService.getAllShipments().stream()
                .map(this::toRoute)
                .filter(java.util.Objects::nonNull)
                .toList();

        return new MapResponse(warehousePins, unitPins, routes);
    }

    /**
     * OK / WARNING / CRITICAL based on the warehouse's lowest stock quantity,
     * mirroring the dashboard's low/critical stock thresholds. A warehouse
     * with no stock records at all reads OK — there is nothing to warn about.
     */
    private String stockStatus(Integer minQuantity) {
        if (minQuantity == null) {
            return "OK";
        }
        if (minQuantity < properties.getCriticalStockThreshold()) {
            return "CRITICAL";
        }
        if (minQuantity < properties.getLowStockThreshold()) {
            return "WARNING";
        }
        return "OK";
    }

    /**
     * Resolves a shipment's route from its origin warehouse to its order's
     * destination unit. Returns {@code null} when either endpoint is missing
     * coordinates — the map can only draw what has a location.
     */
    private MapResponse.ShipmentRoute toRoute(Shipment shipment) {
        Warehouse warehouse = shipment.getWarehouse();
        Unit unit = shipment.getOrder() != null ? shipment.getOrder().getUnit() : null;

        if (warehouse == null || warehouse.getLatitude() == null || warehouse.getLongitude() == null
                || unit == null || unit.getLatitude() == null || unit.getLongitude() == null) {
            return null;
        }

        return new MapResponse.ShipmentRoute(
                shipment.getId(),
                shipment.getStatus() != null ? shipment.getStatus().name() : null,
                new MapResponse.RoutePoint(
                        warehouse.getId(), warehouse.getName(), warehouse.getLatitude(), warehouse.getLongitude()),
                new MapResponse.RoutePoint(
                        unit.getId(), unit.getName(), unit.getLatitude(), unit.getLongitude()));
    }
}

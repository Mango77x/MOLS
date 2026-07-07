package com.mls.logistics.dto.response;

import java.util.List;

/**
 * Geo snapshot returned by {@code GET /api/map}: warehouse and unit pins with
 * coordinates, and shipment routes resolved from the shipment's origin
 * warehouse to the destination unit of its order. Warehouses/units without
 * coordinates, and shipments whose origin or destination lacks coordinates,
 * are left out — the map can only plot what has a location.
 */
public record MapResponse(
        List<WarehousePin> warehouses,
        List<UnitPin> units,
        List<ShipmentRoute> shipments) {

    /** A warehouse pin with its aggregate stock status (OK / WARNING / CRITICAL). */
    public record WarehousePin(Long id, String name, double latitude, double longitude, String stockStatus) {
    }

    /** A unit pin. */
    public record UnitPin(Long id, String name, double latitude, double longitude) {
    }

    /** One endpoint (warehouse or unit) of a shipment route. */
    public record RoutePoint(Long id, String name, double latitude, double longitude) {
    }

    /** A shipment's route from its origin warehouse to its order's destination unit. */
    public record ShipmentRoute(Long id, String status, RoutePoint origin, RoutePoint destination) {
    }
}

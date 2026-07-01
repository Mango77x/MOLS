package com.mls.logistics.dto.response;

import com.mls.logistics.domain.Shipment;

/**
 * Data Transfer Object for Shipment responses.
 * 
 * This class defines the structure of shipment data returned by the API,
 * allowing control over exactly what fields are exposed to clients.
 */
public class ShipmentResponse {

    private Long id;
    private Long orderId;
    private Long vehicleId;
    private Long warehouseId;
    private String status;

    /**
     * Default constructor for serialization.
     */
    public ShipmentResponse() {
    }

    /**
     * Constructs a ShipmentResponse with all fields.
     *
     * @param id shipment identifier
     * @param orderId order identifier
     * @param vehicleId vehicle identifier
     * @param warehouseId warehouse identifier
     * @param status shipment status
     */
    public ShipmentResponse(Long id, Long orderId, Long vehicleId, Long warehouseId, String status) {
        this.id = id;
        this.orderId = orderId;
        this.vehicleId = vehicleId;
        this.warehouseId = warehouseId;
        this.status = status;
    }

    /**
     * Creates a ShipmentResponse from a Shipment entity.
     * 
     * This static factory method converts domain entities to DTOs,
     * decoupling the API from the persistence layer.
     *
     * @param shipment the shipment entity
     * @return ShipmentResponse DTO
     */
    public static ShipmentResponse from(Shipment shipment) {
        return new ShipmentResponse(
                shipment.getId(),
                shipment.getOrder().getId(),
                shipment.getVehicle().getId(),
                shipment.getWarehouse().getId(),
                shipment.getStatus() != null ? shipment.getStatus().name() : null
        );
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

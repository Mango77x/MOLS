package com.mls.logistics.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for updating an existing Shipment.
 *
 * All fields are optional - only provided fields will be updated.
 *
 * <p>No warehouse field: it always follows the shipment's order (see
 * {@code CreateShipmentRequest}) — re-derived automatically by
 * {@code ShipmentService} if {@code orderId} changes.</p>
 */
public class UpdateShipmentRequest {

    @Positive(message = "Order ID must be a positive number")
    private Long orderId;

    @Positive(message = "Vehicle ID must be a positive number")
    private Long vehicleId;

    @Size(min = 2, max = 50, message = "Shipment status must be between 2 and 50 characters")
    private String status;

    public UpdateShipmentRequest() {
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

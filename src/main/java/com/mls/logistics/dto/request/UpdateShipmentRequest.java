package com.mls.logistics.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Data Transfer Object for updating an existing Shipment.
 *
 * All fields are optional - only provided fields will be updated.
 *
 * <p>No warehouse field: it always follows the shipment's order (see
 * {@code CreateShipmentRequest}) — re-derived automatically by
 * {@code ShipmentService} if {@code orderId} changes.</p>
 *
 * <p>{@code items}, when provided (even as an empty list, which is rejected —
 * a shipment must always carry at least one item), replaces the shipment's
 * entire item set. {@code null} leaves the existing items untouched. Only
 * allowed while the shipment is not yet {@code DELIVERED}.</p>
 */
public class UpdateShipmentRequest {

    @Positive(message = "Order ID must be a positive number")
    private Long orderId;

    @Positive(message = "Vehicle ID must be a positive number")
    private Long vehicleId;

    @Size(min = 2, max = 50, message = "Shipment status must be between 2 and 50 characters")
    private String status;

    private List<@Valid ShipmentItemLineRequest> items;

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

    public List<ShipmentItemLineRequest> getItems() {
        return items;
    }

    public void setItems(List<ShipmentItemLineRequest> items) {
        this.items = items;
    }
}

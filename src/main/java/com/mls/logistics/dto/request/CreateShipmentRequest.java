package com.mls.logistics.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for creating a new Shipment.
 *
 * This class defines the structure of data accepted by the API
 * when creating a shipment, separating API contracts from
 * domain entities.
 *
 * <p>No warehouse field: a shipment always ships from its order's fixed
 * origin warehouse (see {@code Order.warehouse}), set automatically by
 * {@code ShipmentService} — asking for it again here would let a shipment
 * disagree with the warehouse its order's items reserved stock against.</p>
 */
public class CreateShipmentRequest {

    @NotNull(message = "Order ID is required")
    @Positive(message = "Order ID must be a positive number")
    private Long orderId;

    @NotNull(message = "Vehicle ID is required")
    @Positive(message = "Vehicle ID must be a positive number")
    private Long vehicleId;

    @NotBlank(message = "Shipment status is required")
    @Size(min = 2, max = 50, message = "Shipment status must be between 2 and 50 characters")
    private String status;

    /**
     * Default constructor for deserialization.
     */
    public CreateShipmentRequest() {
    }

    /**
     * Constructs a CreateShipmentRequest with all fields.
     *
     * @param orderId order identifier
     * @param vehicleId vehicle identifier
     * @param status shipment status
     */
    public CreateShipmentRequest(Long orderId, Long vehicleId, String status) {
        this.orderId = orderId;
        this.vehicleId = vehicleId;
        this.status = status;
    }

    // Getters and setters

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

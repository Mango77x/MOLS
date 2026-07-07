package com.mls.logistics.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for creating a new Order.
 *
 * This class defines the structure of data accepted by the API
 * when creating an order, separating API contracts from
 * domain entities.
 */
public class CreateOrderRequest {

    @NotNull(message = "Unit ID is required")
    @Positive(message = "Unit ID must be a positive number")
    private Long unitId;

    @NotNull(message = "Warehouse ID is required")
    @Positive(message = "Warehouse ID must be a positive number")
    private Long warehouseId;

    @NotNull(message = "Order creation date is required")
    private LocalDate dateCreated;

    @NotBlank(message = "Order status is required")
    @Size(min = 2, max = 50, message = "Order status must be between 2 and 50 characters")
    private String status;

    /**
     * Default constructor for deserialization.
     */
    public CreateOrderRequest() {
    }

    /**
     * Constructs a CreateOrderRequest with all fields.
     *
     * @param unitId unit identifier
     * @param warehouseId origin warehouse identifier
     * @param dateCreated creation date
     * @param status order status
     */
    public CreateOrderRequest(Long unitId, Long warehouseId, LocalDate dateCreated, String status) {
        this.unitId = unitId;
        this.warehouseId = warehouseId;
        this.dateCreated = dateCreated;
        this.status = status;
    }

    // Getters and setters

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public LocalDate getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(LocalDate dateCreated) {
        this.dateCreated = dateCreated;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

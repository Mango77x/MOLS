package com.mls.logistics.dto.response;

import com.mls.logistics.domain.Order;
import java.time.LocalDate;

/**
 * Data Transfer Object for Order responses.
 *
 * This class defines the structure of order data returned by the API,
 * allowing control over exactly what fields are exposed to clients.
 */
public class OrderResponse {

    private Long id;
    private Long unitId;
    private Long warehouseId;
    private LocalDate dateCreated;
    private String status;

    /**
     * Default constructor for serialization.
     */
    public OrderResponse() {
    }

    /**
     * Constructs an OrderResponse with all fields.
     *
     * @param id order identifier
     * @param unitId unit identifier
     * @param warehouseId origin warehouse identifier
     * @param dateCreated creation date
     * @param status order status
     */
    public OrderResponse(Long id, Long unitId, Long warehouseId, LocalDate dateCreated, String status) {
        this.id = id;
        this.unitId = unitId;
        this.warehouseId = warehouseId;
        this.dateCreated = dateCreated;
        this.status = status;
    }

    /**
     * Creates an OrderResponse from an Order entity.
     *
     * This static factory method converts domain entities to DTOs,
     * decoupling the API from the persistence layer.
     *
     * @param order the order entity
     * @return OrderResponse DTO
     */
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUnit().getId(),
                order.getWarehouse().getId(),
                order.getDateCreated(),
                order.getStatus() != null ? order.getStatus().name() : null
        );
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

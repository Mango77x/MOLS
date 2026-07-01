package com.mls.logistics.dto.response;

import com.mls.logistics.domain.Movement;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for Movement responses.
 * 
 * This class defines the structure of movement data returned by the API,
 * allowing control over exactly what fields are exposed to clients.
 */
public class MovementResponse {

    private Long id;
    private Long stockId;
    private String type;
    private int quantity;
    private LocalDateTime dateTime;

    private Long orderId;
    private Long shipmentId;
    private String reason;
    private String createdBy;

    /**
     * Default constructor for serialization.
     */
    public MovementResponse() {
    }

    /**
     * Constructs a MovementResponse with all fields.
     *
     * @param id movement identifier
     * @param stockId stock identifier
     * @param type movement type
     * @param quantity quantity affected
     * @param dateTime movement timestamp
     */
    public MovementResponse(Long id,
                            Long stockId,
                            String type,
                            int quantity,
                            LocalDateTime dateTime,
                            Long orderId,
                            Long shipmentId,
                            String reason,
                            String createdBy) {
        this.id = id;
        this.stockId = stockId;
        this.type = type;
        this.quantity = quantity;
        this.dateTime = dateTime;
        this.orderId = orderId;
        this.shipmentId = shipmentId;
        this.reason = reason;
        this.createdBy = createdBy;
    }

    /**
     * Creates a MovementResponse from a Movement entity.
     * 
     * This static factory method converts domain entities to DTOs,
     * decoupling the API from the persistence layer.
     *
     * @param movement the movement entity
     * @return MovementResponse DTO
     */
    public static MovementResponse from(Movement movement) {
        return new MovementResponse(
                movement.getId(),
                movement.getStock().getId(),
                movement.getType() != null ? movement.getType().name() : null,
                movement.getQuantity(),
                movement.getDateTime(),
                movement.getOrderId(),
                movement.getShipmentId(),
                movement.getReason(),
                movement.getCreatedBy()
        );
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStockId() {
        return stockId;
    }

    public void setStockId(Long stockId) {
        this.stockId = stockId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(Long shipmentId) {
        this.shipmentId = shipmentId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}

package com.mls.logistics.dto.response;

import com.mls.logistics.domain.Stock;

/**
 * Data Transfer Object for Stock responses.
 * 
 * This class defines the structure of stock data returned by the API,
 * allowing control over exactly what fields are exposed to clients.
 */
public class StockResponse {

    private Long id;
    private Long resourceId;
    private Long warehouseId;
    private int quantity;
    private int reservedQuantity;

    /**
     * Default constructor for serialization.
     */
    public StockResponse() {
    }

    /**
     * Constructs a StockResponse with all fields.
     *
     * @param id stock identifier
     * @param resourceId resource identifier
     * @param warehouseId warehouse identifier
     * @param quantity available quantity
     * @param reservedQuantity quantity committed to open order items sourced from this warehouse
     */
    public StockResponse(Long id, Long resourceId, Long warehouseId, int quantity, int reservedQuantity) {
        this.id = id;
        this.resourceId = resourceId;
        this.warehouseId = warehouseId;
        this.quantity = quantity;
        this.reservedQuantity = reservedQuantity;
    }

    /**
     * Creates a StockResponse from a Stock entity.
     *
     * This static factory method converts domain entities to DTOs,
     * decoupling the API from the persistence layer.
     *
     * @param stock the stock entity
     * @return StockResponse DTO
     */
    public static StockResponse from(Stock stock) {
        return new StockResponse(
                stock.getId(),
                stock.getResource().getId(),
                stock.getWarehouse().getId(),
                stock.getQuantity(),
                stock.getReservedQuantity()
        );
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(int reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }
}

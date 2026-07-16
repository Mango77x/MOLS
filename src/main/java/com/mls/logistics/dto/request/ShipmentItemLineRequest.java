package com.mls.logistics.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * A single line within {@link CreateShipmentRequest}/{@link UpdateShipmentRequest}:
 * how much of one {@code OrderItem} this shipment carries. Mirrors
 * {@link OrderItemLineRequest}'s shape for the analogous order-line case.
 */
public class ShipmentItemLineRequest {

    @NotNull(message = "Order item ID is required")
    @Positive(message = "Order item ID must be a positive number")
    private Long orderItemId;

    @Positive(message = "Shipment item quantity must be greater than 0")
    private int quantity;

    public ShipmentItemLineRequest() {
    }

    public ShipmentItemLineRequest(Long orderItemId, int quantity) {
        this.orderItemId = orderItemId;
        this.quantity = quantity;
    }

    public Long getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(Long orderItemId) {
        this.orderItemId = orderItemId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}

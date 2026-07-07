package com.mls.logistics.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * A single line item within {@link CreateOrderWithItemsRequest}.
 *
 * Unlike {@link CreateOrderItemRequest}, this does not carry an
 * {@code orderId}: the parent order does not exist yet when the wizard
 * submits, so the id is assigned server-side after the order is created.
 */
public class OrderItemLineRequest {

    @NotNull(message = "Resource ID is required")
    @Positive(message = "Resource ID must be a positive number")
    private Long resourceId;

    @Positive(message = "Order item quantity must be greater than 0")
    private int quantity;

    public OrderItemLineRequest() {
    }

    public OrderItemLineRequest(Long resourceId, int quantity) {
        this.resourceId = resourceId;
        this.quantity = quantity;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}

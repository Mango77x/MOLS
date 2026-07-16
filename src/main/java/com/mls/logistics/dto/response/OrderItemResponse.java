package com.mls.logistics.dto.response;

import com.mls.logistics.domain.OrderItem;

/**
 * Data Transfer Object for OrderItem responses.
 * 
 * This class defines the structure of order item data returned by the API,
 * allowing control over exactly what fields are exposed to clients.
 */
public class OrderItemResponse {

    private Long id;
    private Long orderId;
    private Long resourceId;
    private int quantity;
    private int deliveredQuantity;
    private int remainingQuantity;

    /**
     * Default constructor for serialization.
     */
    public OrderItemResponse() {
    }

    /**
     * Constructs an OrderItemResponse with all fields.
     *
     * @param id order item identifier
     * @param orderId order identifier
     * @param resourceId resource identifier
     * @param quantity requested quantity
     * @param deliveredQuantity quantity actually delivered so far, summed across DELIVERED shipments
     * @param remainingQuantity quantity not yet allocated to any shipment ({@code quantity} minus
     *        allocations across shipments of any status) — the ceiling a new shipment may still claim
     */
    public OrderItemResponse(Long id, Long orderId, Long resourceId, int quantity,
                              int deliveredQuantity, int remainingQuantity) {
        this.id = id;
        this.orderId = orderId;
        this.resourceId = resourceId;
        this.quantity = quantity;
        this.deliveredQuantity = deliveredQuantity;
        this.remainingQuantity = remainingQuantity;
    }

    /**
     * Creates an OrderItemResponse from an OrderItem entity plus its
     * shipment-derived progress (see {@code OrderItemService.shippingProgress},
     * computed in one batch query per request rather than per item).
     *
     * This static factory method converts domain entities to DTOs,
     * decoupling the API from the persistence layer.
     *
     * @param orderItem the order item entity
     * @return OrderItemResponse DTO
     */
    public static OrderItemResponse from(OrderItem orderItem, int deliveredQuantity, int remainingQuantity) {
        return new OrderItemResponse(
                orderItem.getId(),
                orderItem.getOrder().getId(),
                orderItem.getResource().getId(),
                orderItem.getQuantity(),
                deliveredQuantity,
                remainingQuantity
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

    public int getDeliveredQuantity() {
        return deliveredQuantity;
    }

    public void setDeliveredQuantity(int deliveredQuantity) {
        this.deliveredQuantity = deliveredQuantity;
    }

    public int getRemainingQuantity() {
        return remainingQuantity;
    }

    public void setRemainingQuantity(int remainingQuantity) {
        this.remainingQuantity = remainingQuantity;
    }
}

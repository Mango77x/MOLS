package com.mls.logistics.dto.response;

import com.mls.logistics.domain.ShipmentItem;

/**
 * Data Transfer Object for ShipmentItem responses.
 *
 * Mirrors {@link OrderItemResponse}'s shape for the analogous
 * shipment-line case.
 */
public class ShipmentItemResponse {

    private Long id;
    private Long shipmentId;
    private Long orderItemId;
    private int quantity;

    /**
     * Default constructor for serialization.
     */
    public ShipmentItemResponse() {
    }

    public ShipmentItemResponse(Long id, Long shipmentId, Long orderItemId, int quantity) {
        this.id = id;
        this.shipmentId = shipmentId;
        this.orderItemId = orderItemId;
        this.quantity = quantity;
    }

    public static ShipmentItemResponse from(ShipmentItem item) {
        return new ShipmentItemResponse(
                item.getId(),
                item.getShipment().getId(),
                item.getOrderItem().getId(),
                item.getQuantity()
        );
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(Long shipmentId) {
        this.shipmentId = shipmentId;
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

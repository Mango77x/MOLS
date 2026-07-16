package com.mls.logistics.domain;

import jakarta.persistence.*;

/**
 * Represents a specific {@link OrderItem} (and quantity) carried by a
 * {@link Shipment}. A single order item's quantity may be split across
 * several shipments; the sum of all shipment items for an order item must
 * never exceed that order item's quantity (enforced in {@code ShipmentService}).
 */
@Entity
@Table(name = "shipment_items")
public class ShipmentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Shipment this line belongs to */
    @ManyToOne(optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    /** Order item this shipment is carrying (and how much of it) */
    @ManyToOne(optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    /** Quantity of the order item carried by this shipment */
    private int quantity;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Shipment getShipment() { return shipment; }
    public void setShipment(Shipment shipment) { this.shipment = shipment; }

    public OrderItem getOrderItem() { return orderItem; }
    public void setOrderItem(OrderItem orderItem) { this.orderItem = orderItem; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}

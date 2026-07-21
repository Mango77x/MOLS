package com.mls.logistics.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Represents an Order requested by a Unit for resources.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unit that created the order */
    @ManyToOne
    @JoinColumn(name = "unit_id")
    private Unit unit;

    /**
     * Origin warehouse this order is fulfilled from. Fixed at creation and
     * immutable afterwards: every item's stock reservation is made against
     * this warehouse (see {@code OrderItemService}), and shipments inherit
     * it automatically instead of choosing their own — the whole point is
     * that a validated order can no longer fail at delivery for a warehouse
     * mismatch.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    /** Date of creation */
    private LocalDate dateCreated;

    /** Lifecycle status — transitions are enforced by OrderService (see OrderStatus) */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    /** List of items requested */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items;

    /**
     * Shipments associated to this order.
     *
     * No cascade: a Shipment has its own protected lifecycle (a DELIVERED
     * shipment carries audit-relevant stock movements) and must go through
     * {@code ShipmentService.deleteShipment}'s own guard, not be silently
     * removed just because its parent order is deleted.
     */
    @OneToMany(mappedBy = "order")
    private List<Shipment> shipments;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Unit getUnit() { return unit; }
    public void setUnit(Unit unit) { this.unit = unit; }

    public Warehouse getWarehouse() { return warehouse; }
    public void setWarehouse(Warehouse warehouse) { this.warehouse = warehouse; }

    public LocalDate getDateCreated() { return dateCreated; }
    public void setDateCreated(LocalDate dateCreated) { this.dateCreated = dateCreated; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public List<Shipment> getShipments() { return shipments; }
    public void setShipments(List<Shipment> shipments) { this.shipments = shipments; }
}

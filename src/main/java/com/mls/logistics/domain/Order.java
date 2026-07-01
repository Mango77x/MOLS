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

    /** Date of creation */
    private LocalDate dateCreated;

    /** Lifecycle status — transitions are enforced by OrderService (see OrderStatus) */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    /** List of items requested */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items;

    /** Shipments associated to this order */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<Shipment> shipments;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Unit getUnit() { return unit; }
    public void setUnit(Unit unit) { this.unit = unit; }

    public LocalDate getDateCreated() { return dateCreated; }
    public void setDateCreated(LocalDate dateCreated) { this.dateCreated = dateCreated; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public List<Shipment> getShipments() { return shipments; }
    public void setShipments(List<Shipment> shipments) { this.shipments = shipments; }
}

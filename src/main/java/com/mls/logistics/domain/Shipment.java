package com.mls.logistics.domain;

import jakarta.persistence.*;

/**
 * Represents a Shipment transporting resources from a warehouse to a unit.
 */
@Entity
@Table(name = "shipments")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The order associated with this shipment */
    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    /** Vehicle assigned to transport */
    @ManyToOne
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    /** Warehouse origin */
    @ManyToOne
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    /** Lifecycle status — transitions are enforced by ShipmentService (see ShipmentStatus) */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ShipmentStatus status;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }

    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }

    public Warehouse getWarehouse() { return warehouse; }
    public void setWarehouse(Warehouse warehouse) { this.warehouse = warehouse; }

    public ShipmentStatus getStatus() { return status; }
    public void setStatus(ShipmentStatus status) { this.status = status; }
}

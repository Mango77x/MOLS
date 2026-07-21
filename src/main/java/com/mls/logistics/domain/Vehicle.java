package com.mls.logistics.domain;

import jakarta.persistence.*;
import java.util.List;

/**
 * Represents a Vehicle used for transporting shipments.
 * Types: TERRESTRE, MARITIMO, AEREO
 */
@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Type of vehicle */
    private String type;

    /** Capacity (number of items, weight, etc.) */
    private int capacity;

    /** Operational status (see VehicleStatus) */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VehicleStatus status;

    /**
     * Shipments assigned to this vehicle.
     *
     * No cascade: see {@code Order.shipments} for why. {@code
     * VehicleService.deleteVehicle} rejects deletion while any shipment
     * still references this vehicle.
     */
    @OneToMany(mappedBy = "vehicle")
    private List<Shipment> shipments;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public VehicleStatus getStatus() { return status; }
    public void setStatus(VehicleStatus status) { this.status = status; }

    public List<Shipment> getShipments() { return shipments; }
    public void setShipments(List<Shipment> shipments) { this.shipments = shipments; }
}

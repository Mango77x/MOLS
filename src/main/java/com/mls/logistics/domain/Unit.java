package com.mls.logistics.domain;

import jakarta.persistence.*;
import java.util.List;

/**
 * Represents a Unit (branch, department, or operational unit) that can create orders.
 */
@Entity
@Table(name = "units")
public class Unit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Name of the unit */
    @Column(nullable = false)
    private String name;

    /** Physical location or description */
    private String location;

    /** Geographic latitude in decimal degrees (optional, for the logistics map) */
    private Double latitude;

    /** Geographic longitude in decimal degrees (optional, for the logistics map) */
    private Double longitude;

    /**
     * Orders created by this unit.
     *
     * No cascade: an Order has its own protected lifecycle (a COMPLETED
     * order carries audit-relevant stock movements) and must go through
     * {@code OrderService.deleteOrder}'s own guard, not be silently removed
     * just because its unit is deleted. {@code UnitService.deleteUnit}
     * rejects deletion while any order still references this unit.
     */
    @OneToMany(mappedBy = "unit")
    private List<Order> orders;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public List<Order> getOrders() { return orders; }
    public void setOrders(List<Order> orders) { this.orders = orders; }
}

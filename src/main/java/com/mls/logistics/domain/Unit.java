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

    /** Orders created by this unit */
    @OneToMany(mappedBy = "unit", cascade = CascadeType.ALL)
    private List<Order> orders;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public List<Order> getOrders() { return orders; }
    public void setOrders(List<Order> orders) { this.orders = orders; }
}

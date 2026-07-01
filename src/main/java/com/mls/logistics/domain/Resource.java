package com.mls.logistics.domain;

import jakarta.persistence.*;
import java.util.List;

/**
 * Represents a Resource that can be requested and stored.
 * Examples: materials, parts, electronics, equipment.
 */
@Entity
@Table(name = "resources")
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Name of the resource */
    @Column(nullable = false)
    private String name;

    /** Type of resource (equipment, material, etc.) */
    private String type;

    /** Criticality level (e.g., LOW, MEDIUM, HIGH) */
    private String criticality;

    /** Stocks where this resource exists */
    @OneToMany(mappedBy = "resource", cascade = CascadeType.ALL)
    private List<Stock> stocks;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCriticality() { return criticality; }
    public void setCriticality(String criticality) { this.criticality = criticality; }

    public List<Stock> getStocks() { return stocks; }
    public void setStocks(List<Stock> stocks) { this.stocks = stocks; }
}

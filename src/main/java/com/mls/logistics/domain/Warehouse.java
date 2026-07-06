package com.mls.logistics.domain;

import jakarta.persistence.*;
import java.util.List;

/**
 * Represents a Warehouse that stores resources (stock).
 */
@Entity
@Table(name = "warehouses")
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Name of the warehouse */
    @Column(nullable = false)
    private String name;

    /** Location of the warehouse */
    private String location;

    /** Geographic latitude in decimal degrees (optional, for the logistics map) */
    private Double latitude;

    /** Geographic longitude in decimal degrees (optional, for the logistics map) */
    private Double longitude;

    /** Stock items in this warehouse */
    @OneToMany(mappedBy = "warehouse", cascade = CascadeType.ALL)
    private List<Stock> stockItems;

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

    public List<Stock> getStockItems() { return stockItems; }
    public void setStockItems(List<Stock> stockItems) { this.stockItems = stockItems; }
}

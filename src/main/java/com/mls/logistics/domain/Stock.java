package com.mls.logistics.domain;

import jakarta.persistence.*;
import java.util.List;

/**
 * Represents the quantity of a Resource available in a specific Warehouse.
 */
@Entity
@Table(name = "stocks")
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The resource stored */
    @ManyToOne(optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    /** The warehouse where the stock is located */
    @ManyToOne(optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    /** Quantity available (cannot be negative) */
    private int quantity;

    /**
     * Optimistic-lock version. Detects concurrent modifications of the same
     * stock row (two simultaneous adjustments) and fails one of them instead
     * of silently losing an update.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Historical movements of this stock. No cascade: movements are an
     * append-only audit trail and must never be deleted through this
     * association (StockService blocks deleting stock that has history).
     */
    @OneToMany(mappedBy = "stock")
    private List<Movement> movements;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Resource getResource() { return resource; }
    public void setResource(Resource resource) { this.resource = resource; }

    public Warehouse getWarehouse() { return warehouse; }
    public void setWarehouse(Warehouse warehouse) { this.warehouse = warehouse; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public long getVersion() { return version; }

    public List<Movement> getMovements() { return movements; }
    public void setMovements(List<Movement> movements) { this.movements = movements; }
}

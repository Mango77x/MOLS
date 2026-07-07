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
     * Quantity of this (resource, warehouse) pair currently committed to
     * open (non-terminal) order items belonging to orders sourced from this
     * warehouse. Tracked separately from physical {@code quantity} so
     * order-item creation can enforce "physical stock minus what's already
     * promised" instead of only checking raw physical stock, which two
     * orders could each pass independently and over-commit. See
     * {@code OrderItemService}.
     */
    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

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

    public int getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(int reservedQuantity) { this.reservedQuantity = reservedQuantity; }

    public long getVersion() { return version; }

    public List<Movement> getMovements() { return movements; }
    public void setMovements(List<Movement> movements) { this.movements = movements; }
}

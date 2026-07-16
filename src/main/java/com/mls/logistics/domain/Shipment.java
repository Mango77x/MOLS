package com.mls.logistics.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Order items (and quantities) this shipment carries. {@code orphanRemoval}
     * so replacing the set (see ShipmentService) or deleting a non-delivered
     * shipment cleanly frees the order items' allocation.
     *
     * <p>{@code EAGER} + {@code SUBSELECT}: {@code ShipmentResponse} always
     * serializes this collection, including from list endpoints where the
     * request's Hibernate session has already closed by the time the
     * controller builds the DTO — {@code LAZY} would throw
     * {@code LazyInitializationException} there. {@code SUBSELECT} fetches
     * every result row's items in one extra query instead of one per
     * shipment (N+1).</p>
     */
    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    private List<ShipmentItem> items = new ArrayList<>();

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

    public List<ShipmentItem> getItems() { return items; }
    public void setItems(List<ShipmentItem> items) { this.items = items; }
}

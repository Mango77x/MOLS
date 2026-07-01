package com.mls.logistics.domain;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Append-only audit record of a stock change.
 *
 * <p>Movements are created exclusively by {@code StockService} whenever stock
 * quantity changes. They are never updated or deleted — corrections are made
 * by recording a new compensating movement. The acting user is captured
 * automatically via JPA auditing ({@link CreatedBy}).</p>
 */
@Entity
@Table(name = "movements")
@EntityListeners(AuditingEntityListener.class)
public class Movement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stock affected by this movement */
    @ManyToOne
    @JoinColumn(name = "stock_id")
    private Stock stock;

    /** Type of movement: ENTRY (increase) or EXIT (decrease) */
    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private MovementType type;

    /** Quantity affected (always positive) */
    private int quantity;

    /** Timestamp of the movement */
    private LocalDateTime dateTime;

    /** Optional: order identifier that caused this movement (for traceability) */
    @Column(name = "order_id")
    private Long orderId;

    /** Optional: shipment identifier that caused this movement (for traceability) */
    @Column(name = "shipment_id")
    private Long shipmentId;

    /** Optional: short reason label (e.g., 'Shipment delivered') */
    @Column(name = "reason", length = 200)
    private String reason;

    /** Username of the actor who caused this movement (set automatically, never updated) */
    @CreatedBy
    @Column(name = "created_by", length = 50, updatable = false)
    private String createdBy;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Stock getStock() { return stock; }
    public void setStock(Stock stock) { this.stock = stock; }

    public MovementType getType() { return type; }
    public void setType(MovementType type) { this.type = type; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public LocalDateTime getDateTime() { return dateTime; }
    public void setDateTime(LocalDateTime dateTime) { this.dateTime = dateTime; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long shipmentId) { this.shipmentId = shipmentId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getCreatedBy() { return createdBy; }
}

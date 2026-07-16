package com.mls.logistics.repository;

import com.mls.logistics.domain.ShipmentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Repository for accessing ShipmentItem data from the database.
 */
public interface ShipmentItemRepository extends JpaRepository<ShipmentItem, Long> {

    List<ShipmentItem> findByShipmentId(Long shipmentId);

    /** Projection row for the allocation/delivery aggregate queries below. */
    interface OrderItemQuantity {
        Long getOrderItemId();
        Long getTotal();
    }

    /**
     * Total quantity allocated to each order item across every shipment,
     * regardless of status (PLANNED/IN_TRANSIT/DELIVERED all "claim" a
     * portion of the item, the same way a stock reservation does). Used to
     * cap how much of an order item a new/edited shipment may still carry.
     * Aggregated in the database for every id in one round trip instead of
     * per-item, so building a response for a page of order items never
     * costs an extra query per row.
     */
    @Query("""
            SELECT si.orderItem.id AS orderItemId, SUM(si.quantity) AS total
            FROM ShipmentItem si
            WHERE si.orderItem.id IN :orderItemIds
            GROUP BY si.orderItem.id
            """)
    List<OrderItemQuantity> sumQuantityByOrderItemIds(@Param("orderItemIds") Collection<Long> orderItemIds);

    /**
     * Total quantity actually delivered per order item (DELIVERED shipments
     * only). Drives per-item "shipped X / Y" progress and whether the
     * parent order is fully or partially shipped.
     */
    @Query("""
            SELECT si.orderItem.id AS orderItemId, SUM(si.quantity) AS total
            FROM ShipmentItem si
            WHERE si.orderItem.id IN :orderItemIds AND si.shipment.status = 'DELIVERED'
            GROUP BY si.orderItem.id
            """)
    List<OrderItemQuantity> sumDeliveredQuantityByOrderItemIds(@Param("orderItemIds") Collection<Long> orderItemIds);
}

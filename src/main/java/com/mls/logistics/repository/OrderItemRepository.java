package com.mls.logistics.repository;

import com.mls.logistics.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Collection;
import java.util.List;

/**
 * Repository for accessing OrderItem data from the database.
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * Finds all items belonging to a specific order.
     *
     * Used to calculate total requested quantity per resource
     * and to validate new items against available stock.
     *
     * @param orderId the order identifier
     * @return list of order items for this order
     */
    List<OrderItem> findByOrderId(Long orderId);

    /**
     * Finds all items belonging to a specific order with sorting.
     */
    List<OrderItem> findByOrderId(Long orderId, Sort sort);

    /**
     * Finds all items belonging to a list of orders.
     */
    List<OrderItem> findByOrderIdIn(Collection<Long> orderIds, Sort sort);

    /**
     * Finds a page of items belonging to a specific order.
     */
    Page<OrderItem> findByOrderId(Long orderId, Pageable pageable);
}
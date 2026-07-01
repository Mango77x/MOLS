package com.mls.logistics.repository;

import com.mls.logistics.domain.Order;
import com.mls.logistics.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Repository for accessing Order data from the database.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {
    long countByStatus(OrderStatus status);

    List<Order> findByStatusInAndDateCreatedBefore(Collection<OrderStatus> statuses, LocalDate cutoffDate);

    List<Order> findByStatusNotIn(Collection<OrderStatus> statuses, Sort sort);
}

package com.mls.logistics.repository;

import com.mls.logistics.domain.Movement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for accessing Movement data from the database.
 */
public interface MovementRepository extends JpaRepository<Movement, Long> {
    List<Movement> findByOrderId(Long orderId, Sort sort);

    List<Movement> findByShipmentId(Long shipmentId, Sort sort);

    @EntityGraph(attributePaths = {"stock", "stock.resource", "stock.warehouse"})
    List<Movement> findTop15ByOrderByDateTimeDesc();

    long countByDateTimeAfter(LocalDateTime dateTime);

    List<Movement> findByDateTimeAfter(LocalDateTime dateTime);

    /**
     * True when at least one movement references this stock. Used to protect
     * the audit trail: stock rows with history cannot be deleted.
     */
    boolean existsByStockId(Long stockId);
}

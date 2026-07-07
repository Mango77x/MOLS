package com.mls.logistics.repository;

import com.mls.logistics.domain.Movement;
import com.mls.logistics.domain.MovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for accessing Movement data from the database.
 */
public interface MovementRepository
        extends JpaRepository<Movement, Long>, JpaSpecificationExecutor<Movement> {
    List<Movement> findByOrderId(Long orderId, Sort sort);

    List<Movement> findByShipmentId(Long shipmentId, Sort sort);

    @EntityGraph(attributePaths = {"stock", "stock.resource", "stock.warehouse"})
    List<Movement> findTop15ByOrderByDateTimeDesc();

    long countByDateTimeAfter(LocalDateTime dateTime);

    /**
     * True when at least one movement references this stock. Used to protect
     * the audit trail: stock rows with history cannot be deleted.
     */
    boolean existsByStockId(Long stockId);

    /** Projection row for {@link #countByTypeSince}. */
    interface TypeCount {
        MovementType getType();
        Long getCount();
    }

    /**
     * Movement counts per type since the given timestamp, grouped in the
     * database instead of loading every matching row into memory. Feeds the
     * dashboard's "movements by type" chart.
     */
    @Query("SELECT m.type AS type, COUNT(m) AS count FROM Movement m WHERE m.dateTime > :since GROUP BY m.type")
    List<TypeCount> countByTypeSince(@Param("since") LocalDateTime since);
}

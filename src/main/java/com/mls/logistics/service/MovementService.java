package com.mls.logistics.service;

import com.mls.logistics.domain.Movement;
import com.mls.logistics.domain.MovementType;
import com.mls.logistics.repository.MovementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only service layer for the Movement audit trail.
 *
 * <p>Movements are an <strong>append-only</strong> audit record: they are
 * created exclusively by {@code StockService} when stock changes, and are
 * never updated or deleted. Corrections are made by applying a new stock
 * adjustment (which records a compensating movement) — never by rewriting
 * history. This service therefore only exposes query operations.</p>
 */
@Service
@Transactional(readOnly = true)
public class MovementService {

    private final MovementRepository movementRepository;

    /**
     * Constructor-based dependency injection.
     * This is the recommended approach in Spring.
     */
    public MovementService(MovementRepository movementRepository) {
        this.movementRepository = movementRepository;
    }

    /**
     * Retrieves all registered movements.
     */
    public List<Movement> getAllMovements() {
        return movementRepository.findAll();
    }

    /**
     * Retrieves all registered movements with ordering.
     */
    public List<Movement> getAllMovements(Sort sort) {
        return movementRepository.findAll(sort);
    }

    /**
     * Retrieves a page of movements.
     */
    public Page<Movement> getAllMovements(Pageable pageable) {
        return movementRepository.findAll(pageable);
    }

    /**
     * Retrieves a page of movements matching the optional filters.
     *
     * @param type       movement type (ENTRY/EXIT); ignored when null/blank
     * @param orderId    restrict to one originating order; ignored when null
     * @param shipmentId restrict to one originating shipment; ignored when null
     */
    public Page<Movement> searchMovements(String type, Long orderId, Long shipmentId,
                                          Pageable pageable) {
        List<Specification<Movement>> filters = new ArrayList<>();
        if (type != null && !type.isBlank()) {
            MovementType parsed = MovementType.from(type);
            filters.add((root, query, cb) -> cb.equal(root.get("type"), parsed));
        }
        if (orderId != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("orderId"), orderId));
        }
        if (shipmentId != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("shipmentId"), shipmentId));
        }
        return movementRepository.findAll(Specification.allOf(filters), pageable);
    }

    /**
     * Returns movement entries linked to a specific order.
     */
    public List<Movement> getMovementsByOrderId(Long orderId, Sort sort) {
        return movementRepository.findByOrderId(orderId, sort);
    }

    /**
     * Returns movement entries linked to a specific shipment.
     */
    public List<Movement> getMovementsByShipmentId(Long shipmentId, Sort sort) {
        return movementRepository.findByShipmentId(shipmentId, sort);
    }

    /**
     * Retrieves a movement by its identifier.
     */
    public Optional<Movement> getMovementById(Long id) {
        return movementRepository.findById(id);
    }

    public List<Movement> getRecentMovements() {
        return movementRepository.findTop15ByOrderByDateTimeDesc();
    }

    public long countByDateTimeAfter(LocalDateTime since) {
        return movementRepository.countByDateTimeAfter(since);
    }

    /**
     * Counts movements per type since the given timestamp.
     *
     * Keys are {@code MovementType} names; used by the dashboard chart.
     */
    public Map<String, Long> getMovementCountByType(LocalDateTime since) {
        return movementRepository.findByDateTimeAfter(since)
                .stream()
                .collect(Collectors.groupingBy(
                        m -> m.getType() == null ? "UNKNOWN" : m.getType().name(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }
}

package com.mls.logistics.repository;

import com.mls.logistics.domain.Shipment;
import com.mls.logistics.domain.ShipmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Sort;

import java.util.List;

/**
 * Repository for accessing Shipment data from the database.
 */
public interface ShipmentRepository
        extends JpaRepository<Shipment, Long>, JpaSpecificationExecutor<Shipment> {
    List<Shipment> findByOrderId(Long orderId, Sort sort);

    long countByStatus(ShipmentStatus status);
}

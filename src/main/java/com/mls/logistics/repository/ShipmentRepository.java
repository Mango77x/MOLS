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

    /** Used by {@code OrderService.deleteOrder} to check for a DELIVERED shipment on a PARTIALLY_SHIPPED order. */
    boolean existsByOrderIdAndStatus(Long orderId, ShipmentStatus status);

    /** Used by {@code VehicleService.deleteVehicle} to reject deleting a vehicle that still has shipments. */
    boolean existsByVehicleId(Long vehicleId);

    /** Used by {@code WarehouseService.deleteWarehouse} to reject deleting a warehouse that's still a shipment origin. */
    boolean existsByWarehouseId(Long warehouseId);
}

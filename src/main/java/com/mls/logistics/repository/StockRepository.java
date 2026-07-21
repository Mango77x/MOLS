package com.mls.logistics.repository;

import com.mls.logistics.domain.Stock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for accessing Stock data from the database.
 */
public interface StockRepository
        extends JpaRepository<Stock, Long>, JpaSpecificationExecutor<Stock> {

    /**
     * Finds the stock record for a specific resource in a specific warehouse.
     *
     * Used to check available quantity before processing orders and
     * to locate the stock record that needs to be adjusted.
     *
     * @param resourceId  the resource identifier
     * @param warehouseId the warehouse identifier
     * @return the stock record if found
     */
    Optional<Stock> findByResourceIdAndWarehouseId(Long resourceId, Long warehouseId);

    /**
     * Same as {@link #findByResourceIdAndWarehouseId} but acquires a
     * row-level lock for the duration of the caller's transaction.
     *
     * <p>Used by {@code OrderItemService} to serialize the
     * check-reserved-then-reserve sequence when creating/editing/releasing
     * order items against the same (resource, warehouse) pair, closing the
     * check-then-act race that plain reads leave open under concurrent
     * requests.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.resource.id = :resourceId and s.warehouse.id = :warehouseId")
    Optional<Stock> findByResourceIdAndWarehouseIdForUpdate(
            @Param("resourceId") Long resourceId, @Param("warehouseId") Long warehouseId);

    List<Stock> findByQuantityLessThan(int threshold);

    long countByQuantityLessThan(int threshold);

    /** Used by {@code WarehouseService.deleteWarehouse} to reject deleting a warehouse that still has stock. */
    boolean existsByWarehouseId(Long warehouseId);

    /** Used by {@code ResourceService.deleteResource} to reject deleting a resource that still has stock. */
    boolean existsByResourceId(Long resourceId);

    /** Projection row for {@link #sumQuantityByWarehouse}. */
    interface WarehouseQuantity {
        String getWarehouseName();
        Long getTotal();
    }

    /**
     * Total stock quantity per warehouse, summed in the database instead of
     * loading every stock row into memory. Ordered by total descending, then
     * warehouse name (case-insensitive) ascending — feeds the dashboard's
     * "stock by warehouse" chart, which wants the busiest warehouses first.
     */
    @Query("""
            SELECT s.warehouse.name AS warehouseName, SUM(s.quantity) AS total
            FROM Stock s
            GROUP BY s.warehouse.name
            ORDER BY SUM(s.quantity) DESC, LOWER(s.warehouse.name) ASC
            """)
    List<WarehouseQuantity> sumQuantityByWarehouse();

    /** Projection row for {@link #minQuantityByWarehouse}. */
    interface WarehouseMinQuantity {
        Long getWarehouseId();
        Integer getMinQuantity();
    }

    /**
     * Lowest stock quantity per warehouse id, computed in the database.
     * Used by the map to color warehouse pins by stock health: the worst
     * (lowest) quantity in a warehouse determines whether the whole pin
     * reads OK, WARNING or CRITICAL.
     */
    @Query("""
            SELECT s.warehouse.id AS warehouseId, MIN(s.quantity) AS minQuantity
            FROM Stock s
            GROUP BY s.warehouse.id
            """)
    List<WarehouseMinQuantity> minQuantityByWarehouse();
}
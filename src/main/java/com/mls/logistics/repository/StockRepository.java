package com.mls.logistics.repository;

import com.mls.logistics.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

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
     * Finds all stock records for a specific resource across all warehouses.
     *
     * Used to calculate total available quantity of a resource
     * regardless of warehouse location.
     *
     * @param resourceId the resource identifier
     * @return list of stock records for this resource
     */
    java.util.List<Stock> findByResourceId(Long resourceId);

    List<Stock> findByQuantityLessThan(int threshold);

    long countByQuantityLessThan(int threshold);

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
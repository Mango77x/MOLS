package com.mls.logistics.repository;

import com.mls.logistics.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

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
}
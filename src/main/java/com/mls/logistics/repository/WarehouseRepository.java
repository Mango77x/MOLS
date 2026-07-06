package com.mls.logistics.repository;

import com.mls.logistics.domain.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repository for accessing Warehouse data from the database.
 */
public interface WarehouseRepository
        extends JpaRepository<Warehouse, Long>, JpaSpecificationExecutor<Warehouse> {
	// Custom queries will be added later if needed
}

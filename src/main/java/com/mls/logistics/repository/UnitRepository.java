package com.mls.logistics.repository;

import com.mls.logistics.domain.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repository for accessing Unit data from the database.
 */
public interface UnitRepository
        extends JpaRepository<Unit, Long>, JpaSpecificationExecutor<Unit> {
    // Custom queries will be added later if needed
}

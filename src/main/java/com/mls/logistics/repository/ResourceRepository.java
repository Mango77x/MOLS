package com.mls.logistics.repository;

import com.mls.logistics.domain.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repository for accessing Resource data from the database.
 */
public interface ResourceRepository
        extends JpaRepository<Resource, Long>, JpaSpecificationExecutor<Resource> {
    // Custom queries will be added later if needed
}

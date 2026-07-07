package com.mls.logistics.repository;

import com.mls.logistics.domain.Resource;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository for accessing Resource data from the database.
 */
public interface ResourceRepository
        extends JpaRepository<Resource, Long>, JpaSpecificationExecutor<Resource> {

    /**
     * Same as {@link #findById} but acquires a row-level lock on the
     * resource for the duration of the caller's transaction.
     *
     * <p>Used by {@code OrderItemService} to serialize the
     * check-reserved-then-reserve sequence when creating/editing/releasing
     * order items against the same resource, closing the check-then-act
     * race that plain reads leave open under concurrent requests.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resource r where r.id = :id")
    Optional<Resource> findByIdForUpdate(@Param("id") Long id);
}

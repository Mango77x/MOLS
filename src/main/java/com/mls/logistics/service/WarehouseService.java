package com.mls.logistics.service;

import com.mls.logistics.domain.Warehouse;
import com.mls.logistics.dto.request.UpdateWarehouseRequest;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.repository.WarehouseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import com.mls.logistics.dto.request.CreateWarehouseRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for Warehouse-related business operations.
 * 
 * This class acts as an intermediary between controllers and repositories,
 * enforcing business rules and application logic.
 */
@Service
@Transactional(readOnly = true)  // Default for all methods
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    /**
     * Constructor-based dependency injection.
     * This is the recommended approach in Spring.
     */
    public WarehouseService(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    /**
     * Retrieves all registered warehouses.
     */
    public List<Warehouse> getAllWarehouses() {
        return warehouseRepository.findAll();
    }

    public List<Warehouse> getAllWarehouses(Sort sort) {
        return warehouseRepository.findAll(sort);
    }

    /**
     * Retrieves a page of warehouses.
     */
    public Page<Warehouse> getAllWarehouses(Pageable pageable) {
        return warehouseRepository.findAll(pageable);
    }

    /**
     * Retrieves a warehouse by its identifier.
     */
    public Optional<Warehouse> getWarehouseById(Long id) {
        return warehouseRepository.findById(id);
    }

    /**
     * Creates a new warehouse.
     * 
     * Business rules can be added here in the future
     * (e.g. warehouse capacity validation).
     */
    @Transactional
    public Warehouse createWarehouse(Warehouse warehouse) {
        return warehouseRepository.save(warehouse);
    }

    /**
     * Creates a new warehouse from a DTO request.
     * 
     * This method separates API contracts from domain logic.
     *
     * @param request DTO containing warehouse data
     * @return created warehouse entity
     */
    @Transactional  // Overrides class-level readOnly=true
    public Warehouse createWarehouse(CreateWarehouseRequest request) {
        Warehouse warehouse = new Warehouse();
        warehouse.setName(request.getName());
        warehouse.setLocation(request.getLocation());
        return warehouseRepository.save(warehouse);
    }

    /**
     * Updates an existing warehouse.
     * 
     * Only non-null fields from the request are updated.
     *
     * @param id warehouse identifier
     * @param request update data
     * @return updated warehouse
     * @throws ResourceNotFoundException if warehouse doesn't exist
     */
    @Transactional
    public Warehouse updateWarehouse(Long id, UpdateWarehouseRequest request) {
        Warehouse warehouse = warehouseRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", id));

        if (request.getName() != null) {
            warehouse.setName(request.getName());
        }
        if (request.getLocation() != null) {
            warehouse.setLocation(request.getLocation());
        }

        return warehouseRepository.save(warehouse);
    }

    /**
     * Deletes a warehouse by ID.
     *
     * @param id warehouse identifier
     * @throws ResourceNotFoundException if warehouse doesn't exist
     */
    @Transactional
    public void deleteWarehouse(Long id) {
        if (!warehouseRepository.existsById(id)) {
            throw new ResourceNotFoundException("Warehouse", "id", id);
        }
        warehouseRepository.deleteById(id);
    }
}

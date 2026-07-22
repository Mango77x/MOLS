package com.mls.logistics.service;

import com.mls.logistics.domain.Warehouse;
import com.mls.logistics.dto.request.UpdateWarehouseRequest;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.repository.OrderRepository;
import com.mls.logistics.repository.ShipmentRepository;
import com.mls.logistics.repository.StockRepository;
import com.mls.logistics.repository.WarehouseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import com.mls.logistics.dto.request.CreateWarehouseRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final StockRepository stockRepository;
    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;

    /**
     * Constructor-based dependency injection.
     * This is the recommended approach in Spring.
     */
    public WarehouseService(
            WarehouseRepository warehouseRepository,
            StockRepository stockRepository,
            OrderRepository orderRepository,
            ShipmentRepository shipmentRepository) {
        this.warehouseRepository = warehouseRepository;
        this.stockRepository = stockRepository;
        this.orderRepository = orderRepository;
        this.shipmentRepository = shipmentRepository;
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
     * Retrieves a page of warehouses matching the optional filters.
     *
     * @param name case-insensitive name fragment; ignored when null/blank
     */
    public Page<Warehouse> searchWarehouses(String name, Pageable pageable) {
        List<Specification<Warehouse>> filters = new ArrayList<>();
        if (name != null && !name.isBlank()) {
            String pattern = "%" + name.trim().toLowerCase() + "%";
            filters.add((root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern));
        }
        return warehouseRepository.findAll(Specification.allOf(filters), pageable);
    }

    /**
     * Retrieves a warehouse by its identifier.
     */
    public Optional<Warehouse> getWarehouseById(Long id) {
        return warehouseRepository.findById(id);
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
        warehouse.setLatitude(request.getLatitude());
        warehouse.setLongitude(request.getLongitude());
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
        if (request.getLatitude() != null) {
            warehouse.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            warehouse.setLongitude(request.getLongitude());
        }

        return warehouseRepository.save(warehouse);
    }

    /**
     * Deletes a warehouse by ID.
     *
     * @param id warehouse identifier
     * @throws ResourceNotFoundException if warehouse doesn't exist
     * @throws InvalidRequestException if the warehouse still has stock, orders, or shipments referencing it
     */
    @Transactional
    public void deleteWarehouse(Long id) {
        if (!warehouseRepository.existsById(id)) {
            throw new ResourceNotFoundException("Warehouse", "id", id);
        }
        // A warehouse is referenced by stock (audit-relevant once it has
        // movement history), and, independently, by orders/shipments that
        // use it as their origin — all three FKs are required (non-null),
        // so any of them existing must block the delete rather than cascade.
        if (stockRepository.existsByWarehouseId(id)) {
            throw new InvalidRequestException(
                "WAREHOUSE_DELETE_HAS_STOCK",
                Map.of("warehouseId", id),
                "Cannot delete warehouse with existing stock. Warehouse id: " + id);
        }
        if (orderRepository.existsByWarehouseId(id)) {
            throw new InvalidRequestException(
                "WAREHOUSE_DELETE_HAS_ORDERS",
                Map.of("warehouseId", id),
                "Cannot delete warehouse with existing orders. Warehouse id: " + id);
        }
        if (shipmentRepository.existsByWarehouseId(id)) {
            throw new InvalidRequestException(
                "WAREHOUSE_DELETE_HAS_SHIPMENTS",
                Map.of("warehouseId", id),
                "Cannot delete warehouse with existing shipments. Warehouse id: " + id);
        }
        warehouseRepository.deleteById(id);
    }
}

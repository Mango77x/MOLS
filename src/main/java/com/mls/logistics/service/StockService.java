package com.mls.logistics.service;

import com.mls.logistics.domain.Movement;
import com.mls.logistics.domain.MovementType;
import com.mls.logistics.domain.Resource;
import com.mls.logistics.domain.Stock;
import com.mls.logistics.domain.Warehouse;
import com.mls.logistics.dto.request.AdjustStockRequest;
import com.mls.logistics.dto.request.CreateStockRequest;
import com.mls.logistics.exception.InsufficientStockException;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.repository.MovementRepository;
import com.mls.logistics.repository.StockRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service layer for Stock-related business operations.
 *
 * Enforces the following business rules:
 * - Stock quantity can never go negative (Rule 1)
 * - Every stock change automatically generates a Movement audit record (Rule 2)
 *
 * Direct quantity manipulation via updateStock() is intentionally prohibited.
 * All quantity changes must go through adjustStock() to guarantee auditability.
 */
@Service
@Transactional(readOnly = true)
public class StockService {

    private final StockRepository stockRepository;
    private final MovementRepository movementRepository;

    /**
     * Constructor-based dependency injection.
     *
     * MovementRepository is injected here because StockService is responsible
     * for creating Movement records whenever stock changes. This keeps the
     * audit trail creation coupled to the stock change, not to the caller.
     *
     * @param stockRepository    repository for stock persistence
     * @param movementRepository repository for movement audit persistence
     */
    public StockService(StockRepository stockRepository,
                        MovementRepository movementRepository) {
        this.stockRepository = stockRepository;
        this.movementRepository = movementRepository;
    }

    /**
     * Retrieves all registered stocks.
     */
    public List<Stock> getAllStocks() {
        return stockRepository.findAll();
    }

    public List<Stock> getAllStocks(Sort sort) {
        return stockRepository.findAll(sort);
    }

    /**
     * Retrieves a page of stock records.
     */
    public Page<Stock> getAllStocks(Pageable pageable) {
        return stockRepository.findAll(pageable);
    }

    /**
     * Retrieves a stock by its identifier.
     */
    public Optional<Stock> getStockById(Long id) {
        return stockRepository.findById(id);
    }

    /**
     * Finds a stock record by resource and warehouse.
     *
     * Useful for UI flows to avoid creating duplicate stock rows.
     */
    public Optional<Stock> getStockByResourceAndWarehouse(Long resourceId, Long warehouseId) {
        return stockRepository.findByResourceIdAndWarehouseId(resourceId, warehouseId);
    }

    /**
     * Creates a new stock record for a resource in a warehouse.
     *
     * Initial quantity must be zero or positive.
     * If initial quantity is greater than zero, an ENTRY movement is recorded.
     *
     * @param request DTO containing stock creation data
     * @return created stock entity
     * @throws InvalidRequestException if initial quantity is negative
     */
    @Transactional
    public Stock createStock(CreateStockRequest request) {
        // Rule 1: initial quantity cannot be negative
        if (request.getQuantity() < 0) {
            throw new InvalidRequestException(
                "Initial stock quantity cannot be negative. Provided: " + request.getQuantity()
            );
        }

        Stock stock = new Stock();

        Resource resource = new Resource();
        resource.setId(request.getResourceId());
        stock.setResource(resource);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(request.getWarehouseId());
        stock.setWarehouse(warehouse);

        stock.setQuantity(request.getQuantity());
        Stock savedStock = stockRepository.save(stock);

        // Rule 2: if initial quantity > 0, record an ENTRY movement
        if (request.getQuantity() > 0) {
            recordMovement(savedStock, MovementType.ENTRY, request.getQuantity(), null, null, null);
        }

        return savedStock;
    }

    /**
     * Adjusts stock quantity by a delta value.
     *
     * This is the ONLY method that modifies stock quantity.
     * Direct quantity updates are prohibited to guarantee audit trail integrity.
     *
     * The delta can be positive (adding stock) or negative (removing stock).
     * Movement type is determined automatically:
     * - Positive delta → ENTRY
     * - Negative delta → EXIT
     *
     * @param id      stock identifier
     * @param request DTO containing the quantity delta and optional reason
     * @return updated stock entity
     * @throws ResourceNotFoundException   if stock does not exist
     * @throws InsufficientStockException  if adjustment would result in negative quantity
     * @throws InvalidRequestException     if delta is zero
     */
    @Transactional
    public Stock adjustStock(Long id, AdjustStockRequest request) {
        Stock stock = stockRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock", "id", id));

        int delta = request.getDelta();

        // Reject zero adjustments — they serve no purpose and pollute the audit trail
        if (delta == 0) {
            throw new InvalidRequestException(
                "Stock adjustment delta cannot be zero."
            );
        }

        int newQuantity = stock.getQuantity() + delta;

        // Rule 1: stock quantity can never go negative
        if (newQuantity < 0) {
            throw new InsufficientStockException(
                "Insufficient stock. Available: " + stock.getQuantity() +
                ", requested reduction: " + Math.abs(delta) +
                ". Stock id: " + id
            );
        }

        stock.setQuantity(newQuantity);
        Stock savedStock = stockRepository.save(stock);

        // Rule 2: record movement automatically
        MovementType movementType = delta > 0 ? MovementType.ENTRY : MovementType.EXIT;
        recordMovement(savedStock, movementType, Math.abs(delta), request.getReason(), request.getOrderId(), request.getShipmentId());

        return savedStock;
    }

    /**
     * Returns the total available quantity of a resource across all warehouses.
     *
     * Used by OrderItemService to validate order requests against available supply.
     *
     * @param resourceId the resource identifier
     * @return total quantity available across all warehouses
     */
    public int getTotalAvailableQuantity(Long resourceId) {
        return stockRepository
                .findByResourceId(resourceId)
                .stream()
                .mapToInt(Stock::getQuantity)
                .sum();
    }

    /**
     * Aggregates current stock quantity by warehouse name.
     *
     * This is used by the dashboard to render the stock distribution chart.
     */
    public Map<String, Long> getStockQuantityByWarehouse() {
        Map<String, Long> aggregated = stockRepository.findAll()
                .stream()
                .filter(s -> s.getWarehouse() != null)
                .collect(Collectors.groupingBy(
                        s -> {
                            String name = s.getWarehouse().getName();
                            return (name == null || name.isBlank()) ? "(Unnamed)" : name;
                        },
                        Collectors.summingLong(s -> (long) s.getQuantity())
                ));

        return aggregated.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    public long countByQuantityLessThan(int threshold) {
        return stockRepository.countByQuantityLessThan(threshold);
    }

    public List<Stock> getLowStockItems(int threshold) {
        return stockRepository.findByQuantityLessThan(threshold);
    }

    /**
     * Deletes a stock record.
     *
     * <p>Only stock records with no movement history can be deleted. Once a
     * stock row has movements, deleting it would destroy part of the audit
     * trail; the correct way to retire it is to adjust its quantity to zero
     * (which itself is audited).</p>
     *
     * @param id stock identifier
     * @throws ResourceNotFoundException if stock does not exist
     * @throws InvalidRequestException if the stock has movement history
     */
    @Transactional
    public void deleteStock(Long id) {
        if (!stockRepository.existsById(id)) {
            throw new ResourceNotFoundException("Stock", "id", id);
        }
        if (movementRepository.existsByStockId(id)) {
            throw new InvalidRequestException(
                "Cannot delete stock with movement history: the audit trail is append-only. " +
                "Adjust the quantity to zero instead. Stock id: " + id);
        }
        stockRepository.deleteById(id);
    }

    /**
     * Records a Movement audit entry for a stock change.
     *
     * Private method — called internally after every stock quantity change.
     * Movements are never created directly from the API for stock changes.
     *
     * @param stock    the stock that was modified
     * @param type     movement type: ENTRY or EXIT
     * @param quantity absolute quantity affected (always positive)
     */
    private void recordMovement(Stock stock,
                                MovementType type,
                                int quantity,
                                String reason,
                                Long orderId,
                                Long shipmentId) {
        Movement movement = new Movement();
        movement.setStock(stock);
        movement.setType(type);
        movement.setQuantity(quantity);
        movement.setDateTime(LocalDateTime.now());
        movement.setReason(reason);
        movement.setOrderId(orderId);
        movement.setShipmentId(shipmentId);
        movementRepository.save(movement);
    }
}
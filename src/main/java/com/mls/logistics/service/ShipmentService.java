package com.mls.logistics.service;

import com.mls.logistics.domain.Order;
import com.mls.logistics.domain.OrderItem;
import com.mls.logistics.domain.Shipment;
import com.mls.logistics.domain.ShipmentStatus;
import com.mls.logistics.domain.Vehicle;
import com.mls.logistics.domain.Warehouse;
import com.mls.logistics.dto.request.AdjustStockRequest;
import com.mls.logistics.dto.request.CreateShipmentRequest;
import com.mls.logistics.dto.request.UpdateShipmentRequest;
import com.mls.logistics.exception.InsufficientStockException;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.repository.ShipmentRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for Shipment-related business operations.
 *
 * <p>This service sits between controllers and repositories and is responsible for applying
 * shipment business rules while keeping controllers free of business logic.</p>
 *
 * <h3>Status state machine</h3>
 * <p>Shipment statuses follow the {@link ShipmentStatus} state machine. In particular,
 * {@code DELIVERED} is terminal: reverting a delivered shipment would corrupt stock
 * accounting, so it is rejected.</p>
 *
 * <h3>Fulfillment rule</h3>
 * <ul>
 *   <li>When a shipment transitions to {@code DELIVERED}, the system deducts stock from the
 *   shipment's origin warehouse for each item in the associated order.</li>
 *   <li>Stock deduction is performed via {@link StockService#adjustStock(Long, com.mls.logistics.dto.request.AdjustStockRequest)}
 *   to guarantee that each deduction produces a {@code Movement} audit record (EXIT).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final OrderItemService orderItemService;
    private final StockService stockService;
    private final OrderService orderService;

    /**
     * Constructor-based dependency injection.
     *
     * @param shipmentRepository repository for shipment persistence
     * @param orderItemService   used to load order items during fulfillment
     * @param stockService       used to deduct stock and generate movement audit records
     */
    public ShipmentService(ShipmentRepository shipmentRepository,
                           OrderItemService orderItemService,
                           StockService stockService,
                           OrderService orderService) {
        this.shipmentRepository = shipmentRepository;
        this.orderItemService = orderItemService;
        this.stockService = stockService;
        this.orderService = orderService;
    }

    /**
     * Retrieves all registered shipments.
     *
     * @return all shipments
     */
    public List<Shipment> getAllShipments() {
        return shipmentRepository.findAll();
    }

    /**
     * Retrieves all registered shipments with sorting.
     *
     * @param sort sorting configuration
     * @return sorted list of shipments
     */
    public List<Shipment> getAllShipments(Sort sort) {
        return shipmentRepository.findAll(sort);
    }

    /**
     * Retrieves a shipment by its identifier.
     *
     * @param id shipment identifier
     * @return optional shipment
     */
    public Optional<Shipment> getShipmentById(Long id) {
        return shipmentRepository.findById(id);
    }

    /**
     * Lists shipments associated with a given order.
     */
    public List<Shipment> getShipmentsByOrderId(Long orderId, Sort sort) {
        return shipmentRepository.findByOrderId(orderId, sort);
    }

    /**
     * Creates a new shipment.
     *
     * <p>If the shipment is created with status {@code DELIVERED}, fulfillment is executed immediately.</p>
     *
     * @param shipment shipment entity
     * @return created shipment
     */
    @Transactional
    public Shipment createShipment(Shipment shipment) {
        Long orderId = shipment != null && shipment.getOrder() != null ? shipment.getOrder().getId() : null;
        assertOrderIsOpen(orderId);

        Shipment saved = shipmentRepository.save(shipment);

        if (isDelivered(saved.getStatus())) {
            fulfillIfOrderIsComplete(saved);
        }

        return saved;
    }

    /**
     * Creates a new shipment from a DTO request.
     *
     * <p>This keeps API contracts (DTOs) separate from domain entities. The request is mapped to a
     * {@link Shipment} with references to {@link Order}, {@link Vehicle} and {@link Warehouse} using IDs.
     * The status string is validated and converted to {@link ShipmentStatus}.</p>
     *
     * <p>If the shipment is created with status {@code DELIVERED}, fulfillment is executed immediately.</p>
     *
     * @param request create shipment request
     * @return created shipment
     */
    @Transactional
    public Shipment createShipment(CreateShipmentRequest request) {
        Shipment shipment = new Shipment();

        Order order = new Order();
        order.setId(request.getOrderId());

        Vehicle vehicle = new Vehicle();
        vehicle.setId(request.getVehicleId());

        Warehouse warehouse = new Warehouse();
        warehouse.setId(request.getWarehouseId());

        shipment.setOrder(order);
        shipment.setVehicle(vehicle);
        shipment.setWarehouse(warehouse);
        shipment.setStatus(ShipmentStatus.from(request.getStatus()));

        assertOrderIsOpen(request.getOrderId());

        Shipment saved = shipmentRepository.save(shipment);

        if (isDelivered(saved.getStatus())) {
            fulfillIfOrderIsComplete(saved);
        }

        return saved;
    }

    /**
     * Updates an existing shipment.
     *
     * <p>Only non-null fields from {@code request} are applied. Status changes are validated
     * against the {@link ShipmentStatus} state machine.</p>
     *
     * <p>When the shipment transitions from a non-delivered status to {@code DELIVERED}, this method
     * triggers fulfillment (stock deductions + movement audit entries).</p>
     *
     * @param id      shipment identifier
     * @param request update request
     * @return updated shipment
     * @throws ResourceNotFoundException if shipment doesn't exist
     * @throws InvalidRequestException if the status transition is invalid or required references are missing
     * @throws InsufficientStockException if stock is insufficient to fulfill the order from the origin warehouse
     */
    @Transactional
    public Shipment updateShipment(Long id, UpdateShipmentRequest request) {
        Shipment shipment = shipmentRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", "id", id));

        ShipmentStatus previousStatus = shipment.getStatus();

        // If the shipment is linked to a closed order, no further changes should be allowed.
        Long existingOrderId = shipment.getOrder() != null ? shipment.getOrder().getId() : null;
        if (existingOrderId != null && isOrderClosed(existingOrderId)) {
            throw new InvalidRequestException(
                "Cannot modify a shipment for a COMPLETED or CANCELLED order. Shipment id: " + id + ", order id: " + existingOrderId
            );
        }

        if (request.getOrderId() != null) {
            assertOrderIsOpen(request.getOrderId());
            Order order = new Order();
            order.setId(request.getOrderId());
            shipment.setOrder(order);
        }
        if (request.getVehicleId() != null) {
            Vehicle vehicle = new Vehicle();
            vehicle.setId(request.getVehicleId());
            shipment.setVehicle(vehicle);
        }
        if (request.getWarehouseId() != null) {
            Warehouse warehouse = new Warehouse();
            warehouse.setId(request.getWarehouseId());
            shipment.setWarehouse(warehouse);
        }
        if (request.getStatus() != null) {
            ShipmentStatus nextStatus = ShipmentStatus.from(request.getStatus());
            ShipmentStatus current = previousStatus != null ? previousStatus : ShipmentStatus.PLANNED;
            if (!current.canTransitionTo(nextStatus)) {
                throw new InvalidRequestException(
                    "Invalid shipment status transition: " + current + " -> " + nextStatus +
                    ". Shipment id: " + id);
            }
            shipment.setStatus(nextStatus);
        }

        if (!isDelivered(previousStatus) && isDelivered(shipment.getStatus())) {
            fulfillIfOrderIsComplete(shipment);
        }

        return shipmentRepository.save(shipment);
    }

    /**
     * Deletes a shipment by ID.
     *
     * @param id shipment identifier
     * @throws ResourceNotFoundException if shipment doesn't exist
     * @throws InvalidRequestException if the shipment is DELIVERED (its stock movements already happened)
     */
    @Transactional
    public void deleteShipment(Long id) {
        Shipment shipment = shipmentRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", "id", id));

        // A delivered shipment already produced stock deductions and audit records;
        // deleting it would orphan that history.
        if (isDelivered(shipment.getStatus())) {
            throw new InvalidRequestException(
                "Cannot delete a DELIVERED shipment: its stock movements are part of the audit trail. Shipment id: " + id);
        }

        shipmentRepository.deleteById(id);
    }

    public long countByStatus(ShipmentStatus status) {
        return shipmentRepository.countByStatus(status);
    }

    /**
     * Executes shipment fulfillment.
     *
     * <p>For each {@link OrderItem} of the shipment's order, the corresponding stock record is located
     * in the shipment's origin warehouse, then adjusted by a negative delta (EXIT). The underlying
     * {@link StockService} ensures that each adjustment is audited as a {@code Movement} record.</p>
     *
     * <p>This method assumes it is called only when transitioning to {@code DELIVERED} (or creating
     * a shipment already delivered), and is designed to fail fast when required references are missing.</p>
     *
     * @param shipment persisted shipment
     */
    private void fulfillIfOrderIsComplete(Shipment shipment) {
        if (shipment.getId() == null) {
            throw new InvalidRequestException("Shipment must be persisted before fulfillment.");
        }
        if (shipment.getOrder() == null || shipment.getOrder().getId() == null) {
            throw new InvalidRequestException(
                    "Cannot deliver shipment without an associated order. Shipment id: " + shipment.getId()
            );
        }
        if (shipment.getWarehouse() == null || shipment.getWarehouse().getId() == null) {
            throw new InvalidRequestException(
                    "Cannot deliver shipment without an origin warehouse. Shipment id: " + shipment.getId()
            );
        }

        Long orderId = shipment.getOrder().getId();

        // Only complete (and fulfill) an order once all shipments are delivered.
        if (!areAllShipmentsDeliveredForOrder(orderId)) {
            return;
        }

        // Guard: if the order is already closed, do not fulfill again.
        if (isOrderClosed(orderId)) {
            return;
        }

        Long warehouseId = shipment.getWarehouse().getId();
        Long shipmentId = shipment.getId();

        Sort itemSort = Sort.by(Sort.Direction.ASC, "id");
        List<OrderItem> items = orderItemService.getOrderItemsByOrderId(orderId, itemSort);

        for (OrderItem item : items) {
            if (item.getResource() == null || item.getResource().getId() == null) {
                throw new InvalidRequestException(
                        "Order item is missing a resource reference. Order id: " + orderId
                );
            }

            int quantity = item.getQuantity();
            if (quantity <= 0) {
                throw new InvalidRequestException(
                        "Order item quantity must be positive. Provided: " + quantity + ". Order id: " + orderId
                );
            }

            Long resourceId = item.getResource().getId();
            var stock = stockService
                    .getStockByResourceAndWarehouse(resourceId, warehouseId)
                    .orElseThrow(() -> new InsufficientStockException(
                            "No stock record found for resource id: " + resourceId +
                                    " in warehouse id: " + warehouseId +
                                    ". Cannot deliver shipment id: " + shipment.getId()
                    ));

                stockService.adjustStock(
                    stock.getId(),
                    new AdjustStockRequest(
                        -quantity,
                        "Shipment delivered",
                        orderId,
                        shipmentId
                    )
                );
        }

        // Shipment is delivered and fulfillment succeeded: mark the parent order as completed.
        orderService.markOrderCompleted(orderId);
    }

    private boolean areAllShipmentsDeliveredForOrder(Long orderId) {
        if (orderId == null) {
            return false;
        }
        Sort sort = Sort.by(Sort.Direction.ASC, "id");
        List<Shipment> shipments = shipmentRepository.findByOrderId(orderId, sort);
        if (shipments == null || shipments.isEmpty()) {
            return false;
        }
        return shipments.stream().allMatch(s -> isDelivered(s.getStatus()));
    }

    private void assertOrderIsOpen(Long orderId) {
        if (orderId == null) {
            return;
        }
        if (isOrderClosed(orderId)) {
            throw new InvalidRequestException(
                "Cannot create or update shipments for a COMPLETED or CANCELLED order. Order id: " + orderId);
        }
    }

    /**
     * True when the order is in a terminal status (COMPLETED / CANCELLED).
     */
    private boolean isOrderClosed(Long orderId) {
        return orderService
                .getOrderById(orderId)
                .map(o -> o.getStatus() != null && o.getStatus().isTerminal())
                .orElse(false);
    }

    private boolean isDelivered(ShipmentStatus status) {
        return status == ShipmentStatus.DELIVERED;
    }
}

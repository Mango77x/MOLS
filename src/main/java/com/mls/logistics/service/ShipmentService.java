package com.mls.logistics.service;

import com.mls.logistics.domain.Order;
import com.mls.logistics.domain.OrderItem;
import com.mls.logistics.domain.Shipment;
import com.mls.logistics.domain.ShipmentItem;
import com.mls.logistics.domain.ShipmentStatus;
import com.mls.logistics.domain.Vehicle;
import com.mls.logistics.dto.request.AdjustStockRequest;
import com.mls.logistics.dto.request.CreateShipmentRequest;
import com.mls.logistics.dto.request.ShipmentItemLineRequest;
import com.mls.logistics.dto.request.UpdateShipmentRequest;
import com.mls.logistics.exception.InsufficientStockException;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.repository.ShipmentItemRepository;
import com.mls.logistics.repository.ShipmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
 * <h3>Items and partial fulfillment</h3>
 * <ul>
 *   <li>A shipment carries a specific subset of its order's items, each with its own
 *   quantity ({@link ShipmentItem}), fixed at creation and only replaceable as a whole
 *   set on update while the shipment is not yet {@code DELIVERED} (see
 *   {@link #replaceItems}). A line's quantity can never exceed what's still unallocated
 *   on that order item across every shipment (any status) — see {@link #buildItems}.</li>
 *   <li>When a shipment transitions to {@code DELIVERED}, the system deducts stock from the
 *   shipment's origin warehouse only for <strong>that shipment's own items</strong> — not
 *   gated on sibling shipments of the same order also being delivered, so delivering the
 *   first of several shipments for an order has an immediate, visible effect.</li>
 *   <li>Stock deduction is performed via {@link StockService#adjustStock(Long, com.mls.logistics.dto.request.AdjustStockRequest)}
 *   to guarantee that each deduction produces a {@code Movement} audit record (EXIT), and
 *   the matching portion of each order item's stock reservation is released via
 *   {@link OrderItemService#releasePartialReservation}.</li>
 *   <li>After each delivery, the parent order moves to {@code COMPLETED} once every item is
 *   fully delivered, or {@code PARTIALLY_SHIPPED} once some (but not all) items are.</li>
 *   <li>The origin warehouse is never chosen independently — it is always inherited from the
 *   order ({@code Order.warehouse}), the same warehouse {@code OrderItemService} reserved stock
 *   against, so this deduction can never target a different, unreserved warehouse.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    private final OrderItemService orderItemService;
    private final StockService stockService;
    private final OrderService orderService;

    /**
     * Constructor-based dependency injection.
     *
     * @param shipmentRepository     repository for shipment persistence
     * @param shipmentItemRepository repository for shipment-item persistence and allocation queries
     * @param orderItemService       used to load order items and manage their reservations during fulfillment
     * @param stockService           used to deduct stock and generate movement audit records
     * @param orderService           used to load orders and drive order-level fulfillment status
     */
    public ShipmentService(ShipmentRepository shipmentRepository,
                           ShipmentItemRepository shipmentItemRepository,
                           OrderItemService orderItemService,
                           StockService stockService,
                           OrderService orderService) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentItemRepository = shipmentItemRepository;
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
     * Retrieves a page of shipments.
     *
     * @param pageable pagination configuration
     * @return page of shipments
     */
    public Page<Shipment> getAllShipments(Pageable pageable) {
        return shipmentRepository.findAll(pageable);
    }

    /**
     * Retrieves a page of shipments matching the optional filters.
     *
     * @param status  shipment status; ignored when null/blank
     * @param orderId restrict to one order; ignored when null
     */
    public Page<Shipment> searchShipments(String status, Long orderId, Pageable pageable) {
        List<Specification<Shipment>> filters = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            ShipmentStatus parsed = ShipmentStatus.from(status);
            filters.add((root, query, cb) -> cb.equal(root.get("status"), parsed));
        }
        if (orderId != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("order").get("id"), orderId));
        }
        return shipmentRepository.findAll(Specification.allOf(filters), pageable);
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
     * Creates a new shipment from a DTO request.
     *
     * <p>This keeps API contracts (DTOs) separate from domain entities. The request is mapped to a
     * {@link Shipment} with references to {@link Order} and {@link Vehicle} by id; the warehouse is
     * inherited from the order, not part of the request. The status string is validated and converted
     * to {@link ShipmentStatus}. {@code request.getItems()} is validated against each order item's
     * remaining (unallocated) quantity and attached to the shipment — see {@link #buildItems}.</p>
     *
     * <p>If the shipment is created with status {@code DELIVERED}, fulfillment is executed immediately.</p>
     *
     * @param request create shipment request
     * @return created shipment
     */
    @Transactional
    public Shipment createShipment(CreateShipmentRequest request) {
        Order order = orderService
                .getOrderById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", request.getOrderId()));
        assertOrderIsOpen(order);

        Shipment shipment = new Shipment();

        Vehicle vehicle = new Vehicle();
        vehicle.setId(request.getVehicleId());

        shipment.setOrder(order);
        shipment.setVehicle(vehicle);
        // Inherited from the order, never chosen independently: the order's
        // items already reserved stock against this specific warehouse, so
        // delivery must deduct from that same warehouse — never a different
        // one the caller might otherwise have picked.
        shipment.setWarehouse(order.getWarehouse());
        shipment.setStatus(ShipmentStatus.from(request.getStatus()));

        Shipment saved = shipmentRepository.save(shipment);
        attachItems(saved, request.getItems());

        if (isDelivered(saved.getStatus())) {
            fulfillIfOrderIsComplete(saved);
        }

        return saved;
    }

    /**
     * Updates an existing shipment.
     *
     * <p>Only non-null fields from {@code request} are applied. Status changes are validated
     * against the {@link ShipmentStatus} state machine. When {@code request.getItems()} is
     * provided, it replaces the shipment's entire item set (see {@link #replaceItems}) — only
     * allowed while the shipment is not yet {@code DELIVERED}.</p>
     *
     * <p>When the shipment transitions from a non-delivered status to {@code DELIVERED}, this method
     * triggers fulfillment (stock deductions + movement audit entries) for the shipment's own items.</p>
     *
     * @param id      shipment identifier
     * @param request update request
     * @return updated shipment
     * @throws ResourceNotFoundException if shipment doesn't exist
     * @throws InvalidRequestException if the status transition is invalid, items are changed on a
     *         DELIVERED shipment, or required references are missing
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
            Order order = orderService
                    .getOrderById(request.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order", "id", request.getOrderId()));
            assertOrderIsOpen(order);
            shipment.setOrder(order);
            // Follows the new order's warehouse — see createShipment for why
            // a shipment never chooses its own independently of its order.
            shipment.setWarehouse(order.getWarehouse());
        }
        if (request.getVehicleId() != null) {
            Vehicle vehicle = new Vehicle();
            vehicle.setId(request.getVehicleId());
            shipment.setVehicle(vehicle);
        }
        if (request.getItems() != null) {
            if (isDelivered(previousStatus)) {
                throw new InvalidRequestException(
                    "Cannot change items on a DELIVERED shipment: its stock movements are part of the audit trail. Shipment id: " + id);
            }
            if (request.getItems().isEmpty()) {
                throw new InvalidRequestException("Shipment must include at least one item. Shipment id: " + id);
            }
            replaceItems(shipment, request.getItems());
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

        // Non-delivered shipments never touched stock reservations, so deleting
        // them (cascading their items via Shipment.items' orphanRemoval) simply
        // frees the allocation for future shipments — no extra bookkeeping.
        shipmentRepository.deleteById(id);
    }

    public long countByStatus(ShipmentStatus status) {
        return shipmentRepository.countByStatus(status);
    }

    /**
     * Validates and attaches item lines to an already-persisted, item-less shipment.
     */
    private void attachItems(Shipment shipment, List<ShipmentItemLineRequest> lines) {
        List<ShipmentItem> items = buildItems(shipment, lines);
        shipmentItemRepository.saveAll(items);
        shipment.getItems().clear();
        shipment.getItems().addAll(items);
    }

    /**
     * Replaces a shipment's entire item set: deletes and flushes the existing rows
     * first so the allocation check below never counts the shipment's own soon-to-be-
     * replaced lines as still-outstanding, then validates and inserts the new set.
     */
    private void replaceItems(Shipment shipment, List<ShipmentItemLineRequest> lines) {
        List<ShipmentItem> existing = shipmentItemRepository.findByShipmentId(shipment.getId());
        if (!existing.isEmpty()) {
            shipmentItemRepository.deleteAll(existing);
            shipmentItemRepository.flush();
        }
        attachItems(shipment, lines);
    }

    /**
     * Builds (but does not persist) {@link ShipmentItem}s for the given lines, validating
     * that each line's order item belongs to the shipment's order and that its quantity
     * does not exceed what's still unallocated on that order item — the order item's
     * quantity minus what every shipment (any status) has already claimed.
     *
     * @throws ResourceNotFoundException if a referenced order item doesn't exist
     * @throws InvalidRequestException if an order item doesn't belong to this shipment's
     *         order, or a line's quantity exceeds the order item's remaining allocation
     */
    private List<ShipmentItem> buildItems(Shipment shipment, List<ShipmentItemLineRequest> lines) {
        List<ShipmentItem> items = new ArrayList<>();
        for (ShipmentItemLineRequest line : lines) {
            OrderItem orderItem = orderItemService
                    .getOrderItemById(line.getOrderItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("OrderItem", "id", line.getOrderItemId()));

            Long shipmentOrderId = shipment.getOrder() != null ? shipment.getOrder().getId() : null;
            Long itemOrderId = orderItem.getOrder() != null ? orderItem.getOrder().getId() : null;
            if (shipmentOrderId == null || !shipmentOrderId.equals(itemOrderId)) {
                throw new InvalidRequestException(
                    "Order item id " + line.getOrderItemId() + " does not belong to this shipment's order.");
            }

            int alreadyAllocated = sumOrderItemQuantity(
                    shipmentItemRepository.sumQuantityByOrderItemIds(List.of(orderItem.getId())));
            int remaining = orderItem.getQuantity() - alreadyAllocated;
            if (line.getQuantity() > remaining) {
                throw new InvalidRequestException(
                    "Cannot ship " + line.getQuantity() + " of order item id " + orderItem.getId() +
                    ": only " + remaining + " remains unallocated (ordered " + orderItem.getQuantity() + ").");
            }

            ShipmentItem shipmentItem = new ShipmentItem();
            shipmentItem.setShipment(shipment);
            shipmentItem.setOrderItem(orderItem);
            shipmentItem.setQuantity(line.getQuantity());
            items.add(shipmentItem);
        }
        return items;
    }

    /**
     * Executes shipment fulfillment for one delivered shipment.
     *
     * <p>For each {@link ShipmentItem} of <strong>this shipment</strong> (not the whole
     * order), the corresponding stock record is located in the shipment's origin
     * warehouse, adjusted by a negative delta (EXIT), and the matching portion of its
     * order item's reservation is released. Afterwards, every order item's cumulative
     * delivered quantity (across all shipments) is recomputed to decide whether the
     * order is now fully or only partially shipped.</p>
     *
     * <p>This method assumes it is called only when transitioning to {@code DELIVERED} (or creating
     * a shipment already delivered), and is designed to fail fast when required references are missing.</p>
     *
     * @param shipment persisted shipment, already carrying its items
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

        // Guard: if the order is already closed, do not fulfill again.
        if (isOrderClosed(orderId)) {
            return;
        }

        Long warehouseId = shipment.getWarehouse().getId();
        Long shipmentId = shipment.getId();

        List<ShipmentItem> items = shipment.getItems();
        if (items == null || items.isEmpty()) {
            throw new InvalidRequestException("Cannot deliver a shipment with no items. Shipment id: " + shipmentId);
        }

        for (ShipmentItem shipmentItem : items) {
            OrderItem orderItem = shipmentItem.getOrderItem();
            if (orderItem.getResource() == null || orderItem.getResource().getId() == null) {
                throw new InvalidRequestException(
                        "Order item is missing a resource reference. Order id: " + orderId
                );
            }

            int quantity = shipmentItem.getQuantity();
            Long resourceId = orderItem.getResource().getId();
            var stock = stockService
                    .getStockByResourceAndWarehouse(resourceId, warehouseId)
                    .orElseThrow(() -> new InsufficientStockException(
                            "No stock record found for resource id: " + resourceId +
                                    " in warehouse id: " + warehouseId +
                                    ". Cannot deliver shipment id: " + shipmentId
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

            int deliveredSoFar = sumOrderItemQuantity(
                    shipmentItemRepository.sumDeliveredQuantityByOrderItemIds(List.of(orderItem.getId())));
            boolean fullyDelivered = deliveredSoFar >= orderItem.getQuantity();
            orderItemService.releasePartialReservation(orderItem, quantity, fullyDelivered);
        }

        // Recompute overall order fulfillment from every item's cumulative delivered total.
        List<OrderItem> orderItems = orderItemService.getOrderItemsByOrderId(orderId, Sort.by(Sort.Direction.ASC, "id"));
        boolean allFullyDelivered = true;
        boolean anyDelivered = false;
        for (OrderItem orderItem : orderItems) {
            int delivered = sumOrderItemQuantity(
                    shipmentItemRepository.sumDeliveredQuantityByOrderItemIds(List.of(orderItem.getId())));
            if (delivered > 0) {
                anyDelivered = true;
            }
            if (delivered < orderItem.getQuantity()) {
                allFullyDelivered = false;
            }
        }

        if (allFullyDelivered) {
            orderService.markOrderCompleted(orderId);
        } else if (anyDelivered) {
            orderService.markOrderPartiallyShipped(orderId);
        }
    }

    private int sumOrderItemQuantity(List<ShipmentItemRepository.OrderItemQuantity> rows) {
        return rows.stream()
                .findFirst()
                .map(row -> row.getTotal() != null ? row.getTotal().intValue() : 0)
                .orElse(0);
    }

    private void assertOrderIsOpen(Order order) {
        if (order != null && order.getStatus() != null && order.getStatus().isTerminal()) {
            throw new InvalidRequestException(
                "Cannot create or update shipments for a COMPLETED or CANCELLED order. Order id: " + order.getId());
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

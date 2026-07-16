package com.mls.logistics.service;

import com.mls.logistics.domain.Order;
import com.mls.logistics.domain.OrderStatus;
import com.mls.logistics.domain.Unit;
import com.mls.logistics.domain.Warehouse;
import com.mls.logistics.dto.request.CreateOrderRequest;
import com.mls.logistics.dto.request.CreateOrderItemRequest;
import com.mls.logistics.dto.request.UpdateOrderRequest;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for Order-related business operations.
 *
 * This class acts as an intermediary between controllers and repositories,
 * enforcing business rules and application logic.
 *
 * <h3>Status state machine</h3>
 * Order statuses are a formal state machine (see {@link OrderStatus}).
 * Any status change goes through {@link #applyStatusTransition(Order, OrderStatus)}
 * which rejects invalid transitions (e.g. reopening a COMPLETED order).
 */
@Service
@Transactional(readOnly = true)
public class OrderService {

    /** Statuses that still accept work (items, shipments, edits). */
    private static final List<OrderStatus> OPEN_STATUSES =
            List.of(OrderStatus.CREATED, OrderStatus.VALIDATED, OrderStatus.PARTIALLY_SHIPPED);

    /** Terminal statuses — no further changes allowed. */
    private static final List<OrderStatus> TERMINAL_STATUSES =
            List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED);

    private final OrderRepository orderRepository;
    private final OrderItemService orderItemService;

    /**
     * Constructor-based dependency injection.
     * This is the recommended approach in Spring.
     */
    public OrderService(OrderRepository orderRepository, OrderItemService orderItemService) {
        this.orderRepository = orderRepository;
        this.orderItemService = orderItemService;
    }

    /**
     * Retrieves all registered orders.
     */
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public List<Order> getAllOrders(Sort sort) {
        return orderRepository.findAll(sort);
    }

    /**
     * Retrieves a page of orders.
     */
    public Page<Order> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    /**
     * Retrieves a page of orders matching the optional filters.
     *
     * @param status order status; ignored when null/blank
     * @param unitId restrict to one requesting unit; ignored when null
     */
    public Page<Order> searchOrders(String status, Long unitId, Pageable pageable) {
        List<Specification<Order>> filters = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            OrderStatus parsed = OrderStatus.from(status);
            filters.add((root, query, cb) -> cb.equal(root.get("status"), parsed));
        }
        if (unitId != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("unit").get("id"), unitId));
        }
        return orderRepository.findAll(Specification.allOf(filters), pageable);
    }

    /**
     * Retrieves orders that are still open (not COMPLETED / CANCELLED).
     *
     * Used by the UI to offer valid targets for new shipments.
     */
    public List<Order> getOpenOrders(Sort sort) {
        return orderRepository.findByStatusNotIn(TERMINAL_STATUSES, sort);
    }

    /**
     * Retrieves an order by its identifier.
     */
    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    /**
     * Creates a new order.
     */
    @Transactional
    public Order createOrder(Order order) {
        return orderRepository.save(order);
    }

    /**
     * Creates a new order from a DTO request.
     *
     * This method separates API contracts from domain logic. The status
     * string is validated and converted to {@link OrderStatus}; unknown
     * values are rejected with a 400.
     *
     * @param request DTO containing order data
     * @return created order entity
     */
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        Order order = new Order();

        Unit unit = new Unit();
        unit.setId(request.getUnitId());

        Warehouse warehouse = new Warehouse();
        warehouse.setId(request.getWarehouseId());

        order.setUnit(unit);
        order.setWarehouse(warehouse);
        order.setDateCreated(request.getDateCreated());
        order.setStatus(OrderStatus.from(request.getStatus()));

        return orderRepository.save(order);
    }

    /**
     * Creates an order and its items in a single database transaction.
     *
     * If any item fails validation (for example insufficient stock), nothing is saved.
     */
    @Transactional
    public Order createOrderWithItems(CreateOrderRequest request, List<CreateOrderItemRequest> items) {
        Order order = createOrder(request);

        if (items != null) {
            for (CreateOrderItemRequest item : items) {
                CreateOrderItemRequest toCreate = new CreateOrderItemRequest(
                        order.getId(),
                        item.getResourceId(),
                        item.getQuantity()
                );
                orderItemService.createOrderItem(toCreate);
            }
        }

        return order;
    }

    /**
     * Updates an existing order.
     *
     * Only non-null fields from the request are updated. Status changes are
     * validated against the {@link OrderStatus} state machine.
     *
     * @param id order identifier
     * @param request update data
     * @return updated order
     * @throws ResourceNotFoundException if order doesn't exist
     * @throws InvalidRequestException if the status transition is not allowed
     */
    @Transactional
    public Order updateOrder(Long id, UpdateOrderRequest request) {
        Order order = orderRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        if (request.getUnitId() != null) {
            Unit unit = new Unit();
            unit.setId(request.getUnitId());
            order.setUnit(unit);
        }
        if (request.getDateCreated() != null) {
            order.setDateCreated(request.getDateCreated());
        }
        if (request.getStatus() != null) {
            applyStatusTransition(order, OrderStatus.from(request.getStatus()));
        }

        return orderRepository.save(order);
    }

    /**
     * Deletes an order by ID.
     *
     * @param id order identifier
     * @throws ResourceNotFoundException if order doesn't exist
     * @throws InvalidRequestException if the order is COMPLETED (its stock movements are part of the audit trail)
     */
    @Transactional
    public void deleteOrder(Long id) {
        Order order = orderRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        // A completed order was fulfilled: stock was deducted and movements were
        // recorded against it. Deleting it would orphan that audit history.
        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new InvalidRequestException(
                "Cannot delete a COMPLETED order: its stock movements are part of the audit trail. Order id: " + id);
        }

        // Release any still-outstanding item reservations before the cascade
        // delete removes the rows — deleteById cascades via JPA, bypassing
        // OrderItemService, so this is the only chance to do it. A no-op for
        // orders that were already CANCELLED (already released then).
        orderItemService.releaseReservationsForOrder(id);

        orderRepository.deleteById(id);
    }

    public long getTotalOrdersCount() {
        return orderRepository.count();
    }

    public long countByStatus(OrderStatus status) {
        return orderRepository.countByStatus(status);
    }

    /**
     * Fulfillment rate as a percentage of completed orders vs total orders.
     */
    public double getFulfillmentRate() {
        long total = orderRepository.count();
        if (total == 0) {
            return 0.0;
        }

        long completed = orderRepository.countByStatus(OrderStatus.COMPLETED);
        return (completed * 100.0) / total;
    }

    /**
     * Marks an order as completed.
     *
     * Used by shipment fulfillment: when all shipments of an order are
     * delivered, the parent order is considered fulfilled. Idempotent —
     * already-completed orders are left untouched.
     */
    @Transactional
    public void markOrderCompleted(Long orderId) {
        if (orderId == null) {
            return;
        }

        Order order = orderRepository
                .findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (order.getStatus() == OrderStatus.COMPLETED) {
            return;
        }

        applyStatusTransition(order, OrderStatus.COMPLETED);
        orderRepository.save(order);
    }

    /**
     * Marks an order as partially shipped.
     *
     * Used by shipment fulfillment: when a shipment delivers some, but not
     * all, of an order's items. Idempotent and only moves forward — a no-op
     * if the order is already {@code PARTIALLY_SHIPPED} or {@code COMPLETED}
     * (a faster concurrent delivery that already finished the order is never
     * regressed back to partial).
     */
    @Transactional
    public void markOrderPartiallyShipped(Long orderId) {
        if (orderId == null) {
            return;
        }

        Order order = orderRepository
                .findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (order.getStatus() == OrderStatus.PARTIALLY_SHIPPED || order.getStatus() == OrderStatus.COMPLETED) {
            return;
        }

        applyStatusTransition(order, OrderStatus.PARTIALLY_SHIPPED);
        orderRepository.save(order);
    }

    /**
     * Returns open (pre-fulfillment) orders older than the provided threshold (in days).
     */
    public List<Order> getStaleOrders(int daysThreshold) {
        LocalDate cutoff = LocalDate.now().minus(daysThreshold, ChronoUnit.DAYS);

        List<Order> results = orderRepository
                .findByStatusInAndDateCreatedBefore(OPEN_STATUSES, cutoff);

        return results.stream()
                .sorted(Comparator
                        .comparing(Order::getDateCreated, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Order::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * Applies a status change after validating it against the state machine.
     *
     * A null current status (legacy rows) is treated as CREATED.
     *
     * <p>Moving from an open status into a terminal one (COMPLETED or
     * CANCELLED) releases every item's stock reservation — whether that
     * happens here (manual completion, or cancellation) or via
     * {@link #markOrderCompleted} (fulfillment-driven completion), it's the
     * same idempotent release, so calling it from both places never
     * double-releases.</p>
     *
     * @throws InvalidRequestException if the transition is not allowed
     */
    private void applyStatusTransition(Order order, OrderStatus next) {
        OrderStatus current = order.getStatus() != null ? order.getStatus() : OrderStatus.CREATED;
        if (!current.canTransitionTo(next)) {
            throw new InvalidRequestException(
                "Invalid order status transition: " + current + " -> " + next +
                ". Order id: " + order.getId());
        }
        order.setStatus(next);
        if (next.isTerminal() && !current.isTerminal()) {
            orderItemService.releaseReservationsForOrder(order.getId());
        }
    }
}

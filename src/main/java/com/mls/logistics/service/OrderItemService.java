package com.mls.logistics.service;

import com.mls.logistics.domain.OrderItem;
import com.mls.logistics.domain.Order;
import com.mls.logistics.domain.OrderStatus;
import com.mls.logistics.domain.Resource;
import com.mls.logistics.dto.request.CreateOrderItemRequest;
import com.mls.logistics.dto.request.UpdateOrderItemRequest;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.repository.OrderItemRepository;
import com.mls.logistics.repository.OrderRepository;
import com.mls.logistics.repository.ResourceRepository;
import com.mls.logistics.exception.InsufficientStockException;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for OrderItem-related business operations.
 *
 * This class acts as an intermediary between controllers and repositories,
 * enforcing business rules and application logic.
 *
 * <h3>Stock reservation</h3>
 * <p>Creating/editing an order item reserves stock against
 * {@link Resource#getReservedQuantity()} instead of only checking physical
 * stock: the check-and-reserve sequence locks the resource row
 * ({@link ResourceRepository#findByIdForUpdate}) so concurrent requests
 * against the same resource serialize, and the reservation itself means a
 * second order can no longer promise stock a first order already claimed
 * (physical stock alone can't tell the two apart). The reservation is
 * released exactly once — via {@link #releaseReservation} /
 * {@link #releaseReservationsForOrder} — when the order is cancelled,
 * completed, or the item/order is deleted; see {@code OrderService} and
 * {@code ShipmentService} for the call sites.</p>
 */
@Service
@Transactional(readOnly = true)
public class OrderItemService {

    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final ResourceRepository resourceRepository;
    private final StockService stockService;

    /**
     * Constructor-based dependency injection.
     * This is the recommended approach in Spring.
     */
    public OrderItemService(OrderItemRepository orderItemRepository,
                            OrderRepository orderRepository,
                            ResourceRepository resourceRepository,
                            @Lazy StockService stockService) {
        this.orderItemRepository = orderItemRepository;
        this.orderRepository = orderRepository;
        this.resourceRepository = resourceRepository;
        this.stockService = stockService;
    }

    /**
     * Retrieves all registered order items.
     */
    public List<OrderItem> getAllOrderItems() {
        return orderItemRepository.findAll();
    }

    /**
     * Retrieves a page of order items.
     */
    public Page<OrderItem> getAllOrderItems(Pageable pageable) {
        return orderItemRepository.findAll(pageable);
    }

    /**
     * Retrieves a page of order items matching the optional filters.
     *
     * @param orderId restrict to one order; ignored when null
     */
    public Page<OrderItem> searchOrderItems(Long orderId, Pageable pageable) {
        if (orderId == null) {
            return orderItemRepository.findAll(pageable);
        }
        return orderItemRepository.findByOrderId(orderId, pageable);
    }

    /**
     * Retrieves all items for a specific order.
     */
    public List<OrderItem> getOrderItemsByOrderId(Long orderId, Sort sort) {
        return orderItemRepository.findByOrderId(orderId, sort);
    }

    public List<OrderItem> getOrderItemsByOrderIds(List<Long> orderIds, Sort sort) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return orderItemRepository.findByOrderIdIn(orderIds, sort);
    }

    /**
     * Retrieves an order item by its identifier.
     */
    public Optional<OrderItem> getOrderItemById(Long id) {
        return orderItemRepository.findById(id);
    }

    /**
     * Creates a new order item.
     * 
     * Business rules can be added here in the future
     * (e.g. item quantity validation).
     */
    @Transactional
    public OrderItem createOrderItem(OrderItem orderItem) {
        return orderItemRepository.save(orderItem);
    }

    /**
     * Creates a new order item from a DTO request.
     *
     * This method separates API contracts from domain logic.
     *
     * @param request DTO containing order item data
     * @return created order item entity
     * @throws ResourceNotFoundException if the order doesn't exist
     * @throws InvalidRequestException if the order is COMPLETED or CANCELLED
     * @throws InsufficientStockException if the requested quantity exceeds what's still unreserved
     */
    @Transactional
    public OrderItem createOrderItem(CreateOrderItemRequest request) {
        Order order = orderRepository
                .findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", request.getOrderId()));
        assertOrderIsOpen(order);

        reserve(request.getResourceId(), request.getQuantity());

        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        Resource resource = new Resource();
        resource.setId(request.getResourceId());
        orderItem.setResource(resource);
        orderItem.setQuantity(request.getQuantity());
        orderItem.setReservationActive(true);
        return orderItemRepository.save(orderItem);
    }

    /**
     * Updates an existing order item.
     *
     * Only non-null fields from the request are updated. When the resource
     * or quantity changes, the old reservation is released and the new one
     * is reserved within the same transaction (see {@link #reserve}).
     *
     * @param id order item identifier
     * @param request update data
     * @return updated order item
     * @throws ResourceNotFoundException if order item doesn't exist
     * @throws InvalidRequestException if the item's (or target) order is COMPLETED or CANCELLED
     * @throws InsufficientStockException if the requested quantity exceeds what's still unreserved
     */
    @Transactional
    public OrderItem updateOrderItem(Long id, UpdateOrderItemRequest request) {
        OrderItem orderItem = orderItemRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OrderItem", "id", id));

        if (orderItem.getOrder() != null) {
            assertOrderIsOpen(orderItem.getOrder());
        }

        Long previousResourceId = orderItem.getResource() != null ? orderItem.getResource().getId() : null;
        int previousQuantity = orderItem.getQuantity();

        Long effectiveResourceId = request.getResourceId() != null ? request.getResourceId() : previousResourceId;
        int effectiveQuantity = request.getQuantity() != null ? request.getQuantity() : previousQuantity;

        boolean reservationChanged = orderItem.isReservationActive()
                && (!effectiveResourceId.equals(previousResourceId) || effectiveQuantity != previousQuantity);

        if (reservationChanged) {
            release(previousResourceId, previousQuantity);
            try {
                reserve(effectiveResourceId, effectiveQuantity);
            } catch (RuntimeException ex) {
                // Roll the released amount back so a failed re-reservation
                // doesn't silently shrink the original one (best-effort;
                // the enclosing transaction will roll back anyway).
                reserve(previousResourceId, previousQuantity);
                throw ex;
            }
        }

        if (request.getOrderId() != null) {
            Order newOrder = orderRepository
                    .findById(request.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order", "id", request.getOrderId()));
            assertOrderIsOpen(newOrder);
            orderItem.setOrder(newOrder);
        }
        if (request.getResourceId() != null) {
            Resource resource = new Resource();
            resource.setId(request.getResourceId());
            orderItem.setResource(resource);
        }
        if (request.getQuantity() != null) {
            orderItem.setQuantity(request.getQuantity());
        }

        return orderItemRepository.save(orderItem);
    }

    /**
     * Deletes an order item by ID, releasing its stock reservation (if
     * still outstanding) first.
     *
     * @param id order item identifier
     * @throws ResourceNotFoundException if order item doesn't exist
     */
    @Transactional
    public void deleteOrderItem(Long id) {
        OrderItem orderItem = orderItemRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OrderItem", "id", id));
        releaseReservation(orderItem);
        orderItemRepository.deleteById(id);
    }

    /**
     * Releases every still-outstanding reservation held by an order's
     * items. Idempotent per item — safe to call from multiple paths
     * (order cancellation, manual completion, fulfillment-driven
     * completion, order deletion) without double-releasing.
     *
     * @param orderId order identifier; a no-op if null
     */
    @Transactional
    public void releaseReservationsForOrder(Long orderId) {
        if (orderId == null) {
            return;
        }
        for (OrderItem item : orderItemRepository.findByOrderId(orderId, Sort.unsorted())) {
            releaseReservation(item);
        }
    }

    /**
     * Releases a single order item's reservation if it is still active.
     * No-op otherwise, so callers never need to track whether a release
     * already happened.
     */
    @Transactional
    public void releaseReservation(OrderItem item) {
        if (item == null || !item.isReservationActive()) {
            return;
        }
        Long resourceId = item.getResource() != null ? item.getResource().getId() : null;
        release(resourceId, item.getQuantity());
        item.setReservationActive(false);
        orderItemRepository.save(item);
    }

    /**
     * Locks the resource row, checks it has enough physical stock left
     * after subtracting what's already reserved by other order items, and
     * — if so — reserves {@code quantity} against it.
     *
     * <p>The lock is held for the rest of the caller's transaction, so a
     * second concurrent call for the same resource blocks here until the
     * first commits (or rolls back), instead of both reading the same
     * stale "available" figure and over-committing it.</p>
     *
     * @throws ResourceNotFoundException if the resource doesn't exist
     * @throws InsufficientStockException if quantity exceeds physical stock minus existing reservations
     */
    private void reserve(Long resourceId, int quantity) {
        Resource resource = resourceRepository
                .findByIdForUpdate(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource", "id", resourceId));

        int physicalTotal = stockService.getTotalAvailableQuantity(resourceId);
        int trulyAvailable = physicalTotal - resource.getReservedQuantity();
        if (quantity > trulyAvailable) {
            throw new InsufficientStockException(
                "Cannot reserve stock. Requested: " + quantity +
                ", available (physical stock minus existing reservations): " + trulyAvailable +
                ". Resource id: " + resourceId
            );
        }

        resource.setReservedQuantity(resource.getReservedQuantity() + quantity);
        resourceRepository.save(resource);
    }

    /**
     * Locks the resource row and releases a previously-held reservation.
     * Clamped at zero and silently no-ops for a missing resource — a
     * release must never itself fail, or a cancellation/deletion could get
     * stuck unable to complete.
     */
    private void release(Long resourceId, int quantity) {
        if (resourceId == null || quantity <= 0) {
            return;
        }
        resourceRepository.findByIdForUpdate(resourceId).ifPresent(resource -> {
            resource.setReservedQuantity(Math.max(0, resource.getReservedQuantity() - quantity));
            resourceRepository.save(resource);
        });
    }

    /**
     * @throws InvalidRequestException if the order is COMPLETED or CANCELLED
     */
    private void assertOrderIsOpen(Order order) {
        OrderStatus status = order.getStatus();
        if (status != null && status.isTerminal()) {
            throw new InvalidRequestException(
                "Cannot add or modify items on a COMPLETED or CANCELLED order. Order id: " + order.getId()
            );
        }
    }
}

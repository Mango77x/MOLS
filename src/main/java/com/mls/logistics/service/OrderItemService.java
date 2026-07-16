package com.mls.logistics.service;

import com.mls.logistics.domain.OrderItem;
import com.mls.logistics.domain.Order;
import com.mls.logistics.domain.OrderStatus;
import com.mls.logistics.domain.Resource;
import com.mls.logistics.domain.Stock;
import com.mls.logistics.dto.request.CreateOrderItemRequest;
import com.mls.logistics.dto.request.UpdateOrderItemRequest;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.repository.OrderItemRepository;
import com.mls.logistics.repository.OrderRepository;
import com.mls.logistics.repository.ShipmentItemRepository;
import com.mls.logistics.repository.StockRepository;
import com.mls.logistics.exception.InsufficientStockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Service layer for OrderItem-related business operations.
 *
 * This class acts as an intermediary between controllers and repositories,
 * enforcing business rules and application logic.
 *
 * <h3>Stock reservation</h3>
 * <p>Every order has a fixed origin warehouse (see {@code Order.warehouse}),
 * so creating/editing an order item reserves stock against that specific
 * {@code (resource, warehouse)} {@link Stock} row instead of only checking
 * physical quantity: the check-and-reserve sequence locks the stock row
 * ({@link StockRepository#findByResourceIdAndWarehouseIdForUpdate}) so
 * concurrent requests against the same resource+warehouse serialize, and the
 * reservation itself means a second order sourced from the same warehouse
 * can no longer promise stock a first order already claimed. Because the
 * warehouse is fixed at order creation, a validated item can never fail at
 * delivery for a warehouse mismatch — the deduction in {@code ShipmentService}
 * always targets the same stock row the reservation was made against. The
 * reservation is released exactly once — via {@link #releaseReservation} /
 * {@link #releaseReservationsForOrder} — when the order is cancelled,
 * completed, or the item/order is deleted; see {@code OrderService} and
 * {@code ShipmentService} for the call sites.</p>
 */
@Service
@Transactional(readOnly = true)
public class OrderItemService {

    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final StockRepository stockRepository;
    private final ShipmentItemRepository shipmentItemRepository;

    /**
     * Constructor-based dependency injection.
     * This is the recommended approach in Spring.
     */
    public OrderItemService(OrderItemRepository orderItemRepository,
                            OrderRepository orderRepository,
                            StockRepository stockRepository,
                            ShipmentItemRepository shipmentItemRepository) {
        this.orderItemRepository = orderItemRepository;
        this.orderRepository = orderRepository;
        this.stockRepository = stockRepository;
        this.shipmentItemRepository = shipmentItemRepository;
    }

    /** Per-item shipment progress: what's been delivered, and what's still unallocated. */
    public record ShippingProgress(int deliveredQuantity, int remainingQuantity) {
    }

    /**
     * Computes {@link ShippingProgress} for a batch of order items in two
     * database round trips total (one for delivered totals, one for overall
     * allocated totals), regardless of how many items are passed — building
     * an {@code OrderItemResponse} page never costs a query per row.
     */
    public Map<Long, ShippingProgress> shippingProgress(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = items.stream().map(OrderItem::getId).toList();

        Map<Long, Integer> delivered = new HashMap<>();
        for (ShipmentItemRepository.OrderItemQuantity row : shipmentItemRepository.sumDeliveredQuantityByOrderItemIds(ids)) {
            delivered.put(row.getOrderItemId(), row.getTotal() != null ? row.getTotal().intValue() : 0);
        }
        Map<Long, Integer> allocated = new HashMap<>();
        for (ShipmentItemRepository.OrderItemQuantity row : shipmentItemRepository.sumQuantityByOrderItemIds(ids)) {
            allocated.put(row.getOrderItemId(), row.getTotal() != null ? row.getTotal().intValue() : 0);
        }

        Map<Long, ShippingProgress> result = new HashMap<>();
        for (OrderItem item : items) {
            int deliveredQty = delivered.getOrDefault(item.getId(), 0);
            int remainingQty = item.getQuantity() - allocated.getOrDefault(item.getId(), 0);
            result.put(item.getId(), new ShippingProgress(deliveredQty, remainingQty));
        }
        return result;
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

        reserve(request.getResourceId(), order.getWarehouse().getId(), request.getQuantity());

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

        Order previousOrder = orderItem.getOrder();
        if (previousOrder != null) {
            assertOrderIsOpen(previousOrder);
        }
        Long previousWarehouseId = previousOrder != null && previousOrder.getWarehouse() != null
                ? previousOrder.getWarehouse().getId() : null;

        Order targetOrder = previousOrder;
        if (request.getOrderId() != null) {
            targetOrder = orderRepository
                    .findById(request.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order", "id", request.getOrderId()));
            assertOrderIsOpen(targetOrder);
        }
        Long targetWarehouseId = targetOrder != null && targetOrder.getWarehouse() != null
                ? targetOrder.getWarehouse().getId() : null;

        Long previousResourceId = orderItem.getResource() != null ? orderItem.getResource().getId() : null;
        int previousQuantity = orderItem.getQuantity();

        Long effectiveResourceId = request.getResourceId() != null ? request.getResourceId() : previousResourceId;
        int effectiveQuantity = request.getQuantity() != null ? request.getQuantity() : previousQuantity;

        // An item already carried (in full or in part) by a shipment can't
        // change what it represents, or shrink below what's already
        // allocated — the shipment's own line would then refer to more (or
        // something else) than this item actually is.
        int allocated = allocatedQuantity(id);
        if (allocated > 0) {
            boolean resourceChanging = request.getResourceId() != null && !request.getResourceId().equals(previousResourceId);
            Long previousOrderId = previousOrder != null ? previousOrder.getId() : null;
            boolean orderChanging = request.getOrderId() != null && !request.getOrderId().equals(previousOrderId);
            if (resourceChanging || orderChanging) {
                throw new InvalidRequestException(
                    "Cannot change the resource or order of an order item already allocated to a shipment. Order item id: " + id);
            }
            if (effectiveQuantity < allocated) {
                throw new InvalidRequestException(
                    "Cannot reduce quantity below " + allocated + " already allocated to shipments. Order item id: " + id);
            }
        }

        // A change in resource, quantity, OR the order's warehouse (moving
        // the item to an order sourced from a different warehouse) all
        // invalidate the existing reservation the same way.
        boolean reservationChanged = orderItem.isReservationActive()
                && (!effectiveResourceId.equals(previousResourceId)
                        || effectiveQuantity != previousQuantity
                        || !Objects.equals(targetWarehouseId, previousWarehouseId));

        if (reservationChanged) {
            release(previousResourceId, previousWarehouseId, previousQuantity);
            try {
                reserve(effectiveResourceId, targetWarehouseId, effectiveQuantity);
            } catch (RuntimeException ex) {
                // Roll the released amount back so a failed re-reservation
                // doesn't silently shrink the original one (best-effort;
                // the enclosing transaction will roll back anyway).
                reserve(previousResourceId, previousWarehouseId, previousQuantity);
                throw ex;
            }
        }

        if (request.getOrderId() != null) {
            orderItem.setOrder(targetOrder);
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
     * @throws InvalidRequestException if the item is already allocated to a shipment
     */
    @Transactional
    public void deleteOrderItem(Long id) {
        OrderItem orderItem = orderItemRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OrderItem", "id", id));
        if (allocatedQuantity(id) > 0) {
            throw new InvalidRequestException(
                "Cannot delete an order item already allocated to a shipment. Order item id: " + id);
        }
        releaseReservation(orderItem);
        orderItemRepository.deleteById(id);
    }

    /** Total quantity of this order item allocated across every shipment, regardless of status. */
    private int allocatedQuantity(Long orderItemId) {
        return shipmentItemRepository.sumQuantityByOrderItemIds(List.of(orderItemId)).stream()
                .findFirst()
                .map(row -> row.getTotal() != null ? row.getTotal().intValue() : 0)
                .orElse(0);
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
        Long warehouseId = item.getOrder() != null && item.getOrder().getWarehouse() != null
                ? item.getOrder().getWarehouse().getId() : null;
        release(resourceId, warehouseId, item.getQuantity());
        item.setReservationActive(false);
        orderItemRepository.save(item);
    }

    /**
     * Releases part of an order item's reservation as it gets delivered by a
     * shipment (see {@code ShipmentService.fulfillIfOrderIsComplete}). Unlike
     * {@link #releaseReservation}, this drains only {@code quantity} from the
     * held reservation instead of the item's full quantity, since a single
     * shipment may cover only part of an item — once stock physically leaves
     * the warehouse for that portion, holding it as "reserved" as well would
     * double-count it against availability.
     *
     * @param fullyDelivered whether this call brings the item's cumulative
     *        delivered quantity up to its full {@code quantity} — if so, the
     *        reservation is marked inactive so {@link #releaseReservation}
     *        (fired on order completion/cancellation) never re-releases it
     */
    @Transactional
    public void releasePartialReservation(OrderItem item, int quantity, boolean fullyDelivered) {
        if (item == null || !item.isReservationActive() || quantity <= 0) {
            return;
        }
        Long resourceId = item.getResource() != null ? item.getResource().getId() : null;
        Long warehouseId = item.getOrder() != null && item.getOrder().getWarehouse() != null
                ? item.getOrder().getWarehouse().getId() : null;
        release(resourceId, warehouseId, quantity);
        if (fullyDelivered) {
            item.setReservationActive(false);
            orderItemRepository.save(item);
        }
    }

    /**
     * Locks the {@code (resource, warehouse)} stock row, checks it has
     * enough physical stock left after subtracting what's already reserved
     * by other order items sourced from the same warehouse, and — if so —
     * reserves {@code quantity} against it.
     *
     * <p>The lock is held for the rest of the caller's transaction, so a
     * second concurrent call for the same stock row blocks here until the
     * first commits (or rolls back), instead of both reading the same
     * stale "available" figure and over-committing it.</p>
     *
     * @throws InsufficientStockException if there's no stock record for this resource
     *         in this warehouse, or quantity exceeds physical stock minus existing reservations
     */
    private void reserve(Long resourceId, Long warehouseId, int quantity) {
        Stock stock = stockRepository
                .findByResourceIdAndWarehouseIdForUpdate(resourceId, warehouseId)
                .orElseThrow(() -> new InsufficientStockException(
                    "Cannot reserve stock: this resource has no stock record in the order's warehouse. " +
                    "Requested: " + quantity + ". Resource id: " + resourceId + ", warehouse id: " + warehouseId
                ));

        int trulyAvailable = stock.getQuantity() - stock.getReservedQuantity();
        if (quantity > trulyAvailable) {
            throw new InsufficientStockException(
                "Cannot reserve stock. Requested: " + quantity +
                ", available in this warehouse (physical stock minus existing reservations): " + trulyAvailable +
                ". Resource id: " + resourceId + ", warehouse id: " + warehouseId
            );
        }

        stock.setReservedQuantity(stock.getReservedQuantity() + quantity);
        stockRepository.save(stock);
    }

    /**
     * Locks the {@code (resource, warehouse)} stock row and releases a
     * previously-held reservation. Clamped at zero and silently no-ops when
     * no matching stock row exists — a release must never itself fail, or a
     * cancellation/deletion could get stuck unable to complete.
     */
    private void release(Long resourceId, Long warehouseId, int quantity) {
        if (resourceId == null || warehouseId == null || quantity <= 0) {
            return;
        }
        stockRepository.findByResourceIdAndWarehouseIdForUpdate(resourceId, warehouseId).ifPresent(stock -> {
            stock.setReservedQuantity(Math.max(0, stock.getReservedQuantity() - quantity));
            stockRepository.save(stock);
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

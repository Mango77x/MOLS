package com.mls.logistics.service;

import com.mls.logistics.domain.OrderItem;
import com.mls.logistics.domain.Order;
import com.mls.logistics.domain.Resource;
import com.mls.logistics.dto.request.CreateOrderItemRequest;
import com.mls.logistics.dto.request.UpdateOrderItemRequest;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.repository.OrderItemRepository;
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
 */
@Service
@Transactional(readOnly = true)
public class OrderItemService {

    private final OrderItemRepository orderItemRepository;
    private final StockService stockService;

    /**
     * Constructor-based dependency injection.
     * This is the recommended approach in Spring.
     */
    public OrderItemService(OrderItemRepository orderItemRepository,
                            @Lazy StockService stockService) {
        this.orderItemRepository = orderItemRepository;
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
     */
    @Transactional
    public OrderItem createOrderItem(CreateOrderItemRequest request) {
        // Rule 3: requested quantity must not exceed available stock
        int available = stockService.getTotalAvailableQuantity(request.getResourceId());
        if (request.getQuantity() > available) {
            throw new InsufficientStockException(
                "Cannot create order item. Requested: " + request.getQuantity() +
                ", available in stock: " + available +
                ". Resource id: " + request.getResourceId()
            );
        }

        OrderItem orderItem = new OrderItem();
        Order order = new Order();
        order.setId(request.getOrderId());
        Resource resource = new Resource();
        resource.setId(request.getResourceId());
        orderItem.setOrder(order);
        orderItem.setResource(resource);
        orderItem.setQuantity(request.getQuantity());
        return orderItemRepository.save(orderItem);
    }

    /**
     * Updates an existing order item.
     * 
     * Only non-null fields from the request are updated.
     *
     * @param id order item identifier
     * @param request update data
     * @return updated order item
     * @throws ResourceNotFoundException if order item doesn't exist
     */
    @Transactional
    public OrderItem updateOrderItem(Long id, UpdateOrderItemRequest request) {
        OrderItem orderItem = orderItemRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OrderItem", "id", id));

        Long effectiveResourceId = request.getResourceId() != null
                ? request.getResourceId()
                : (orderItem.getResource() != null ? orderItem.getResource().getId() : null);

        Integer effectiveQuantity = request.getQuantity() != null ? request.getQuantity() : null;

        // Rule 3: requested quantity must not exceed available stock
        if (effectiveResourceId != null && effectiveQuantity != null) {
            int available = stockService.getTotalAvailableQuantity(effectiveResourceId);
            if (effectiveQuantity > available) {
                throw new InsufficientStockException(
                        "Cannot update order item. Requested: " + effectiveQuantity +
                                ", available in stock: " + available +
                                ". Resource id: " + effectiveResourceId
                );
            }
        }

        if (request.getOrderId() != null) {
            Order order = new Order();
            order.setId(request.getOrderId());
            orderItem.setOrder(order);
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
     * Deletes an order item by ID.
     *
     * @param id order item identifier
     * @throws ResourceNotFoundException if order item doesn't exist
     */
    @Transactional
    public void deleteOrderItem(Long id) {
        if (!orderItemRepository.existsById(id)) {
            throw new ResourceNotFoundException("OrderItem", "id", id);
        }
        orderItemRepository.deleteById(id);
    }
}

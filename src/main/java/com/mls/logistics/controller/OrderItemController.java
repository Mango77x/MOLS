package com.mls.logistics.controller;

import com.mls.logistics.domain.OrderItem;
import com.mls.logistics.dto.request.CreateOrderItemRequest;
import com.mls.logistics.dto.request.UpdateOrderItemRequest;
import com.mls.logistics.dto.response.OrderItemResponse;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.service.OrderItemService;
import jakarta.validation.Valid;
import com.mls.logistics.dto.request.PageQuery;
import com.mls.logistics.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.stream.Collectors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Set;

/**
 * REST controller exposing OrderItem-related API endpoints.
 *
 * This controller is responsible only for HTTP request/response handling.
 * All business logic is delegated to the OrderItemService.
 */
@RestController
@RequestMapping("/api/order-items")
@Tag(name = "Order Items", description = "Operations for managing items within orders")
public class OrderItemController {

    private final OrderItemService orderItemService;

    /**
     * Constructor-based dependency injection.
     *
     * @param orderItemService service layer for order item operations
     */
    public OrderItemController(OrderItemService orderItemService) {
        this.orderItemService = orderItemService;
    }

    /** Fields clients may sort by on the list endpoint. */
    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "quantity");

    /**
     * Retrieves all order items, optionally paginated.
     *
     * GET /api/order-items
     *
     * Without query parameters the full list is returned (original contract).
     * Passing any of page/size/sort switches the response to a
     * {@link PageResponse} envelope.
     *
     * @return list of order items, or a page of order items when paginated
     */
    @Operation(
        summary = "List all order items",
        description = "Returns all registered order items. Pass page/size/sort to receive a paginated envelope instead of the plain list."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order items retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid pagination or sort parameters")
    })
    @GetMapping
    public ResponseEntity<?> getAllOrderItems(
            @Parameter(description = "Zero-based page index (enables pagination)", example = "0")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size, 1-100 (enables pagination)", example = "20")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort as 'field' or 'field,desc' (enables pagination)", example = "quantity,desc")
            @RequestParam(required = false) String sort) {
        if (page == null && size == null && sort == null) {
            List<OrderItemResponse> orderItems = orderItemService
                    .getAllOrderItems()
                    .stream()
                    .map(OrderItemResponse::from)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(orderItems);
        }
        Pageable pageable = PageQuery.toPageable(page, size, sort, SORTABLE_FIELDS, Sort.by("id"));
        return ResponseEntity.ok(PageResponse.from(
                orderItemService.getAllOrderItems(pageable), OrderItemResponse::from));
    }

    /**
     * Retrieves an order item by its identifier.
     *
     * GET /api/order-items/{id}
     *
     * @param id order item identifier
    * @return order item if found; otherwise ResourceNotFoundException is thrown and translated to 404
     */
    @Operation(
        summary = "Get order item by ID",
        description = "Returns a single order item by its unique identifier"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order item retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Order item not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<OrderItemResponse> getOrderItemById(
            @Parameter(description = "Order item identifier", example = "1")
            @PathVariable Long id) {
        OrderItem orderItem = orderItemService
                .getOrderItemById(id)
            .orElseThrow(() -> new ResourceNotFoundException("OrderItem", "id", id));
        return ResponseEntity.ok(OrderItemResponse.from(orderItem));
    }

    /**
     * Creates a new order item.
     *
     * POST /api/order-items
     *
     * @param request DTO containing order item data
     * @return created order item with HTTP 201 status
     */
    @Operation(
        summary = "Create an order item",
        description = "Creates a new order item and returns the created entity"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Order item created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PostMapping
    public ResponseEntity<OrderItemResponse> createOrderItem(@Valid @RequestBody CreateOrderItemRequest request) {
        OrderItem createdOrderItem = orderItemService.createOrderItem(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderItemResponse.from(createdOrderItem));
    }

    /**
     * Updates an existing order item.
     *
     * PUT /api/order-items/{id}
     *
     * @param id order item identifier
     * @param request update data
     * @return updated order item with HTTP 200 status
     */
    @Operation(
        summary = "Update an order item",
        description = "Updates an existing order item. Only provided fields are updated."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order item updated successfully"),
        @ApiResponse(responseCode = "404", description = "Order item not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PutMapping("/{id}")
    public ResponseEntity<OrderItemResponse> updateOrderItem(
            @Parameter(description = "Order item identifier", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderItemRequest request) {
        OrderItem updatedOrderItem = orderItemService.updateOrderItem(id, request);
        return ResponseEntity.ok(OrderItemResponse.from(updatedOrderItem));
    }

    /**
     * Deletes an order item.
     *
     * DELETE /api/order-items/{id}
     *
     * @param id order item identifier
     * @return 204 No Content on success
     */
    @Operation(
        summary = "Delete an order item",
        description = "Permanently deletes an order item from the system"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Order item deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Order item not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrderItem(
            @Parameter(description = "Order item identifier", example = "1")
            @PathVariable Long id) {
        orderItemService.deleteOrderItem(id);
        return ResponseEntity.noContent().build();
    }
}
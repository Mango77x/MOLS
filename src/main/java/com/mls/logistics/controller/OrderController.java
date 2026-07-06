package com.mls.logistics.controller;

import com.mls.logistics.domain.Order;
import com.mls.logistics.dto.request.CreateOrderRequest;
import com.mls.logistics.dto.request.UpdateOrderRequest;
import com.mls.logistics.dto.response.OrderResponse;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.service.OrderService;
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
 * REST controller exposing Order-related API endpoints.
 *
 * This controller is responsible only for HTTP request/response handling.
 * All business logic is delegated to the OrderService.
 */
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Operations for managing customer orders")
public class OrderController {

    private final OrderService orderService;

    /**
     * Constructor-based dependency injection.
     *
     * @param orderService service layer for order operations
     */
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Fields clients may sort by on the list endpoint. */
    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "dateCreated", "status");

    /**
     * Retrieves all orders, optionally paginated.
     *
     * GET /api/orders
     *
     * Without query parameters the full list is returned (original contract).
     * Passing any of page/size/sort or a filter switches the response to a
     * {@link PageResponse} envelope.
     *
     * @return list of orders, or a page of orders when paginated
     */
    @Operation(
        summary = "List all orders",
        description = "Returns all registered orders. Pass page/size/sort to receive a paginated envelope instead of the plain list."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Orders retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid pagination or sort parameters")
    })
    @GetMapping
    public ResponseEntity<?> getAllOrders(
            @Parameter(description = "Zero-based page index (enables pagination)", example = "0")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size, 1-100 (enables pagination)", example = "20")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort as 'field' or 'field,desc' (enables pagination)", example = "dateCreated,desc")
            @RequestParam(required = false) String sort,
            @Parameter(description = "Filter: order status", example = "CREATED")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filter: requesting unit identifier", example = "1")
            @RequestParam(required = false) Long unitId) {
        if (page == null && size == null && sort == null
                && status == null && unitId == null) {
            List<OrderResponse> orders = orderService
                    .getAllOrders()
                    .stream()
                    .map(OrderResponse::from)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(orders);
        }
        Pageable pageable = PageQuery.toPageable(page, size, sort, SORTABLE_FIELDS, Sort.by("id"));
        return ResponseEntity.ok(PageResponse.from(
                orderService.searchOrders(status, unitId, pageable), OrderResponse::from));
    }

    /**
     * Retrieves an order by its identifier.
     *
     * GET /api/orders/{id}
     *
     * @param id order identifier
    * @return order if found; otherwise ResourceNotFoundException is thrown and translated to 404
     */
    @Operation(
        summary = "Get order by ID",
        description = "Returns a single order by its unique identifier"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(
            @Parameter(description = "Order identifier", example = "1")
            @PathVariable Long id) {
        Order order = orderService
                .getOrderById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    /**
     * Creates a new order.
     *
     * POST /api/orders
     *
     * @param request DTO containing order data
     * @return created order with HTTP 201 status
     */
    @Operation(
        summary = "Create an order",
        description = "Creates a new order and returns the created entity"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Order created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order createdOrder = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(createdOrder));
    }

    /**
     * Updates an existing order.
     *
     * PUT /api/orders/{id}
     *
     * @param id order identifier
     * @param request update data
     * @return updated order with HTTP 200 status
     */
    @Operation(
        summary = "Update an order",
        description = "Updates an existing order. Only provided fields are updated."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order updated successfully"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PutMapping("/{id}")
    public ResponseEntity<OrderResponse> updateOrder(
            @Parameter(description = "Order identifier", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderRequest request) {
        Order updatedOrder = orderService.updateOrder(id, request);
        return ResponseEntity.ok(OrderResponse.from(updatedOrder));
    }

    /**
     * Deletes an order.
     *
     * DELETE /api/orders/{id}
     *
     * @param id order identifier
     * @return 204 No Content on success
     */
    @Operation(
        summary = "Delete an order",
        description = "Permanently deletes an order from the system"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Order deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(
            @Parameter(description = "Order identifier", example = "1")
            @PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}
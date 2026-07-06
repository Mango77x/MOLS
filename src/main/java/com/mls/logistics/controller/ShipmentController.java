package com.mls.logistics.controller;

import com.mls.logistics.domain.Shipment;
import com.mls.logistics.dto.request.CreateShipmentRequest;
import com.mls.logistics.dto.request.UpdateShipmentRequest;
import com.mls.logistics.dto.response.ShipmentResponse;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.service.ShipmentService;
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
 * REST controller exposing Shipment-related API endpoints.
 *
 * This controller is responsible only for HTTP request/response handling.
 * All business logic is delegated to the ShipmentService.
 */
@RestController
@RequestMapping("/api/shipments")
@Tag(name = "Shipments", description = "Operations for managing shipment executions")
public class ShipmentController {

    private final ShipmentService shipmentService;

    /**
     * Constructor-based dependency injection.
     *
     * @param shipmentService service layer for shipment operations
     */
    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    /** Fields clients may sort by on the list endpoint. */
    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "status");

    /**
     * Retrieves all shipments, optionally paginated.
     *
     * GET /api/shipments
     *
     * Without query parameters the full list is returned (original contract).
     * Passing any of page/size/sort or a filter switches the response to a
     * {@link PageResponse} envelope.
     *
     * @return list of shipments, or a page of shipments when paginated
     */
    @Operation(
        summary = "List all shipments",
        description = "Returns all registered shipments. Pass page/size/sort to receive a paginated envelope instead of the plain list."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Shipments retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid pagination or sort parameters")
    })
    @GetMapping
    public ResponseEntity<?> getAllShipments(
            @Parameter(description = "Zero-based page index (enables pagination)", example = "0")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size, 1-100 (enables pagination)", example = "20")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort as 'field' or 'field,desc' (enables pagination)", example = "status,asc")
            @RequestParam(required = false) String sort,
            @Parameter(description = "Filter: shipment status", example = "IN_TRANSIT")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filter: parent order identifier", example = "1")
            @RequestParam(required = false) Long orderId) {
        if (page == null && size == null && sort == null
                && status == null && orderId == null) {
            List<ShipmentResponse> shipments = shipmentService
                    .getAllShipments()
                    .stream()
                    .map(ShipmentResponse::from)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(shipments);
        }
        Pageable pageable = PageQuery.toPageable(page, size, sort, SORTABLE_FIELDS, Sort.by("id"));
        return ResponseEntity.ok(PageResponse.from(
                shipmentService.searchShipments(status, orderId, pageable), ShipmentResponse::from));
    }

    /**
     * Retrieves a shipment by its identifier.
     *
     * GET /api/shipments/{id}
     *
     * @param id shipment identifier
    * @return shipment if found; otherwise ResourceNotFoundException is thrown and translated to 404
     */
    @Operation(
        summary = "Get shipment by ID",
        description = "Returns a single shipment by its unique identifier"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Shipment retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Shipment not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ShipmentResponse> getShipmentById(
            @Parameter(description = "Shipment identifier", example = "1")
            @PathVariable Long id) {
        Shipment shipment = shipmentService
                .getShipmentById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Shipment", "id", id));
        return ResponseEntity.ok(ShipmentResponse.from(shipment));
    }

    /**
     * Creates a new shipment.
     *
     * POST /api/shipments
     *
     * @param request DTO containing shipment data
     * @return created shipment with HTTP 201 status
     */
    @Operation(
        summary = "Create a shipment",
        description = "Creates a new shipment and returns the created entity"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Shipment created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PostMapping
    public ResponseEntity<ShipmentResponse> createShipment(@Valid @RequestBody CreateShipmentRequest request) {
        Shipment createdShipment = shipmentService.createShipment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ShipmentResponse.from(createdShipment));
    }

    /**
     * Updates an existing shipment.
     *
     * PUT /api/shipments/{id}
     *
     * @param id shipment identifier
     * @param request update data
     * @return updated shipment with HTTP 200 status
     */
    @Operation(
        summary = "Update a shipment",
        description = "Updates an existing shipment. Only provided fields are updated. " +
                "Note: when status transitions to DELIVERED, the system deducts stock from the shipment's origin warehouse for each order item and records movement audit entries."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Shipment updated successfully"),
        @ApiResponse(responseCode = "404", description = "Shipment not found"),
        @ApiResponse(responseCode = "409", description = "Insufficient stock to deliver this shipment"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ShipmentResponse> updateShipment(
            @Parameter(description = "Shipment identifier", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody UpdateShipmentRequest request) {
        Shipment updatedShipment = shipmentService.updateShipment(id, request);
        return ResponseEntity.ok(ShipmentResponse.from(updatedShipment));
    }

    /**
     * Deletes a shipment.
     *
     * DELETE /api/shipments/{id}
     *
     * @param id shipment identifier
     * @return 204 No Content on success
     */
    @Operation(
        summary = "Delete a shipment",
        description = "Permanently deletes a shipment from the system"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Shipment deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Shipment not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShipment(
            @Parameter(description = "Shipment identifier", example = "1")
            @PathVariable Long id) {
        shipmentService.deleteShipment(id);
        return ResponseEntity.noContent().build();
    }
}
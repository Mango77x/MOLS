package com.mls.logistics.controller;

import com.mls.logistics.domain.Warehouse;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.service.WarehouseService;
import com.mls.logistics.dto.request.PageQuery;
import com.mls.logistics.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.mls.logistics.dto.request.CreateWarehouseRequest;
import com.mls.logistics.dto.response.WarehouseResponse;
import com.mls.logistics.dto.request.UpdateWarehouseRequest;
import java.util.stream.Collectors;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Set;

/**
 * REST controller exposing Warehouse-related API endpoints.
 *
 * This controller is responsible only for HTTP request/response handling.
 * All business logic is delegated to the WarehouseService.
 */
@RestController
@RequestMapping("/api/warehouses")
@Tag(name = "Warehouses", description = "Operations for managing storage locations")
public class WarehouseController {

    private final WarehouseService warehouseService;

    /**
     * Constructor-based dependency injection.
     *
     * @param warehouseService service layer for warehouse operations
     */
    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    /** Fields clients may sort by on the list endpoint. */
    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "name", "location");

    /**
     * Retrieves all warehouses, optionally paginated.
     *
     * GET /api/warehouses
     *
     * Without query parameters the full list is returned (original contract).
     * Passing any of page/size/sort or a filter switches the response to a
     * {@link PageResponse} envelope.
     *
     * @return list of warehouses, or a page of warehouses when paginated
     */
    @Operation(
        summary = "List all warehouses",
        description = "Returns all registered warehouses. Pass page/size/sort to receive a paginated envelope instead of the plain list."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Warehouses retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid pagination or sort parameters")
    })
    @GetMapping
    public ResponseEntity<?> getAllWarehouses(
            @Parameter(description = "Zero-based page index (enables pagination)", example = "0")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size, 1-100 (enables pagination)", example = "20")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort as 'field' or 'field,desc' (enables pagination)", example = "name,asc")
            @RequestParam(required = false) String sort,
            @Parameter(description = "Filter: case-insensitive name fragment", example = "central")
            @RequestParam(required = false) String name) {
        if (page == null && size == null && sort == null && name == null) {
            List<WarehouseResponse> warehouses = warehouseService
                    .getAllWarehouses()
                    .stream()
                    .map(WarehouseResponse::from)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(warehouses);
        }
        Pageable pageable = PageQuery.toPageable(page, size, sort, SORTABLE_FIELDS, Sort.by("id"));
        return ResponseEntity.ok(PageResponse.from(
                warehouseService.searchWarehouses(name, pageable), WarehouseResponse::from));
    }

    /**
     * Retrieves a warehouse by its identifier.
     *
     * GET /api/warehouses/{id}
     *
     * @param id warehouse identifier
    * @return warehouse if found; otherwise ResourceNotFoundException is thrown and translated to 404
     */
    @Operation(
        summary = "Get warehouse by ID",
        description = "Returns a single warehouse by its unique identifier"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Warehouse retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Warehouse not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<WarehouseResponse> getWarehouseById(
            @Parameter(description = "Warehouse identifier", example = "1")
            @PathVariable Long id) {
        Warehouse warehouse = warehouseService
                .getWarehouseById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", id));
        return ResponseEntity.ok(WarehouseResponse.from(warehouse));
    }
    
    /**
     * Creates a new warehouse.
     *
     * POST /api/warehouses
     *
     * @param request DTO containing warehouse data
     * @return created warehouse with HTTP 201 status
     */
    @Operation(
        summary = "Create a warehouse",
        description = "Creates a new warehouse and returns the created entity"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Warehouse created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PostMapping
    public ResponseEntity<WarehouseResponse> createWarehouse(@Valid @RequestBody CreateWarehouseRequest request) {
        Warehouse createdWarehouse = warehouseService.createWarehouse(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(WarehouseResponse.from(createdWarehouse));
    }

    /**
     * Updates an existing warehouse.
     *
     * PUT /api/warehouses/{id}
     *
     * @param id warehouse identifier
     * @param request update data
     * @return updated warehouse
     */
    @Operation(
        summary = "Update a warehouse",
        description = "Updates an existing warehouse. Only provided fields are updated."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Warehouse updated successfully"),
        @ApiResponse(responseCode = "404", description = "Warehouse not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PutMapping("/{id}")
    public ResponseEntity<WarehouseResponse> updateWarehouse(
            @Parameter(description = "Warehouse identifier", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody UpdateWarehouseRequest request) {
        Warehouse updatedWarehouse = warehouseService.updateWarehouse(id, request);
        return ResponseEntity.ok(WarehouseResponse.from(updatedWarehouse));
    }

    /**
     * Deletes a warehouse.
     *
     * DELETE /api/warehouses/{id}
     *
     * @param id warehouse identifier
     * @return 204 No Content on success
     */
    @Operation(
        summary = "Delete a warehouse",
        description = "Permanently deletes a warehouse from the system"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Warehouse deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Warehouse not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWarehouse(@Parameter(description = "Warehouse identifier", example = "1")
            @PathVariable Long id) { 
        warehouseService.deleteWarehouse(id);
        return ResponseEntity.noContent().build();
    }
}

package com.mls.logistics.controller;

import com.mls.logistics.domain.Vehicle;
import com.mls.logistics.dto.request.CreateVehicleRequest;
import com.mls.logistics.dto.request.UpdateVehicleRequest;
import com.mls.logistics.dto.response.VehicleResponse;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.service.VehicleService;
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
 * REST controller exposing Vehicle-related API endpoints.
 *
 * This controller is responsible only for HTTP request/response handling.
 * All business logic is delegated to the VehicleService.
 */
@RestController
@RequestMapping("/api/vehicles")
@Tag(name = "Vehicles", description = "Operations for managing transport vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    /**
     * Constructor-based dependency injection.
     *
     * @param vehicleService service layer for vehicle operations
     */
    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    /** Fields clients may sort by on the list endpoint. */
    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "type", "capacity", "status");

    /**
     * Retrieves all vehicles, optionally paginated.
     *
     * GET /api/vehicles
     *
     * Without query parameters the full list is returned (original contract).
     * Passing any of page/size/sort or a filter switches the response to a
     * {@link PageResponse} envelope.
     *
     * @return list of vehicles, or a page of vehicles when paginated
     */
    @Operation(
        summary = "List all vehicles",
        description = "Returns all registered vehicles. Pass page/size/sort to receive a paginated envelope instead of the plain list."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Vehicles retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid pagination or sort parameters")
    })
    @GetMapping
    public ResponseEntity<?> getAllVehicles(
            @Parameter(description = "Zero-based page index (enables pagination)", example = "0")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size, 1-100 (enables pagination)", example = "20")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort as 'field' or 'field,desc' (enables pagination)", example = "status,asc")
            @RequestParam(required = false) String sort,
            @Parameter(description = "Filter: exact type (case-insensitive)", example = "LAND")
            @RequestParam(required = false) String type,
            @Parameter(description = "Filter: vehicle status", example = "AVAILABLE")
            @RequestParam(required = false) String status) {
        if (page == null && size == null && sort == null && type == null && status == null) {
            List<VehicleResponse> vehicles = vehicleService
                    .getAllVehicles()
                    .stream()
                    .map(VehicleResponse::from)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(vehicles);
        }
        Pageable pageable = PageQuery.toPageable(page, size, sort, SORTABLE_FIELDS, Sort.by("id"));
        return ResponseEntity.ok(PageResponse.from(
                vehicleService.searchVehicles(type, status, pageable), VehicleResponse::from));
    }

    /**
     * Retrieves a vehicle by its identifier.
     *
     * GET /api/vehicles/{id}
     *
     * @param id vehicle identifier
    * @return vehicle if found; otherwise ResourceNotFoundException is thrown and translated to 404
     */
    @Operation(
        summary = "Get vehicle by ID",
        description = "Returns a single vehicle by its unique identifier"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Vehicle retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Vehicle not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<VehicleResponse> getVehicleById(
            @Parameter(description = "Vehicle identifier", example = "1")
            @PathVariable Long id) {
        Vehicle vehicle = vehicleService
                .getVehicleById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle", "id", id));
        return ResponseEntity.ok(VehicleResponse.from(vehicle));
    }

    /**
     * Creates a new vehicle.
     *
     * POST /api/vehicles
     *
     * @param request DTO containing vehicle data
     * @return created vehicle with HTTP 201 status
     */
    @Operation(
        summary = "Create a vehicle",
        description = "Creates a new vehicle and returns the created entity"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Vehicle created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PostMapping
    public ResponseEntity<VehicleResponse> createVehicle(@Valid @RequestBody CreateVehicleRequest request) {
        Vehicle createdVehicle = vehicleService.createVehicle(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(VehicleResponse.from(createdVehicle));
    }

    /**
     * Updates an existing vehicle.
     *
     * PUT /api/vehicles/{id}
     *
     * @param id vehicle identifier
     * @param request update data
     * @return updated vehicle with HTTP 200 status
     */
    @Operation(
        summary = "Update a vehicle",
        description = "Updates an existing vehicle. Only provided fields are updated."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Vehicle updated successfully"),
        @ApiResponse(responseCode = "404", description = "Vehicle not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PutMapping("/{id}")
    public ResponseEntity<VehicleResponse> updateVehicle(
            @Parameter(description = "Vehicle identifier", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody UpdateVehicleRequest request) {
        Vehicle updatedVehicle = vehicleService.updateVehicle(id, request);
        return ResponseEntity.ok(VehicleResponse.from(updatedVehicle));
    }

    /**
     * Deletes a vehicle.
     *
     * DELETE /api/vehicles/{id}
     *
     * @param id vehicle identifier
     * @return 204 No Content on success
     */
    @Operation(
        summary = "Delete a vehicle",
        description = "Permanently deletes a vehicle from the system"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Vehicle deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Vehicle not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVehicle(
            @Parameter(description = "Vehicle identifier", example = "1")
            @PathVariable Long id) {
        vehicleService.deleteVehicle(id);
        return ResponseEntity.noContent().build();
    }
}
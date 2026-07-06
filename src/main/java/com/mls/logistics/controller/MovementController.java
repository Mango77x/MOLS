package com.mls.logistics.controller;

import com.mls.logistics.domain.Movement;
import com.mls.logistics.dto.response.MovementResponse;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.dto.request.PageQuery;
import com.mls.logistics.dto.response.PageResponse;
import com.mls.logistics.service.MovementService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
 * REST controller exposing the Movement audit trail (read-only).
 *
 * <p>Movements are append-only audit records generated automatically by the
 * system whenever stock changes (see {@code StockService}). They cannot be
 * created, modified, or deleted through the API: corrections are made by
 * applying a new stock adjustment via {@code PATCH /api/stocks/{id}/adjust},
 * which records a compensating movement.</p>
 */
@RestController
@RequestMapping("/api/movements")
@Tag(name = "Movements", description = "Read-only stock movement audit trail")
public class MovementController {

    private final MovementService movementService;

    /**
     * Constructor-based dependency injection.
     *
     * @param movementService service layer for movement operations
     */
    public MovementController(MovementService movementService) {
        this.movementService = movementService;
    }

    /** Fields clients may sort by on the list endpoint. */
    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "dateTime", "type", "quantity");

    /**
     * Retrieves all movement records, optionally paginated.
     *
     * GET /api/movements
     *
     * Without query parameters the full list is returned (original contract).
     * Passing any of page/size/sort or a filter switches the response to a
     * {@link PageResponse} envelope; paginated results default to newest first.
     *
     * @return list of movement records, or a page of them when paginated
     */
    @Operation(
        summary = "List all movements",
        description = "Returns all registered movement records. Pass page/size/sort to receive a paginated envelope instead of the plain list (newest first by default)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Movements retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid pagination or sort parameters")
    })
    @GetMapping
    public ResponseEntity<?> getAllMovements(
            @Parameter(description = "Zero-based page index (enables pagination)", example = "0")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size, 1-100 (enables pagination)", example = "20")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort as 'field' or 'field,desc' (enables pagination)", example = "dateTime,desc")
            @RequestParam(required = false) String sort,
            @Parameter(description = "Filter: movement type", example = "ENTRY")
            @RequestParam(required = false) String type,
            @Parameter(description = "Filter: originating order identifier", example = "1")
            @RequestParam(required = false) Long orderId,
            @Parameter(description = "Filter: originating shipment identifier", example = "1")
            @RequestParam(required = false) Long shipmentId) {
        if (page == null && size == null && sort == null
                && type == null && orderId == null && shipmentId == null) {
            List<MovementResponse> movements = movementService
                    .getAllMovements()
                    .stream()
                    .map(MovementResponse::from)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(movements);
        }
        Pageable pageable = PageQuery.toPageable(page, size, sort, SORTABLE_FIELDS,
                Sort.by(Sort.Direction.DESC, "dateTime"));
        return ResponseEntity.ok(PageResponse.from(
                movementService.searchMovements(type, orderId, shipmentId, pageable),
                MovementResponse::from));
    }

    /**
     * Retrieves a movement record by its identifier.
     *
     * GET /api/movements/{id}
     *
     * @param id movement identifier
     * @return movement if found; otherwise ResourceNotFoundException is thrown and translated to 404
     */
    @Operation(
        summary = "Get movement by ID",
        description = "Returns a single movement record by its unique identifier"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Movement retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Movement not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<MovementResponse> getMovementById(
            @Parameter(description = "Movement identifier", example = "1")
            @PathVariable Long id) {
        Movement movement = movementService
                .getMovementById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Movement", "id", id));
        return ResponseEntity.ok(MovementResponse.from(movement));
    }
}

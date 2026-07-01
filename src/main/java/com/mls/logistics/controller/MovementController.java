package com.mls.logistics.controller;

import com.mls.logistics.domain.Movement;
import com.mls.logistics.dto.response.MovementResponse;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.service.MovementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.stream.Collectors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

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

    /**
     * Retrieves all movement records.
     *
     * GET /api/movements
     *
     * @return list of movement records
     */
    @Operation(
        summary = "List all movements",
        description = "Returns a list of all registered movement records in the system"
    )
    @ApiResponse(responseCode = "200", description = "Movements retrieved successfully")
    @GetMapping
    public ResponseEntity<List<MovementResponse>> getAllMovements() {
        List<MovementResponse> movements = movementService
                .getAllMovements()
                .stream()
                .map(MovementResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(movements);
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

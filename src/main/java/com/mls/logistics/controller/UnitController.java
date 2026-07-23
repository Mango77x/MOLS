package com.mls.logistics.controller;

import com.mls.logistics.domain.Unit;
import com.mls.logistics.dto.request.CreateUnitRequest;
import com.mls.logistics.dto.request.UpdateUnitRequest;
import com.mls.logistics.dto.response.ImportPreviewResponse;
import com.mls.logistics.dto.response.UnitResponse;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.service.UnitService;
import jakarta.validation.Valid;
import com.mls.logistics.dto.request.PageQuery;
import com.mls.logistics.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.stream.Collectors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Set;

/**
 * REST controller exposing Unit-related API endpoints.
 *
 * This controller is responsible only for HTTP request/response handling.
 * All business logic is delegated to the UnitService.
 */
@RestController
@RequestMapping("/api/units")
@Tag(name = "Units", description = "Operations for managing business units")
public class UnitController {

    private final UnitService unitService;

    /**
     * Constructor-based dependency injection.
     *
     * @param unitService service layer for unit operations
     */
    public UnitController(UnitService unitService) {
        this.unitService = unitService;
    }

    /** Fields clients may sort by on the list endpoint. */
    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "name", "location");

    /**
     * Retrieves all units, optionally paginated.
     *
     * GET /api/units
     *
     * Without query parameters the full list is returned (original contract).
     * Passing any of page/size/sort or a filter switches the response to a
     * {@link PageResponse} envelope.
     *
     * @return list of units, or a page of units when paginated
     */
    @Operation(
        summary = "List all units",
        description = "Returns all registered units. Pass page/size/sort to receive a paginated envelope instead of the plain list."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Units retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid pagination or sort parameters")
    })
    @GetMapping
    public ResponseEntity<?> getAllUnits(
            @Parameter(description = "Zero-based page index (enables pagination)", example = "0")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size, 1-100 (enables pagination)", example = "20")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort as 'field' or 'field,desc' (enables pagination)", example = "name,asc")
            @RequestParam(required = false) String sort,
            @Parameter(description = "Filter: case-insensitive name fragment", example = "alpha")
            @RequestParam(required = false) String name) {
        if (page == null && size == null && sort == null && name == null) {
            List<UnitResponse> units = unitService
                    .getAllUnits()
                    .stream()
                    .map(UnitResponse::from)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(units);
        }
        Pageable pageable = PageQuery.toPageable(page, size, sort, SORTABLE_FIELDS, Sort.by("id"));
        return ResponseEntity.ok(PageResponse.from(
                unitService.searchUnits(name, pageable), UnitResponse::from));
    }

    /**
     * Retrieves a unit by its identifier.
     *
     * GET /api/units/{id}
     *
     * @param id unit identifier
    * @return unit if found; otherwise ResourceNotFoundException is thrown and translated to 404
     */
    @Operation(
        summary = "Get unit by ID",
        description = "Returns a single unit by its unique identifier"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Unit retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Unit not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<UnitResponse> getUnitById(
            @Parameter(description = "Unit identifier", example = "1")
            @PathVariable Long id) {
        Unit unit = unitService
                .getUnitById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Unit", "id", id));
        return ResponseEntity.ok(UnitResponse.from(unit));
    }

    /**
     * Creates a new unit.
     *
     * POST /api/units
     *
     * @param request DTO containing unit data
     * @return created unit with HTTP 201 status
     */
    @Operation(
        summary = "Create a unit",
        description = "Creates a new unit and returns the created entity"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Unit created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PostMapping
    public ResponseEntity<UnitResponse> createUnit(@Valid @RequestBody CreateUnitRequest request) {
        Unit createdUnit = unitService.createUnit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UnitResponse.from(createdUnit));
    }

    /**
     * Updates an existing unit.
     *
     * PUT /api/units/{id}
     *
     * @param id unit identifier
     * @param request update data
     * @return updated unit with HTTP 200 status
     */
    @Operation(
        summary = "Update a unit",
        description = "Updates an existing unit. Only provided fields are updated."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Unit updated successfully"),
        @ApiResponse(responseCode = "404", description = "Unit not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PutMapping("/{id}")
    public ResponseEntity<UnitResponse> updateUnit(
            @Parameter(description = "Unit identifier", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody UpdateUnitRequest request) {
        Unit updatedUnit = unitService.updateUnit(id, request);
        return ResponseEntity.ok(UnitResponse.from(updatedUnit));
    }

    /**
     * Deletes a unit.
     *
     * DELETE /api/units/{id}
     *
     * @param id unit identifier
     * @return 204 No Content on success
     */
    @Operation(
        summary = "Delete a unit",
        description = "Permanently deletes a unit from the system"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Unit deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Unit not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUnit(
            @Parameter(description = "Unit identifier", example = "1")
            @PathVariable Long id) {
        unitService.deleteUnit(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Bulk import, step 1: parses and validates a CSV of units without
     * persisting anything.
     *
     * POST /api/units/import/preview (multipart, field name "file")
     */
    @Operation(
        summary = "Preview a bulk unit import",
        description = "Parses and validates a CSV file (columns: name, location, latitude, longitude) "
                + "without persisting anything, so the result can be reviewed before committing."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File parsed; see the per-row result"),
        @ApiResponse(responseCode = "400", description = "The file itself is unusable (unreadable, empty, missing a required column)")
    })
    @PostMapping("/import/preview")
    public ResponseEntity<ImportPreviewResponse<CreateUnitRequest>> previewImport(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(unitService.previewImport(file));
    }

    /**
     * Bulk import, step 2: re-parses and re-validates the same file, then
     * persists every non-error row.
     *
     * POST /api/units/import/commit (multipart, field name "file")
     */
    @Operation(
        summary = "Commit a bulk unit import",
        description = "Re-validates the CSV file and persists every row that isn't an error "
                + "(duplicate-name rows are only a warning, same as the single-record form)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Import committed; see the per-row result"),
        @ApiResponse(responseCode = "400", description = "The file itself is unusable (unreadable, empty, missing a required column)")
    })
    @PostMapping("/import/commit")
    public ResponseEntity<ImportPreviewResponse<CreateUnitRequest>> commitImport(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(unitService.commitImport(file));
    }
}
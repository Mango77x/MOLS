package com.mls.logistics.controller;

import com.mls.logistics.domain.Resource;
import com.mls.logistics.dto.request.CreateResourceRequest;
import com.mls.logistics.dto.request.UpdateResourceRequest;
import com.mls.logistics.dto.response.ResourceResponse;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.service.ResourceService;
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
 * REST controller exposing Resource-related API endpoints.
 *
 * This controller is responsible only for HTTP request/response handling.
 * All business logic is delegated to the ResourceService.
 */
@RestController
@RequestMapping("/api/resources")
@Tag(name = "Resources", description = "Operations for managing catalog resources")
public class ResourceController {

    private final ResourceService resourceService;

    /**
     * Constructor-based dependency injection.
     *
     * @param resourceService service layer for resource operations
     */
    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /** Fields clients may sort by on the list endpoint. */
    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "name", "type", "criticality");

    /**
     * Retrieves all resources, optionally paginated.
     *
     * GET /api/resources
     *
     * Without query parameters the full list is returned (original contract).
     * Passing any of page/size/sort switches the response to a
     * {@link PageResponse} envelope.
     *
     * @return list of resources, or a page of resources when paginated
     */
    @Operation(
        summary = "List all resources",
        description = "Returns all registered resources. Pass page/size/sort to receive a paginated envelope instead of the plain list."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resources retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid pagination or sort parameters")
    })
    @GetMapping
    public ResponseEntity<?> getAllResources(
            @Parameter(description = "Zero-based page index (enables pagination)", example = "0")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size, 1-100 (enables pagination)", example = "20")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort as 'field' or 'field,desc' (enables pagination)", example = "name,asc")
            @RequestParam(required = false) String sort) {
        if (page == null && size == null && sort == null) {
            List<ResourceResponse> resources = resourceService
                    .getAllResources()
                    .stream()
                    .map(ResourceResponse::from)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(resources);
        }
        Pageable pageable = PageQuery.toPageable(page, size, sort, SORTABLE_FIELDS, Sort.by("id"));
        return ResponseEntity.ok(PageResponse.from(
                resourceService.getAllResources(pageable), ResourceResponse::from));
    }

    /**
     * Retrieves a resource by its identifier.
     *
     * GET /api/resources/{id}
     *
     * @param id resource identifier
    * @return resource if found; otherwise ResourceNotFoundException is thrown and translated to 404
     */
    @Operation(
        summary = "Get resource by ID",
        description = "Returns a single resource by its unique identifier"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Resource retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Resource not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ResourceResponse> getResourceById(
            @Parameter(description = "Resource identifier", example = "1")
            @PathVariable Long id) {
        Resource resource = resourceService
                .getResourceById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Resource", "id", id));
        return ResponseEntity.ok(ResourceResponse.from(resource));
    }

    /**
     * Creates a new resource.
     *
     * POST /api/resources
     *
     * @param request DTO containing resource data
     * @return created resource with HTTP 201 status
     */
    @Operation(
        summary = "Create a resource",
        description = "Creates a new resource and returns the created entity"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Resource created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PostMapping
    public ResponseEntity<ResourceResponse> createResource(@Valid @RequestBody CreateResourceRequest request) {
        Resource createdResource = resourceService.createResource(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ResourceResponse.from(createdResource));
    }

    /**
     * Updates an existing resource.
     *
     * PUT /api/resources/{id}
     *
     * @param id resource identifier
     * @param request update data
     * @return updated resource with HTTP 200 status
     */
    @Operation(
        summary = "Update a resource",
        description = "Updates an existing resource. Only provided fields are updated."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resource updated successfully"),
        @ApiResponse(responseCode = "404", description = "Resource not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ResourceResponse> updateResource(
            @Parameter(description = "Resource identifier", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody UpdateResourceRequest request) {
        Resource updatedResource = resourceService.updateResource(id, request);
        return ResponseEntity.ok(ResourceResponse.from(updatedResource));
    }

    /**
     * Deletes a resource.
     *
     * DELETE /api/resources/{id}
     *
     * @param id resource identifier
     * @return 204 No Content on success
     */
    @Operation(
        summary = "Delete a resource",
        description = "Permanently deletes a resource from the system"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Resource deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Resource not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteResource(
            @Parameter(description = "Resource identifier", example = "1")
            @PathVariable Long id) {
        resourceService.deleteResource(id);
        return ResponseEntity.noContent().build();
    }
}
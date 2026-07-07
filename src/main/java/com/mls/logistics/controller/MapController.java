package com.mls.logistics.controller;

import com.mls.logistics.dto.response.MapResponse;
import com.mls.logistics.service.MapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the logistics map snapshot.
 *
 * This controller is responsible only for HTTP request/response handling.
 * All aggregation is delegated to the MapService.
 */
@RestController
@RequestMapping("/api/map")
@Tag(name = "Map", description = "Warehouse/unit pins and shipment routes for the logistics map")
public class MapController {

    private final MapService mapService;

    /**
     * Constructor-based dependency injection.
     *
     * @param mapService service assembling the map snapshot
     */
    public MapController(MapService mapService) {
        this.mapService = mapService;
    }

    /**
     * Retrieves the logistics map snapshot.
     *
     * GET /api/map
     *
     * @return warehouse pins, unit pins and shipment routes with coordinates
     */
    @Operation(
        summary = "Get the logistics map snapshot",
        description = "Returns warehouse pins (with stock status), unit pins and shipment routes, "
                + "all resolved with coordinates in a single call. Entries without coordinates are omitted."
    )
    @ApiResponse(responseCode = "200", description = "Map snapshot retrieved successfully")
    @GetMapping
    public ResponseEntity<MapResponse> getMap() {
        return ResponseEntity.ok(mapService.getMap());
    }
}

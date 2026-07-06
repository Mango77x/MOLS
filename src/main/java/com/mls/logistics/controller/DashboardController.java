package com.mls.logistics.controller;

import com.mls.logistics.dto.response.DashboardResponse;
import com.mls.logistics.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the aggregated operational dashboard.
 *
 * This controller is responsible only for HTTP request/response handling.
 * All aggregation is delegated to the DashboardService.
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Aggregated operational KPIs, charts and alerts")
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Constructor-based dependency injection.
     *
     * @param dashboardService service assembling the dashboard snapshot
     */
    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Retrieves the operational dashboard snapshot.
     *
     * GET /api/dashboard
     *
     * @return KPIs, chart series, alerts, recent movements and the thresholds used
     */
    @Operation(
        summary = "Get the operational dashboard",
        description = "Returns KPIs, chart series, actionable alerts, recent movements and the configured thresholds in a single call"
    )
    @ApiResponse(responseCode = "200", description = "Dashboard retrieved successfully")
    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboard());
    }
}

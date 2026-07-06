package com.mls.logistics.controller;

import com.mls.logistics.dto.response.DashboardResponse;
import com.mls.logistics.security.service.AppUserService;
import com.mls.logistics.security.service.JwtService;
import com.mls.logistics.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for DashboardController.
 *
 * Tests HTTP layer without requiring full application context.
 * Uses MockMvc to simulate HTTP requests.
 */
@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AppUserService appUserService;

    @Test
    @WithMockUser
    void getDashboard_ReturnsAggregatedSnapshot() throws Exception {
        // Given
        DashboardResponse response = new DashboardResponse(
                new DashboardResponse.Kpis(5, 2, 3, 0, 1, 120, 2, 1, 4, 60.0, 90.0, false),
                new DashboardResponse.Charts(
                        new DashboardResponse.ChartSeries(List.of("Central"), List.of(120L)),
                        new DashboardResponse.ChartSeries(List.of("ENTRY", "EXIT"), List.of(3L, 1L)),
                        new DashboardResponse.ChartSeries(
                                List.of("PENDING", "COMPLETED", "CANCELLED"), List.of(2L, 3L, 0L))),
                new DashboardResponse.Alerts(
                        List.of(new DashboardResponse.LowStockAlert(1L, "Ration", "Central", 3, true)),
                        List.of()),
                List.of(),
                new DashboardResponse.Thresholds(10, 5, 3, 24, 30));
        when(dashboardService.getDashboard()).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.totalOrders").value(5))
                .andExpect(jsonPath("$.kpis.fulfillmentTargetMet").value(false))
                .andExpect(jsonPath("$.charts.stockByWarehouse.labels", hasSize(1)))
                .andExpect(jsonPath("$.alerts.lowStock[0].critical").value(true))
                .andExpect(jsonPath("$.thresholds.lowStockThreshold").value(10));
    }
}

package com.mls.logistics.controller;

import com.mls.logistics.dto.response.MapResponse;
import com.mls.logistics.security.service.AppUserService;
import com.mls.logistics.security.service.JwtService;
import com.mls.logistics.service.MapService;
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
 * Integration tests for MapController.
 *
 * Tests HTTP layer without requiring full application context.
 * Uses MockMvc to simulate HTTP requests.
 */
@WebMvcTest(MapController.class)
class MapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MapService mapService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AppUserService appUserService;

    @Test
    @WithMockUser
    void getMap_ReturnsPinsAndRoutes() throws Exception {
        // Given
        MapResponse response = new MapResponse(
                List.of(new MapResponse.WarehousePin(1L, "Central", 40.4168, -3.7038, "CRITICAL")),
                List.of(new MapResponse.UnitPin(2L, "Alpha Unit", 37.3891, -5.9845)),
                List.of(new MapResponse.ShipmentRoute(
                        3L, "IN_TRANSIT",
                        new MapResponse.RoutePoint(1L, "Central", 40.4168, -3.7038),
                        new MapResponse.RoutePoint(2L, "Alpha Unit", 37.3891, -5.9845))));
        when(mapService.getMap()).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warehouses", hasSize(1)))
                .andExpect(jsonPath("$.warehouses[0].stockStatus").value("CRITICAL"))
                .andExpect(jsonPath("$.units[0].name").value("Alpha Unit"))
                .andExpect(jsonPath("$.shipments[0].status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.shipments[0].origin.name").value("Central"))
                .andExpect(jsonPath("$.shipments[0].destination.name").value("Alpha Unit"));
    }
}

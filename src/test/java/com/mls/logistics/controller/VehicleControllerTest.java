package com.mls.logistics.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mls.logistics.domain.Vehicle;
import com.mls.logistics.domain.VehicleStatus;
import com.mls.logistics.dto.request.CreateVehicleRequest;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.security.service.AppUserService;
import com.mls.logistics.security.service.JwtService;
import com.mls.logistics.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Integration tests for VehicleController.
 *
 * Tests HTTP layer without requiring full application context.
 * Uses MockMvc to simulate HTTP requests.
 */
@WebMvcTest(VehicleController.class)
class VehicleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private VehicleService vehicleService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AppUserService appUserService;

    private Vehicle testVehicle;

    @BeforeEach
    void setUp() {
        testVehicle = new Vehicle();
        testVehicle.setId(1L);
        testVehicle.setType("Truck");
        testVehicle.setCapacity(1000);
        testVehicle.setStatus(VehicleStatus.AVAILABLE);
    }

    @Test
    @WithMockUser
    void getAllVehicles_ShouldReturnVehiclesList() throws Exception {
        // Given
        when(vehicleService.getAllVehicles()).thenReturn(Arrays.asList(testVehicle));

        // When & Then
        mockMvc.perform(get("/api/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("Truck"));

        verify(vehicleService, times(1)).getAllVehicles();
    }

    @Test
    @WithMockUser
    void getVehicleById_WhenExists_ShouldReturnVehicle() throws Exception {
        // Given
        when(vehicleService.getVehicleById(1L)).thenReturn(Optional.of(testVehicle));

        // When & Then
        mockMvc.perform(get("/api/vehicles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("Truck"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        verify(vehicleService, times(1)).getVehicleById(1L);
    }

    @Test
    @WithMockUser
    void getVehicleById_WhenNotExists_ShouldReturn404() throws Exception {
        // Given
        when(vehicleService.getVehicleById(999L)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/vehicles/999"))
                .andExpect(status().isNotFound());

        verify(vehicleService, times(1)).getVehicleById(999L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createVehicle_WithValidRequest_ShouldReturn201() throws Exception {
        // Given
        CreateVehicleRequest request = new CreateVehicleRequest("Truck", 1000, "AVAILABLE");
        when(vehicleService.createVehicle(any(CreateVehicleRequest.class))).thenReturn(testVehicle);

        // When & Then
        mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("Truck"));

        verify(vehicleService, times(1)).createVehicle(any(CreateVehicleRequest.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createVehicle_WithInvalidRequest_ShouldReturn400() throws Exception {
        // Given
        CreateVehicleRequest request = new CreateVehicleRequest("", 0, "");

        // When & Then
        mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(vehicleService, never()).createVehicle(any(CreateVehicleRequest.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteVehicle_WhenExists_ShouldReturn204() throws Exception {
        // Given
        doNothing().when(vehicleService).deleteVehicle(1L);

        // When & Then
        mockMvc.perform(delete("/api/vehicles/1"))
                .andExpect(status().isNoContent());

        verify(vehicleService, times(1)).deleteVehicle(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteVehicle_WhenNotExists_ShouldReturn404() throws Exception {
        // Given
        doThrow(new ResourceNotFoundException("Vehicle", "id", 999L))
                .when(vehicleService).deleteVehicle(999L);

        // When & Then
        mockMvc.perform(delete("/api/vehicles/999"))
                .andExpect(status().isNotFound());

        verify(vehicleService, times(1)).deleteVehicle(999L);
    }
}

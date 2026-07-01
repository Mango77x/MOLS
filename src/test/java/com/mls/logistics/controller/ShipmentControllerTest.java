package com.mls.logistics.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mls.logistics.domain.Order;
import com.mls.logistics.domain.Shipment;
import com.mls.logistics.domain.ShipmentStatus;
import com.mls.logistics.domain.Vehicle;
import com.mls.logistics.domain.Warehouse;
import com.mls.logistics.dto.request.CreateShipmentRequest;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.security.service.AppUserService;
import com.mls.logistics.security.service.JwtService;
import com.mls.logistics.service.ShipmentService;
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
 * Integration tests for ShipmentController.
 *
 * Tests HTTP layer without requiring full application context.
 * Uses MockMvc to simulate HTTP requests.
 */
@WebMvcTest(ShipmentController.class)
class ShipmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private ShipmentService shipmentService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AppUserService appUserService;

    private Shipment testShipment;

    @BeforeEach
    void setUp() {
        Order order = new Order();
        order.setId(1L);

        Vehicle vehicle = new Vehicle();
        vehicle.setId(1L);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(1L);

        testShipment = new Shipment();
        testShipment.setId(1L);
        testShipment.setOrder(order);
        testShipment.setVehicle(vehicle);
        testShipment.setWarehouse(warehouse);
        testShipment.setStatus(ShipmentStatus.PLANNED);
    }

    @Test
    @WithMockUser
    void getAllShipments_ShouldReturnShipmentsList() throws Exception {
        // Given
        when(shipmentService.getAllShipments()).thenReturn(Arrays.asList(testShipment));

        // When & Then
        mockMvc.perform(get("/api/shipments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("PLANNED"));

        verify(shipmentService, times(1)).getAllShipments();
    }

    @Test
    @WithMockUser
    void getShipmentById_WhenExists_ShouldReturnShipment() throws Exception {
        // Given
        when(shipmentService.getShipmentById(1L)).thenReturn(Optional.of(testShipment));

        // When & Then
        mockMvc.perform(get("/api/shipments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLANNED"));

        verify(shipmentService, times(1)).getShipmentById(1L);
    }

    @Test
    @WithMockUser
    void getShipmentById_WhenNotExists_ShouldReturn404() throws Exception {
        // Given
        when(shipmentService.getShipmentById(999L)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/shipments/999"))
                .andExpect(status().isNotFound());

        verify(shipmentService, times(1)).getShipmentById(999L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createShipment_WithValidRequest_ShouldReturn201() throws Exception {
        // Given
        CreateShipmentRequest request = new CreateShipmentRequest(1L, 1L, 1L, "PLANNED");
        when(shipmentService.createShipment(any(CreateShipmentRequest.class))).thenReturn(testShipment);

        // When & Then
        mockMvc.perform(post("/api/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED"));

        verify(shipmentService, times(1)).createShipment(any(CreateShipmentRequest.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createShipment_WithInvalidRequest_ShouldReturn400() throws Exception {
        // Given
        CreateShipmentRequest request = new CreateShipmentRequest(null, null, null, "");

        // When & Then
        mockMvc.perform(post("/api/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(shipmentService, never()).createShipment(any(CreateShipmentRequest.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteShipment_WhenExists_ShouldReturn204() throws Exception {
        // Given
        doNothing().when(shipmentService).deleteShipment(1L);

        // When & Then
        mockMvc.perform(delete("/api/shipments/1"))
                .andExpect(status().isNoContent());

        verify(shipmentService, times(1)).deleteShipment(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteShipment_WhenNotExists_ShouldReturn404() throws Exception {
        // Given
        doThrow(new ResourceNotFoundException("Shipment", "id", 999L))
                .when(shipmentService).deleteShipment(999L);

        // When & Then
        mockMvc.perform(delete("/api/shipments/999"))
                .andExpect(status().isNotFound());

        verify(shipmentService, times(1)).deleteShipment(999L);
    }
}

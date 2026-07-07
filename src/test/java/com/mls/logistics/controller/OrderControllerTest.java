package com.mls.logistics.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mls.logistics.domain.Order;
import com.mls.logistics.domain.OrderStatus;
import com.mls.logistics.domain.Unit;
import com.mls.logistics.domain.Warehouse;
import com.mls.logistics.dto.request.CreateOrderRequest;
import com.mls.logistics.dto.request.CreateOrderWithItemsRequest;
import com.mls.logistics.dto.request.OrderItemLineRequest;
import com.mls.logistics.exception.InsufficientStockException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.security.service.AppUserService;
import com.mls.logistics.security.service.JwtService;
import com.mls.logistics.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Integration tests for OrderController.
 *
 * Tests HTTP layer without requiring full application context.
 * Uses MockMvc to simulate HTTP requests.
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AppUserService appUserService;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        Unit unit = new Unit();
        unit.setId(1L);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(1L);

        testOrder = new Order();
        testOrder.setId(1L);
        testOrder.setUnit(unit);
        testOrder.setWarehouse(warehouse);
        testOrder.setDateCreated(LocalDate.of(2024, 1, 1));
        testOrder.setStatus(OrderStatus.CREATED);
    }

    @Test
    @WithMockUser
    void getAllOrders_ShouldReturnOrdersList() throws Exception {
        // Given
        when(orderService.getAllOrders()).thenReturn(Arrays.asList(testOrder));

        // When & Then
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("CREATED"));

        verify(orderService, times(1)).getAllOrders();
    }

    @Test
    @WithMockUser
    void getOrderById_WhenExists_ShouldReturnOrder() throws Exception {
        // Given
        when(orderService.getOrderById(1L)).thenReturn(Optional.of(testOrder));

        // When & Then
        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"));

        verify(orderService, times(1)).getOrderById(1L);
    }

    @Test
    @WithMockUser
    void getOrderById_WhenNotExists_ShouldReturn404() throws Exception {
        // Given
        when(orderService.getOrderById(999L)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/orders/999"))
                .andExpect(status().isNotFound());

        verify(orderService, times(1)).getOrderById(999L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createOrder_WithValidRequest_ShouldReturn201() throws Exception {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(1L, 1L, LocalDate.now(), "CREATED");
        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(testOrder);

        // When & Then
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"));

        verify(orderService, times(1)).createOrder(any(CreateOrderRequest.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createOrder_WithInvalidRequest_ShouldReturn400() throws Exception {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(null, null, null, "");

        // When & Then
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).createOrder(any(CreateOrderRequest.class));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void createOrderWithItems_WithValidRequest_ShouldReturn201() throws Exception {
        // Given
        CreateOrderWithItemsRequest request = new CreateOrderWithItemsRequest(
                new CreateOrderRequest(1L, 1L, LocalDate.now(), "CREATED"),
                Arrays.asList(new OrderItemLineRequest(2L, 5)));
        when(orderService.createOrderWithItems(any(CreateOrderRequest.class), anyList()))
                .thenReturn(testOrder);

        // When & Then
        mockMvc.perform(post("/api/orders/with-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"));

        verify(orderService, times(1)).createOrderWithItems(any(CreateOrderRequest.class), anyList());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void createOrderWithItems_WithMissingHeader_ShouldReturn400() throws Exception {
        // Given
        CreateOrderWithItemsRequest request = new CreateOrderWithItemsRequest(null, List.of());

        // When & Then
        mockMvc.perform(post("/api/orders/with-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).createOrderWithItems(any(), anyList());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void createOrderWithItems_WhenStockInsufficient_ShouldReturn409() throws Exception {
        // Given
        CreateOrderWithItemsRequest request = new CreateOrderWithItemsRequest(
                new CreateOrderRequest(1L, 1L, LocalDate.now(), "CREATED"),
                Arrays.asList(new OrderItemLineRequest(2L, 999)));
        when(orderService.createOrderWithItems(any(CreateOrderRequest.class), anyList()))
                .thenThrow(new InsufficientStockException("Not enough stock available for resource 2"));

        // When & Then
        mockMvc.perform(post("/api/orders/with-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteOrder_WhenExists_ShouldReturn204() throws Exception {
        // Given
        doNothing().when(orderService).deleteOrder(1L);

        // When & Then
        mockMvc.perform(delete("/api/orders/1"))
                .andExpect(status().isNoContent());

        verify(orderService, times(1)).deleteOrder(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteOrder_WhenNotExists_ShouldReturn404() throws Exception {
        // Given
        doThrow(new ResourceNotFoundException("Order", "id", 999L))
                .when(orderService).deleteOrder(999L);

        // When & Then
        mockMvc.perform(delete("/api/orders/999"))
                .andExpect(status().isNotFound());

        verify(orderService, times(1)).deleteOrder(999L);
    }
}

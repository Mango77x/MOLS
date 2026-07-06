package com.mls.logistics.controller;

import com.mls.logistics.domain.Movement;
import com.mls.logistics.domain.MovementType;
import com.mls.logistics.domain.Stock;
import com.mls.logistics.security.service.AppUserService;
import com.mls.logistics.security.service.JwtService;
import com.mls.logistics.service.MovementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Integration tests for MovementController.
 *
 * Tests HTTP layer without requiring full application context.
 * Uses MockMvc to simulate HTTP requests.
 *
 * The movement audit trail is append-only and system-generated, so the API
 * is read-only: these tests also pin down that write methods are NOT exposed.
 */
@WebMvcTest(MovementController.class)
class MovementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MovementService movementService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AppUserService appUserService;

    private Movement testMovement;

    @BeforeEach
    void setUp() {
        Stock stock = new Stock();
        stock.setId(1L);

        testMovement = new Movement();
        testMovement.setId(1L);
        testMovement.setStock(stock);
        testMovement.setType(MovementType.ENTRY);
        testMovement.setQuantity(5);
        testMovement.setDateTime(LocalDateTime.of(2024, 1, 1, 10, 0));
    }

    @Test
    @WithMockUser
    void getAllMovements_ShouldReturnMovementsList() throws Exception {
        // Given
        when(movementService.getAllMovements()).thenReturn(Arrays.asList(testMovement));

        // When & Then
        mockMvc.perform(get("/api/movements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("ENTRY"))
                .andExpect(jsonPath("$[0].quantity").value(5));

        verify(movementService, times(1)).getAllMovements();
    }

    @Test
    @WithMockUser
    void getAllMovements_WithPagination_DefaultsToNewestFirst() throws Exception {
        // Given
        when(movementService.getAllMovements(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testMovement)));

        // When & Then — enabling pagination without a sort orders by dateTime desc
        mockMvc.perform(get("/api/movements").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].type").value("ENTRY"));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(movementService).getAllMovements(pageable.capture());
        Sort.Order order = pageable.getValue().getSort().getOrderFor("dateTime");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @WithMockUser
    void getMovementById_WhenExists_ShouldReturnMovement() throws Exception {
        // Given
        when(movementService.getMovementById(1L)).thenReturn(Optional.of(testMovement));

        // When & Then
        mockMvc.perform(get("/api/movements/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ENTRY"))
                .andExpect(jsonPath("$.quantity").value(5));

        verify(movementService, times(1)).getMovementById(1L);
    }

    @Test
    @WithMockUser
    void getMovementById_WhenNotExists_ShouldReturn404() throws Exception {
        // Given
        when(movementService.getMovementById(999L)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/movements/999"))
                .andExpect(status().isNotFound());

        verify(movementService, times(1)).getMovementById(999L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createMovement_IsNotExposed_EvenForAdmin() throws Exception {
        // The audit trail is append-only and system-generated: POST must not exist.
        mockMvc.perform(post("/api/movements")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stockId\":1,\"type\":\"ENTRY\",\"quantity\":5}"))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(movementService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateMovement_IsNotExposed_EvenForAdmin() throws Exception {
        // Audit records must never be rewritten: PUT must not exist.
        mockMvc.perform(put("/api/movements/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":99}"))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(movementService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteMovement_IsNotExposed_EvenForAdmin() throws Exception {
        // Audit records must never be erased: DELETE must not exist.
        mockMvc.perform(delete("/api/movements/1").with(csrf()))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(movementService);
    }
}

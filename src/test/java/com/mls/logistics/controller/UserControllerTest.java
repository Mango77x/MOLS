package com.mls.logistics.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.security.domain.AppUser;
import com.mls.logistics.security.domain.Role;
import com.mls.logistics.security.service.AppUserAdminService;
import com.mls.logistics.security.service.AppUserService;
import com.mls.logistics.security.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for UserController: HTTP layer only, business logic mocked.
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private AppUserAdminService appUserAdminService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AppUserService appUserService;

    private AppUser testUser;

    @BeforeEach
    void setUp() {
        testUser = new AppUser("alice", "hashed", Role.OPERATOR);
        testUser.setId(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllUsers_ShouldReturnUsersList() throws Exception {
        when(appUserAdminService.getAllUsers(any())).thenReturn(Arrays.asList(testUser));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].role").value("OPERATOR"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUserById_WhenExists_ShouldReturnUser() throws Exception {
        when(appUserAdminService.getUserById(1L)).thenReturn(java.util.Optional.of(testUser));

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUserById_WhenNotExists_ShouldReturn404() throws Exception {
        when(appUserAdminService.getUserById(999L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/users/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createUser_WithValidRequest_ShouldReturn201() throws Exception {
        when(appUserAdminService.createUser(eq("bob"), eq("a-long-enough-password"), eq(Role.AUDITOR), isNull()))
                .thenReturn(new AppUser("bob", "hashed", Role.AUDITOR));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"a-long-enough-password\",\"role\":\"AUDITOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.role").value("AUDITOR"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createUser_WithShortPassword_ShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"short\",\"role\":\"AUDITOR\"}"))
                .andExpect(status().isBadRequest());

        verify(appUserAdminService, never()).createUser(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createUser_WithDuplicateUsername_ShouldReturn400() throws Exception {
        when(appUserAdminService.createUser(any(), any(), any(), any()))
                .thenThrow(new InvalidRequestException("Username already exists."));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"a-long-enough-password\",\"role\":\"AUDITOR\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRole_WhenLastAdmin_ShouldReturn400() throws Exception {
        when(appUserAdminService.updateRole(eq(1L), eq(Role.OPERATOR)))
                .thenThrow(new InvalidRequestException("You can't remove the last enabled ADMIN user."));

        mockMvc.perform(patch("/api/users/1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"OPERATOR\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRole_WhenNotFound_ShouldReturn404() throws Exception {
        when(appUserAdminService.updateRole(eq(999L), any()))
                .thenThrow(new ResourceNotFoundException("User", "id", 999L));

        mockMvc.perform(patch("/api/users/999/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"OPERATOR\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void resetPassword_WithValidRequest_ShouldReturn200() throws Exception {
        when(appUserAdminService.resetPassword(eq(1L), eq("a-new-long-password"))).thenReturn(testUser);

        mockMvc.perform(patch("/api/users/1/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"a-new-long-password\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setEnabled_WhenLastAdmin_ShouldReturn400() throws Exception {
        when(appUserAdminService.setEnabled(eq(1L), eq(false)))
                .thenThrow(new InvalidRequestException("You can't disable the last enabled ADMIN user."));

        mockMvc.perform(patch("/api/users/1/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setEnabled_WithValidRequest_ShouldReturn200() throws Exception {
        when(appUserAdminService.setEnabled(eq(1L), eq(true))).thenReturn(testUser);

        mockMvc.perform(patch("/api/users/1/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateEmail_WithValidRequest_ShouldReturn200() throws Exception {
        testUser.setEmail("alice@example.com");
        when(appUserAdminService.updateEmail(eq(1L), eq("alice@example.com"))).thenReturn(testUser);

        mockMvc.perform(patch("/api/users/1/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateEmail_AlreadyUsed_ShouldReturn400() throws Exception {
        when(appUserAdminService.updateEmail(eq(1L), eq("taken@example.com")))
                .thenThrow(new InvalidRequestException("A user with this email already exists."));

        mockMvc.perform(patch("/api/users/1/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"taken@example.com\"}"))
                .andExpect(status().isBadRequest());
    }
}

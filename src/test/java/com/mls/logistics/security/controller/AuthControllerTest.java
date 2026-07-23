package com.mls.logistics.security.controller;

import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.security.config.JwtProperties;
import com.mls.logistics.security.domain.AppUser;
import com.mls.logistics.security.domain.Role;
import com.mls.logistics.security.repository.AppUserRepository;
import com.mls.logistics.security.service.AppUserAdminService;
import com.mls.logistics.security.service.AppUserService;
import com.mls.logistics.security.service.JwtService;
import com.mls.logistics.service.NotificationMailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the Sprint 19 self-service password-reset endpoints on
 * AuthController: HTTP layer only, business logic mocked. Both endpoints are
 * public (permitAll in SecurityConfig) so none of these need @WithMockUser.
 */
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppUserRepository appUserRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtProperties jwtProperties;

    @MockitoBean
    private AppUserAdminService appUserAdminService;

    @MockitoBean
    private NotificationMailService notificationMailService;

    @MockitoBean
    private AppUserService appUserService;

    @Test
    void forgotPassword_WhenEmailMatchesAnAccount_SendsResetEmailAndReturns200() throws Exception {
        AppUser user = new AppUser("alice", "hash", Role.OPERATOR);
        user.setId(1L);
        user.setEmail("alice@example.com");
        when(appUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generatePasswordResetToken(user)).thenReturn("reset-token");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\"}"))
                .andExpect(status().isOk());

        verify(notificationMailService).sendPasswordResetEmail(user, "reset-token");
    }

    @Test
    void forgotPassword_WhenEmailMatchesNoAccount_StillReturns200_NoEmailSent() throws Exception {
        // The whole point: never reveal whether the email has an account.
        when(appUserRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isOk());

        verify(notificationMailService, never()).sendPasswordResetEmail(any(), anyString());
        verify(jwtService, never()).generatePasswordResetToken(any());
    }

    @Test
    void forgotPassword_WithMalformedEmail_ReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());

        verify(appUserRepository, never()).findByEmail(anyString());
    }

    @Test
    void resetPasswordWithToken_ValidToken_ResetsPasswordAndReturns200() throws Exception {
        AppUser user = new AppUser("alice", "hash", Role.OPERATOR);
        user.setId(1L);
        when(jwtService.extractUsername("valid-token")).thenReturn("alice");
        when(appUserRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtService.isPasswordResetTokenValid("valid-token", user)).thenReturn(true);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"valid-token\",\"newPassword\":\"a-long-enough-password\"}"))
                .andExpect(status().isOk());

        verify(appUserAdminService).resetPassword(1L, "a-long-enough-password");
    }

    @Test
    void resetPasswordWithToken_ExpiredOrAlreadyUsedToken_Returns400_DoesNotResetPassword() throws Exception {
        AppUser user = new AppUser("alice", "hash", Role.OPERATOR);
        user.setId(1L);
        when(jwtService.extractUsername("stale-token")).thenReturn("alice");
        when(appUserRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtService.isPasswordResetTokenValid("stale-token", user)).thenReturn(false);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"stale-token\",\"newPassword\":\"a-long-enough-password\"}"))
                .andExpect(status().isBadRequest());

        verify(appUserAdminService, never()).resetPassword(any(), anyString());
    }

    @Test
    void resetPasswordWithToken_MalformedToken_Returns400() throws Exception {
        when(jwtService.extractUsername("garbage")).thenThrow(new io.jsonwebtoken.MalformedJwtException("bad token"));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"garbage\",\"newPassword\":\"a-long-enough-password\"}"))
                .andExpect(status().isBadRequest());

        verify(appUserAdminService, never()).resetPassword(any(), anyString());
    }

    @Test
    void resetPasswordWithToken_UnknownUser_Returns400() throws Exception {
        when(jwtService.extractUsername("valid-token")).thenReturn("ghost");
        when(appUserRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"valid-token\",\"newPassword\":\"a-long-enough-password\"}"))
                .andExpect(status().isBadRequest());

        verify(appUserAdminService, never()).resetPassword(any(), anyString());
    }

    @Test
    void resetPasswordWithToken_ShortPassword_ReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"valid-token\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());

        verify(jwtService, never()).extractUsername(anyString());
    }
}

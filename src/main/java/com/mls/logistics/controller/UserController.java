package com.mls.logistics.controller;

import com.mls.logistics.dto.request.CreateUserRequest;
import com.mls.logistics.dto.request.ResetPasswordRequest;
import com.mls.logistics.dto.request.SetEnabledRequest;
import com.mls.logistics.dto.request.UpdateEmailRequest;
import com.mls.logistics.dto.request.UpdateRoleRequest;
import com.mls.logistics.dto.response.UserResponse;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.security.domain.AppUser;
import com.mls.logistics.security.domain.Role;
import com.mls.logistics.security.service.AppUserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller exposing admin-only application-user management endpoints.
 *
 * This controller is responsible only for HTTP request/response handling.
 * All business logic (including the "can't touch the last enabled ADMIN"
 * rules) is delegated to {@link AppUserAdminService}. Access is restricted to
 * ADMIN by {@code SecurityConfig} — including reads, unlike every other
 * {@code /api/**} resource — since user accounts are sensitive.
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Admin-only application user management")
public class UserController {

    private final AppUserAdminService appUserAdminService;

    public UserController(AppUserAdminService appUserAdminService) {
        this.appUserAdminService = appUserAdminService;
    }

    /**
     * Retrieves all application users, sorted by id.
     *
     * GET /api/users
     */
    @Operation(
        summary = "List all application users",
        description = "Returns every application user (ADMIN-only)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Users retrieved successfully")
    })
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = appUserAdminService
                .getAllUsers(Sort.by("id"))
                .stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    /**
     * Retrieves a single application user by id.
     *
     * GET /api/users/{id}
     */
    @Operation(
        summary = "Get user by ID",
        description = "Returns a single application user by its unique identifier (ADMIN-only)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(
            @Parameter(description = "User identifier", example = "1")
            @PathVariable Long id) {
        AppUser user = appUserAdminService.getUserById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return ResponseEntity.ok(UserResponse.from(user));
    }

    /**
     * Creates a new application user.
     *
     * POST /api/users
     */
    @Operation(
        summary = "Create a user",
        description = "Creates a new application user and returns the created entity"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data or duplicate username")
    })
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        AppUser created = appUserAdminService.createUser(
                request.getUsername(), request.getPassword(), Role.from(request.getRole()), request.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(created));
    }

    /**
     * Changes a user's role.
     *
     * PATCH /api/users/{id}/role
     */
    @Operation(
        summary = "Change a user's role",
        description = "Updates a user's role. Rejected if it would remove the last enabled ADMIN."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Role updated successfully"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request, or would remove the last enabled ADMIN")
    })
    @PatchMapping("/{id}/role")
    public ResponseEntity<UserResponse> updateRole(
            @Parameter(description = "User identifier", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest request) {
        AppUser updated = appUserAdminService.updateRole(id, Role.from(request.getRole()));
        return ResponseEntity.ok(UserResponse.from(updated));
    }

    /**
     * Resets a user's password.
     *
     * PATCH /api/users/{id}/password
     */
    @Operation(
        summary = "Reset a user's password",
        description = "Sets a new password for the given user."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Password reset successfully"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PatchMapping("/{id}/password")
    public ResponseEntity<UserResponse> resetPassword(
            @Parameter(description = "User identifier", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request) {
        AppUser updated = appUserAdminService.resetPassword(id, request.getPassword());
        return ResponseEntity.ok(UserResponse.from(updated));
    }

    /**
     * Enables or disables a user account.
     *
     * PATCH /api/users/{id}/enabled
     */
    @Operation(
        summary = "Enable or disable a user",
        description = "Toggles account access. Rejected if it would disable the last enabled ADMIN."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account status updated successfully"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request, or would disable the last enabled ADMIN")
    })
    @PatchMapping("/{id}/enabled")
    public ResponseEntity<UserResponse> setEnabled(
            @Parameter(description = "User identifier", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody SetEnabledRequest request) {
        AppUser updated = appUserAdminService.setEnabled(id, request.getEnabled());
        return ResponseEntity.ok(UserResponse.from(updated));
    }

    /**
     * Sets or clears a user's email — enables them for the low-stock/stale-order
     * digest job and the self-service password-reset flow (Sprint 19).
     *
     * PATCH /api/users/{id}/email
     */
    @Operation(
        summary = "Set a user's email",
        description = "Sets or clears (blank) a user's email address."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Email updated successfully"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "400", description = "Invalid email, or already used by another user")
    })
    @PatchMapping("/{id}/email")
    public ResponseEntity<UserResponse> updateEmail(
            @Parameter(description = "User identifier", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody UpdateEmailRequest request) {
        AppUser updated = appUserAdminService.updateEmail(id, request.getEmail());
        return ResponseEntity.ok(UserResponse.from(updated));
    }
}

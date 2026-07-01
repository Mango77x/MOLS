package com.mls.logistics.security.controller;

import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.security.domain.AppUser;
import com.mls.logistics.security.dto.AuthResponse;
import com.mls.logistics.security.dto.LoginRequest;
import com.mls.logistics.security.dto.RegisterRequest;
import com.mls.logistics.security.repository.AppUserRepository;
import com.mls.logistics.security.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication operations.
 *
 * Provides public endpoints for user registration and login.
 * Returns JWT tokens on successful authentication.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "User registration and login")
public class AuthController {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AppUserRepository appUserRepository,
                          PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager,
                          JwtService jwtService) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Operation(summary = "Register a new user",
               description = "Creates a new user account and returns a JWT token")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User registered successfully"),
        @ApiResponse(responseCode = "400", description = "Username already taken or invalid data")
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        if (appUserRepository.existsByUsername(request.getUsername())) {
            throw new InvalidRequestException(
                "Username '" + request.getUsername() + "' is already taken");
        }

        AppUser user = new AppUser(
                request.getUsername(),
                passwordEncoder.encode(request.getPassword()),
                request.getRole()
        );

        appUserRepository.save(user);

        String token = jwtService.generateToken(user);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new AuthResponse(token, user.getUsername(),
                        user.getRole().name()));
    }

    @Operation(summary = "Login",
               description = "Authenticates user credentials and returns a JWT token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()));

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String token = jwtService.generateToken(userDetails);

            AppUser user = appUserRepository
                    .findByUsername(userDetails.getUsername())
                    .orElseThrow();

            return ResponseEntity.ok(
                    new AuthResponse(token, user.getUsername(),
                            user.getRole().name()));

        } catch (AuthenticationException e) {
            // Covers bad credentials, disabled accounts, and temporary
            // lockouts. A single generic 401 avoids leaking which case it was.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
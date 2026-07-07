package com.mls.logistics.security.controller;

import com.mls.logistics.dto.response.UserResponse;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.security.config.JwtProperties;
import com.mls.logistics.security.domain.AppUser;
import com.mls.logistics.security.domain.Role;
import com.mls.logistics.security.dto.AuthResponse;
import com.mls.logistics.security.dto.LoginRequest;
import com.mls.logistics.security.dto.MeResponse;
import com.mls.logistics.security.dto.RegisterRequest;
import com.mls.logistics.security.dto.SetupRequest;
import com.mls.logistics.security.dto.SetupStatusResponse;
import com.mls.logistics.security.repository.AppUserRepository;
import com.mls.logistics.security.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
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
    private final JwtProperties jwtProperties;

    public AuthController(AppUserRepository appUserRepository,
                          PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          JwtProperties jwtProperties) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Operation(summary = "First-run setup status",
               description = "Reports whether the application still needs its first-run setup "
                       + "(no application users exist yet). Public — the SPA calls this before "
                       + "showing the login page to decide whether to redirect to /setup instead.")
    @ApiResponse(responseCode = "200", description = "Setup status returned")
    @GetMapping("/setup-status")
    public ResponseEntity<SetupStatusResponse> setupStatus() {
        return ResponseEntity.ok(new SetupStatusResponse(appUserRepository.count() == 0));
    }

    @Operation(summary = "First-run setup",
               description = "Creates the very first ADMIN user. Only works while the database "
                       + "has zero application users — rejected once any user exists. Public, "
                       + "since by definition no one can be authenticated yet at that point.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "First ADMIN user created successfully"),
        @ApiResponse(responseCode = "400", description = "Setup already completed, or invalid data")
    })
    @PostMapping("/setup")
    public ResponseEntity<UserResponse> setup(@Valid @RequestBody SetupRequest request) {
        if (appUserRepository.count() > 0) {
            throw new InvalidRequestException("Setup has already been completed.");
        }

        String normalizedUsername = request.getUsername().trim();
        if (appUserRepository.existsByUsername(normalizedUsername)) {
            throw new InvalidRequestException("Username already exists.");
        }

        AppUser user = new AppUser(
                normalizedUsername,
                passwordEncoder.encode(request.getPassword()),
                Role.ADMIN);
        appUserRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
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
               description = "Authenticates user credentials and returns a JWT token. "
                       + "The token is also set as an HttpOnly SameSite=Strict cookie "
                       + "for browser clients, which never need to store it in script-accessible storage.")
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

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, authCookie(token,
                            Duration.ofMillis(jwtProperties.getExpirationMs())).toString())
                    .body(new AuthResponse(token, user.getUsername(),
                            user.getRole().name()));

        } catch (AuthenticationException e) {
            // Covers bad credentials, disabled accounts, and temporary
            // lockouts. A single generic 401 avoids leaking which case it was.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @Operation(summary = "Current session",
               description = "Returns the authenticated user's name and role. Browser "
                       + "clients use it to restore their session on page load, since the "
                       + "JWT lives in an HttpOnly cookie they cannot read.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Session information returned"),
        @ApiResponse(responseCode = "403", description = "Not authenticated")
    })
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse(null);
        return ResponseEntity.ok(new MeResponse(authentication.getName(), role));
    }

    @Operation(summary = "Logout",
               description = "Clears the HttpOnly auth cookie. Clients using the "
                       + "Authorization header simply discard their token.")
    @ApiResponse(responseCode = "204", description = "Auth cookie cleared")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authCookie("", Duration.ZERO).toString())
                .build();
    }

    /**
     * Builds the auth cookie: HttpOnly (no script access), SameSite=Strict
     * (not sent on cross-site requests, the CSRF mitigation for the stateless
     * API), scoped to /api, and Secure outside plain-HTTP local development.
     */
    private ResponseCookie authCookie(String token, Duration maxAge) {
        return ResponseCookie.from(JwtProperties.AUTH_COOKIE, token)
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .sameSite("Strict")
                .path("/api")
                .maxAge(maxAge)
                .build();
    }
}
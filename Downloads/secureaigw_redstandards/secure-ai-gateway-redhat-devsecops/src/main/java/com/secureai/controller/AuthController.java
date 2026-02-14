package com.secureai.controller;

import com.secureai.dto.LoginRequest;
import com.secureai.dto.LoginResponse;
import com.secureai.exception.AuthenticationException;
import com.secureai.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller for user login and JWT token generation.
 * Implements secure authentication flow with proper validation.
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and token management endpoints")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Value("${spring.security.user.name:admin}")
    private String adminUsername;

    @Value("${spring.security.user.password}")
    private String adminPassword;

    @Value("${jwt.expiration:3600000}")
    private long jwtExpiration;

    /**
     * Authenticate user and generate JWT token.
     *
     * @param request the login request containing username and password
     * @return ResponseEntity with JWT token or error
     */
    @PostMapping("/login")
    @Operation(summary = "Authenticate user", description = "Authenticate with username and password to receive a JWT token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully authenticated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Authentication failed")
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for user: {}", request.getUsername());

        try {
            // Validate credentials
            // NOTE: In production, use a proper UserDetailsService with database-backed users
            if (!adminUsername.equals(request.getUsername())) {
                log.warn("Login failed: Invalid username: {}", request.getUsername());
                throw new AuthenticationException("Invalid credentials");
            }

            // For production: Use passwordEncoder.matches(request.getPassword(), hashedPassword)
            // This is a simplified version - in production, passwords should be hashed
            if (!adminPassword.equals(request.getPassword())) {
                log.warn("Login failed: Invalid password for user: {}", request.getUsername());
                throw new AuthenticationException("Invalid credentials");
            }

            // Generate JWT token
            String token = jwtUtil.generateToken(request.getUsername());

            LoginResponse response = LoginResponse.builder()
                    .token(token)
                    .tokenType("Bearer")
                    .expiresIn(jwtExpiration / 1000) // Convert to seconds
                    .build();

            log.info("Login successful for user: {}", request.getUsername());
            return ResponseEntity.ok(response);

        } catch (AuthenticationException e) {
            // Re-throw authentication exceptions
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during login", e);
            throw new AuthenticationException("Authentication failed");
        }
    }

    /**
     * Validate a JWT token.
     *
     * @param token the JWT token to validate
     * @return ResponseEntity with validation result
     */
    @PostMapping("/validate")
    @Operation(summary = "Validate JWT token", description = "Validate a JWT token and return its status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token is valid"),
            @ApiResponse(responseCode = "401", description = "Token is invalid")
    })
    public ResponseEntity<Void> validateToken(@RequestHeader("Authorization") String token) {
        try {
            if (token != null && token.startsWith("Bearer ")) {
                String jwtToken = token.substring(7);
                if (jwtUtil.validateToken(jwtToken)) {
                    return ResponseEntity.ok().build();
                }
            }
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            log.error("Token validation failed", e);
            return ResponseEntity.status(401).build();
        }
    }
}

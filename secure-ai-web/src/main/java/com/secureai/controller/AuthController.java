package com.secureai.controller;

import com.secureai.model.LoginRequest;
import com.secureai.model.LoginResponse;
import com.secureai.model.RegisterRequest;
import com.secureai.model.User;
import com.secureai.security.JwtUtil;
import com.secureai.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "JWT-based auth endpoints")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    public AuthController(AuthService authService, JwtUtil jwtUtil) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive a JWT token")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for user '{}'", request.getUsername());
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user account")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration attempt for username '{}'", request.getUsername());
        User user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "message", "User registered successfully",
            "username", user.getUsername(),
            "role", user.getRole()
        ));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and invalidate the current JWT token")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            String token = header.substring(7);
            jwtUtil.invalidateToken(token);
            log.info("User logged out — token invalidated");
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/health")
    @Operation(summary = "Auth service health check")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "auth"));
    }

}

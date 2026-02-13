package com.secureai.controller;

import com.secureai.model.LoginRequest;
import com.secureai.model.LoginResponse;
import com.secureai.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {

        if ("admin".equals(request.getUsername())
                && "password".equals(request.getPassword())) {

            String token = jwtUtil.generateToken(request.getUsername());

            LoginResponse response = new LoginResponse();
            response.setToken(token);
            response.setExpiresIn(3600);

            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(401).build();
    }
}

package com.secureai.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// ─────────────────────────────────────────────────────────────────────────────
// Auth DTOs
// ─────────────────────────────────────────────────────────────────────────────

class LoginRequest {
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 100)
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 200)
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}

class LoginResponse {
    private String token;
    private String tokenType = "Bearer";
    private long expiresIn;
    private String username;
    private String role;

    public LoginResponse() {}
    public LoginResponse(String token, long expiresIn, String username, String role) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.username = username;
        this.role = role;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getTokenType() { return tokenType; }
    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}

class RegisterRequest {
    @NotBlank @Size(min = 3, max = 100)
    private String username;

    @NotBlank @Size(min = 8, max = 200)
    private String password;

    @Size(max = 255)
    private String email;

    public String getUsername() { return username; }
    public void setUsername(String u) { this.username = u; }
    public String getPassword() { return password; }
    public void setPassword(String p) { this.password = p; }
    public String getEmail() { return email; }
    public void setEmail(String e) { this.email = e; }
}

// ─────────────────────────────────────────────────────────────────────────────
// AI Ask DTOs
// ─────────────────────────────────────────────────────────────────────────────

class AskRequest {
    @NotBlank(message = "Prompt cannot be empty")
    @Size(min = 1, max = 4000, message = "Prompt must be between 1 and 4000 characters")
    private String prompt;

    private boolean useReActAgent = false;

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public boolean isUseReActAgent() { return useReActAgent; }
    public void setUseReActAgent(boolean useReActAgent) { this.useReActAgent = useReActAgent; }
}

class AskResponse {
    private String response;
    private boolean piiDetected;
    private boolean piiRedacted;
    private int reactSteps;
    private long durationMs;
    private String model;

    public AskResponse() {}
    public AskResponse(String response, boolean piiDetected, boolean piiRedacted,
                       int reactSteps, long durationMs, String model) {
        this.response = response;
        this.piiDetected = piiDetected;
        this.piiRedacted = piiRedacted;
        this.reactSteps = reactSteps;
        this.durationMs = durationMs;
        this.model = model;
    }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
    public boolean isPiiDetected() { return piiDetected; }
    public void setPiiDetected(boolean piiDetected) { this.piiDetected = piiDetected; }
    public boolean isPiiRedacted() { return piiRedacted; }
    public void setPiiRedacted(boolean piiRedacted) { this.piiRedacted = piiRedacted; }
    public int getReactSteps() { return reactSteps; }
    public void setReactSteps(int reactSteps) { this.reactSteps = reactSteps; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}

class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private String path;
    private String timestamp;

    public ErrorResponse(int status, String error, String message, String path) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.timestamp = java.time.Instant.now().toString();
    }

    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
    public String getTimestamp() { return timestamp; }
}

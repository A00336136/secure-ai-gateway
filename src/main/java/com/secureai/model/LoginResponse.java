package com.secureai.model;

public class LoginResponse {
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

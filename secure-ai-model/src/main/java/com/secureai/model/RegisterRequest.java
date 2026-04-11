package com.secureai.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {
    @NotBlank @Size(min = 3, max = 100)
    private String username;

    @NotBlank
    @Size(min = 8, max = 200, message = "Password must be between 8 and 200 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^()\\-_=+])[A-Za-z\\d@$!%*?&#^()\\-_=+]{8,}$",
        message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
    )
    private String password;

    @Email(message = "Invalid email format")
    @Size(max = 255)
    private String email;

    public String getUsername() { return username; }
    public void setUsername(String u) { this.username = u; }
    public String getPassword() { return password; }
    public void setPassword(String p) { this.password = p; }
    public String getEmail() { return email; }
    public void setEmail(String e) { this.email = e; }
}

package com.secureai.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AskRequest {

    @NotBlank(message = "Prompt cannot be empty")
    @Size(max = 8192, message = "Prompt exceeds maximum length of 8KB")
    private String prompt;

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
}


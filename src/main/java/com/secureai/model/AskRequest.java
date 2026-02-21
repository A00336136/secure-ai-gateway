package com.secureai.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AskRequest {
    @NotBlank(message = "Prompt cannot be empty")
    @Size(min = 1, max = 4000, message = "Prompt must be 1-4000 characters")
    private String prompt;
    private boolean useReActAgent = false;

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public boolean isUseReActAgent() { return useReActAgent; }
    public void setUseReActAgent(boolean useReActAgent) { this.useReActAgent = useReActAgent; }
}

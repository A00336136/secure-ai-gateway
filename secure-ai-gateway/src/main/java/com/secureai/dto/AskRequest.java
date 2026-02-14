package com.secureai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for AI query endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for AI query")
public class AskRequest {

    @NotBlank(message = "Prompt cannot be empty")
    @Size(min = 1, max = 8192, message = "Prompt must be between 1 and 8192 characters")
    @Schema(description = "The prompt to send to the AI model", example = "What is the capital of France?", required = true)
    private String prompt;
}

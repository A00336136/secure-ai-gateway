package com.secureai.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for AI query endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Response from AI query")
public class AskResponse {

    @Schema(description = "The AI response (potentially redacted)", example = "The capital of France is Paris.")
    private String response;

    @Schema(description = "Whether PII was detected and redacted", example = "false")
    private boolean redacted;

    @Schema(description = "Unique request identifier", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String requestId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    @Schema(description = "Timestamp of the response", example = "2024-01-15T10:30:00.000Z")
    private Instant timestamp;
}

package com.secureai.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Standardized error response DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Error response")
public class ErrorResponse {

    @Schema(description = "Error message", example = "Invalid request")
    private String message;

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    @Schema(description = "Timestamp of the error", example = "2024-01-15T10:30:00.000Z")
    private Instant timestamp;

    @Schema(description = "Request path", example = "/api/ask")
    private String path;

    @Schema(description = "Validation errors")
    private List<String> errors;
}

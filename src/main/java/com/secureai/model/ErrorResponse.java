package com.secureai.model;

public class ErrorResponse {
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

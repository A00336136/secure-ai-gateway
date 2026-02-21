package com.secureai.model;

public class AskResponse {
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
    public void setResponse(String r) { this.response = r; }
    public boolean isPiiDetected() { return piiDetected; }
    public void setPiiDetected(boolean p) { this.piiDetected = p; }
    public boolean isPiiRedacted() { return piiRedacted; }
    public void setPiiRedacted(boolean p) { this.piiRedacted = p; }
    public int getReactSteps() { return reactSteps; }
    public void setReactSteps(int r) { this.reactSteps = r; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long d) { this.durationMs = d; }
    public String getModel() { return model; }
    public void setModel(String m) { this.model = m; }
}

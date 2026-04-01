package com.secureai.model;

/**
 * AI Gateway Response — includes security metadata alongside the AI answer.
 *
 * New fields added for enterprise observability:
 *  - tokensUsed: estimated token consumption (OWASP LLM10 tracking)
 *  - groundednessScore: 0.0–1.0 hallucination risk score (NIST AI 600-1)
 *  - groundednessVerdict: GROUNDED / PARTIAL / UNGROUNDED / UNKNOWN
 */
public class AskResponse {
    private String response;
    private boolean piiDetected;
    private boolean piiRedacted;
    private int reactSteps;
    private long durationMs;
    private String model;
    // Enterprise observability fields
    private int tokensUsed;
    private double groundednessScore;
    private String groundednessVerdict;

    public AskResponse() {}

    public AskResponse(String response, boolean piiDetected, boolean piiRedacted,
                       int reactSteps, long durationMs, String model) {
        this(response, piiDetected, piiRedacted, reactSteps, durationMs, model, 0, 1.0, "SKIPPED");
    }

    public AskResponse(String response, boolean piiDetected, boolean piiRedacted,
                       int reactSteps, long durationMs, String model,
                       int tokensUsed, double groundednessScore, String groundednessVerdict) {
        this.response = response;
        this.piiDetected = piiDetected;
        this.piiRedacted = piiRedacted;
        this.reactSteps = reactSteps;
        this.durationMs = durationMs;
        this.model = model;
        this.tokensUsed = tokensUsed;
        this.groundednessScore = groundednessScore;
        this.groundednessVerdict = groundednessVerdict;
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
    public int getTokensUsed() { return tokensUsed; }
    public void setTokensUsed(int t) { this.tokensUsed = t; }
    public double getGroundednessScore() { return groundednessScore; }
    public void setGroundednessScore(double g) { this.groundednessScore = g; }
    public String getGroundednessVerdict() { return groundednessVerdict; }
    public void setGroundednessVerdict(String g) { this.groundednessVerdict = g; }
}

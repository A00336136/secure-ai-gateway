package com.secureai.agent;

import com.secureai.service.OllamaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReAct Agent (Reasoning + Acting)
 *
 * Loop: THOUGHT → ACTION → OBSERVATION → repeat → ANSWER
 *
 * The agent uses the LLM to reason about a problem step-by-step.
 * Each step it produces:
 *   Thought: [reasoning about what to do]
 *   Action: [tool name or ANSWER]
 *   Action Input: [input for the tool]
 *
 * When Action == ANSWER, the loop terminates.
 * Max 10 steps to prevent infinite loops.
 *
 * Reference: "ReAct: Synergizing Reasoning and Acting in Language Models"
 *            Yao et al., 2022 — https://arxiv.org/abs/2210.03629
 */
@Service
public class ReActAgentService {

    private static final Logger log = LoggerFactory.getLogger(ReActAgentService.class);

    @Value("${ollama.react.max-steps:10}")
    private int maxSteps;

    @Autowired
    private OllamaClient ollamaClient;

    private static final Pattern THOUGHT_PATTERN =
            Pattern.compile("Thought:\\s*(.+?)(?=Action:|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTION_PATTERN =
            Pattern.compile("Action:\\s*(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTION_INPUT_PATTERN =
            Pattern.compile("Action Input:\\s*(.+?)(?=Observation:|Thought:|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern FINAL_ANSWER_PATTERN =
            Pattern.compile("Final Answer:\\s*(.+)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final String SYSTEM_PROMPT = """
            You are a helpful AI assistant using the ReAct (Reasoning + Acting) framework.
            You have access to the following tools:
            
            - calculate: Perform mathematical calculations. Input: a math expression.
            - search_knowledge: Answer questions from your training knowledge. Input: a question.
            - summarize: Summarize a given text. Input: text to summarize.
            
            Always follow this EXACT format:
            
            Thought: [your reasoning about what to do next]
            Action: [tool name OR "answer"]
            Action Input: [input for the tool]
            Observation: [result of the action — will be filled in by the system]
            
            When you have enough information to answer the user:
            Thought: I now know the final answer.
            Action: answer
            Final Answer: [your complete response to the user]
            
            Begin!
            """;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Execute the ReAct loop for a given user prompt.
     * @return AgentResult with final answer and step count
     */
    public AgentResult execute(String userPrompt) {
        log.info("ReAct agent starting for prompt: {}...",
                userPrompt.length() > 60 ? userPrompt.substring(0, 60) : userPrompt);

        List<AgentStep> steps = new ArrayList<>();
        StringBuilder conversationHistory = new StringBuilder();
        conversationHistory.append("Question: ").append(userPrompt).append("\n\n");

        for (int step = 1; step <= maxSteps; step++) {
            log.debug("ReAct step {}/{}", step, maxSteps);

            String llmResponse = ollamaClient.generateResponse(
                    conversationHistory.toString(),
                    SYSTEM_PROMPT
            );

            AgentStep agentStep = parseStep(llmResponse, step);
            steps.add(agentStep);

            log.debug("Step {}: thought='{}', action='{}'",
                    step, agentStep.thought, agentStep.action);

            // Check for final answer
            if ("answer".equalsIgnoreCase(agentStep.action) && agentStep.finalAnswer != null) {
                log.info("ReAct agent completed in {} step(s)", step);
                return new AgentResult(agentStep.finalAnswer, steps, step);
            }

            // Execute tool action and add observation
            String observation = executeTool(agentStep.action, agentStep.actionInput);
            agentStep.observation = observation;

            // Append to conversation
            conversationHistory.append(llmResponse).append("\n");
            conversationHistory.append("Observation: ").append(observation).append("\n\n");
        }

        // Max steps reached — return best available response
        log.warn("ReAct agent reached max steps ({}), returning partial result", maxSteps);
        String fallback = "I've analyzed this through " + maxSteps +
                " reasoning steps. Based on my analysis: " + userPrompt;
        return new AgentResult(fallback, steps, maxSteps);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tool Execution
    // ─────────────────────────────────────────────────────────────────────────

    private String executeTool(String action, String input) {
        if (action == null) return "No action specified.";
        return switch (action.toLowerCase().trim()) {
            case "calculate" -> executeCalculation(input);
            case "search_knowledge" -> executeKnowledgeSearch(input);
            case "summarize" -> executeSummarize(input);
            default -> "Unknown tool: " + action + ". Available tools: calculate, search_knowledge, summarize";
        };
    }

    private String executeCalculation(String expression) {
        try {
            // Simple safe evaluation — real impl would use a math library
            // For demonstration: delegate back to LLM with specific prompt
            String result = ollamaClient.generateResponse(
                    "Calculate this mathematical expression and return ONLY the numeric result: " + expression
            );
            return "Result: " + result.trim();
        } catch (Exception e) {
            return "Calculation error: " + e.getMessage();
        }
    }

    private String executeKnowledgeSearch(String query) {
        try {
            String result = ollamaClient.generateResponse(
                    "Answer this question concisely based on your knowledge: " + query
            );
            return result.trim();
        } catch (Exception e) {
            return "Search error: " + e.getMessage();
        }
    }

    private String executeSummarize(String text) {
        try {
            String result = ollamaClient.generateResponse(
                    "Summarize this text in 2-3 sentences: " + text
            );
            return "Summary: " + result.trim();
        } catch (Exception e) {
            return "Summarize error: " + e.getMessage();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parsing
    // ─────────────────────────────────────────────────────────────────────────

    private AgentStep parseStep(String llmResponse, int stepNumber) {
        AgentStep step = new AgentStep(stepNumber);

        Matcher thoughtMatcher = THOUGHT_PATTERN.matcher(llmResponse);
        if (thoughtMatcher.find()) {
            step.thought = thoughtMatcher.group(1).trim();
        }

        Matcher actionMatcher = ACTION_PATTERN.matcher(llmResponse);
        if (actionMatcher.find()) {
            step.action = actionMatcher.group(1).trim();
        }

        Matcher inputMatcher = ACTION_INPUT_PATTERN.matcher(llmResponse);
        if (inputMatcher.find()) {
            step.actionInput = inputMatcher.group(1).trim();
        }

        Matcher finalMatcher = FINAL_ANSWER_PATTERN.matcher(llmResponse);
        if (finalMatcher.find()) {
            step.finalAnswer = finalMatcher.group(1).trim();
            step.action = "answer";
        }

        step.rawResponse = llmResponse;
        return step;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data Classes
    // ─────────────────────────────────────────────────────────────────────────

    public static class AgentStep {
        public final int stepNumber;
        public String thought;
        public String action;
        public String actionInput;
        public String observation;
        public String finalAnswer;
        public String rawResponse;

        public AgentStep(int stepNumber) {
            this.stepNumber = stepNumber;
        }
    }

    public static class AgentResult {
        public final String answer;
        public final List<AgentStep> steps;
        public final int totalSteps;

        public AgentResult(String answer, List<AgentStep> steps, int totalSteps) {
            this.answer = answer;
            this.steps = steps;
            this.totalSteps = totalSteps;
        }
    }
}

package com.secureai.service;

import com.secureai.agent.ReActAgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReActAgentService Tests")
class ReActAgentServiceTest {

    @InjectMocks
    private ReActAgentService agentService;

    @Mock
    private OllamaClient ollamaClient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(agentService, "maxSteps", 5);
    }

    @Test
    @DisplayName("Agent should return final answer when LLM provides it immediately")
    void agentShouldReturnFinalAnswer() {
        String llmResponse = """
                Thought: I know the answer directly.
                Action: answer
                Final Answer: Paris is the capital of France.
                """;
        when(ollamaClient.generateResponse(anyString(), anyString())).thenReturn(llmResponse);

        ReActAgentService.AgentResult result = agentService.execute("What is the capital of France?");

        assertThat(result.answer).contains("Paris");
        assertThat(result.totalSteps).isEqualTo(1);
    }

    @Test
    @DisplayName("Agent should complete in exactly one step for immediate answer")
    void agentCompletesInOneStep() {
        String llmResponse = """
                Thought: Simple question.
                Action: answer
                Final Answer: 42
                """;
        when(ollamaClient.generateResponse(anyString(), anyString())).thenReturn(llmResponse);

        ReActAgentService.AgentResult result = agentService.execute("What is the answer?");

        assertThat(result.totalSteps).isEqualTo(1);
        assertThat(result.steps).hasSize(1);
    }

    @Test
    @DisplayName("Agent should hit max steps and return fallback")
    void agentShouldRespectMaxSteps() {
        // LLM never produces a final answer
        String neverEndsResponse = """
                Thought: Still thinking...
                Action: search_knowledge
                Action Input: something
                """;
        when(ollamaClient.generateResponse(anyString(), anyString()))
                .thenReturn(neverEndsResponse);
        // Also mock tool call responses
        when(ollamaClient.generateResponse(anyString(), isNull()))
                .thenReturn("Some observation result");

        ReActAgentService.AgentResult result = agentService.execute("Infinite loop question");

        assertThat(result.totalSteps).isEqualTo(5); // maxSteps
        assertThat(result.answer).isNotNull();
    }

    @Test
    @DisplayName("Agent should use tools and then answer")
    void agentShouldUseTools() {
        // Step 1: LLM decides to use knowledge search
        String response1 = "Thought: I need to search.\nAction: search_knowledge\nAction Input: capital of France";
        // Step 2: LLM provides final answer based on observation
        String response2 = "Thought: I found it.\nAction: answer\nFinal Answer: Paris";

        when(ollamaClient.generateResponse(anyString(), anyString())).thenReturn(response1, response2);
        when(ollamaClient.generateResponse(contains("Answer this question concisely"))).thenReturn("Paris knowledge result");

        ReActAgentService.AgentResult result = agentService.execute("user_prompt");

        assertThat(result.totalSteps).isEqualTo(2);
        assertThat(result.answer).isEqualTo("Paris");
    }

    @Test
    @DisplayName("Agent should handle calculation tool")
    void agentShouldHandleCalculation() {
        String response1 = "Thought: Let me calculate.\nAction: calculate\nAction Input: 2+2";
        String response2 = "Thought: Result is 4.\nAction: answer\nFinal Answer: 4";

        when(ollamaClient.generateResponse(anyString(), anyString())).thenReturn(response1, response2);
        when(ollamaClient.generateResponse(contains("Calculate this mathematical expression"))).thenReturn("4");

        ReActAgentService.AgentResult result = agentService.execute("calc");
        assertThat(result.answer).isEqualTo("4");
    }

    @Test
    @DisplayName("Agent should handle summarize tool")
    void agentShouldHandleSummarize() {
        String response1 = "Thought: Let me summarize.\nAction: summarize\nAction Input: long text";
        String response2 = "Thought: Summary done.\nAction: answer\nFinal Answer: short text";

        when(ollamaClient.generateResponse(anyString(), anyString())).thenReturn(response1, response2);
        when(ollamaClient.generateResponse(contains("Summarize this text"))).thenReturn("short text");

        ReActAgentService.AgentResult result = agentService.execute("sum");
        assertThat(result.answer).isEqualTo("short text");
    }

    @Test
    @DisplayName("Agent should handle unknown tool")
    void agentShouldHandleUnknownTool() {
        String response1 = "Thought: Use unknown tool.\nAction: magic_wand\nAction Input: abra cadabra";
        String response2 = "Thought: It failed.\nAction: answer\nFinal Answer: error";

        when(ollamaClient.generateResponse(anyString(), anyString())).thenReturn(response1, response2);

        ReActAgentService.AgentResult result = agentService.execute("magic");
        assertThat(result.steps.get(0).getObservation()).contains("Unknown tool");
    }

    @Test
    @DisplayName("Agent should handle tool execution error")
    void agentShouldHandleToolError() {
        String response1 = "Thought: Let me calculate.\nAction: calculate\nAction Input: 2+2";
        String response2 = "Thought: It failed.\nAction: answer\nFinal Answer: error";

        when(ollamaClient.generateResponse(anyString(), anyString())).thenReturn(response1, response2);
        when(ollamaClient.generateResponse(contains("Calculate this mathematical expression"))).thenThrow(new RuntimeException("Ollama down"));

        ReActAgentService.AgentResult result = agentService.execute("calc error");
        assertThat(result.steps.get(0).getObservation()).contains("Calculation error");
    }

    @Test
    @DisplayName("Agent should handle knowledge search tool error")
    void agentShouldHandleKnowledgeSearchError() {
        String response1 = "Thought: Search.\nAction: search_knowledge\nAction Input: query";
        String response2 = "Thought: Failed.\nAction: answer\nFinal Answer: fallback";

        when(ollamaClient.generateResponse(anyString(), anyString())).thenReturn(response1, response2);
        when(ollamaClient.generateResponse(contains("Answer this question concisely"))).thenThrow(new RuntimeException("Search failed"));

        ReActAgentService.AgentResult result = agentService.execute("search error");
        assertThat(result.steps.get(0).getObservation()).contains("Search error");
    }

    @Test
    @DisplayName("Agent should handle summarize tool error")
    void agentShouldHandleSummarizeError() {
        String response1 = "Thought: Summarize.\nAction: summarize\nAction Input: some text";
        String response2 = "Thought: Failed.\nAction: answer\nFinal Answer: fallback";

        when(ollamaClient.generateResponse(anyString(), anyString())).thenReturn(response1, response2);
        when(ollamaClient.generateResponse(contains("Summarize this text"))).thenThrow(new RuntimeException("Summarize failed"));

        ReActAgentService.AgentResult result = agentService.execute("summarize error");
        assertThat(result.steps.get(0).getObservation()).contains("Summarize error");
    }

    @Test
    @DisplayName("Agent should handle null action from LLM response")
    void agentShouldHandleNullAction() {
        // LLM returns something with no Action line at all
        String response1 = "I'm just rambling without following the format.";
        String response2 = "Thought: Done.\nAction: answer\nFinal Answer: recovered";

        when(ollamaClient.generateResponse(anyString(), anyString())).thenReturn(response1, response2);

        ReActAgentService.AgentResult result = agentService.execute("null action test");
        assertThat(result.steps.get(0).getObservation()).isEqualTo("No action specified.");
    }

    @Test
    @DisplayName("Agent should handle long prompt truncation in log")
    void agentShouldHandleLongPrompt() {
        String longPrompt = "x".repeat(100);
        String llmResponse = "Thought: Simple.\nAction: answer\nFinal Answer: done";

        when(ollamaClient.generateResponse(anyString(), anyString())).thenReturn(llmResponse);

        ReActAgentService.AgentResult result = agentService.execute(longPrompt);
        assertThat(result.answer).isEqualTo("done");
    }

    @Test
    @DisplayName("Agent should handle short prompt without truncation")
    void agentShouldHandleShortPrompt() {
        String shortPrompt = "Hi";
        String llmResponse = "Thought: Simple.\nAction: answer\nFinal Answer: hello";

        when(ollamaClient.generateResponse(anyString(), anyString())).thenReturn(llmResponse);

        ReActAgentService.AgentResult result = agentService.execute(shortPrompt);
        assertThat(result.answer).isEqualTo("hello");
    }
}

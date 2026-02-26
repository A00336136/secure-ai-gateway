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
    @DisplayName("Steps list should have correct size")
    void stepsListShouldHaveCorrectSize() {
        String llmResponse = """
                Thought: I know.
                Action: answer
                Final Answer: Done.
                """;
        when(ollamaClient.generateResponse(anyString(), anyString())).thenReturn(llmResponse);

        ReActAgentService.AgentResult result = agentService.execute("Test");
        assertThat(result.steps).hasSize(result.totalSteps);
    }
}

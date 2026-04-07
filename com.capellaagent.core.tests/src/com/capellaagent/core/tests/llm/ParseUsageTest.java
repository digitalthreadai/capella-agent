package com.capellaagent.core.tests.llm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.capellaagent.core.llm.ILlmProvider;
import com.capellaagent.core.llm.LlmException;
import com.capellaagent.core.llm.LlmMessage;
import com.capellaagent.core.llm.LlmRequestConfig;
import com.capellaagent.core.llm.LlmResponse;
import com.capellaagent.core.llm.LlmUsage;
import com.capellaagent.core.llm.providers.ClaudeProvider;
import com.capellaagent.core.llm.providers.OllamaProvider;
import com.capellaagent.core.llm.providers.OpenAiProvider;
import com.capellaagent.core.tools.IToolDescriptor;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Unit tests for {@link ILlmProvider#parseUsage} across the three response
 * shape families (OpenAI-compatible, Anthropic, Ollama).
 * <p>
 * Each provider receives a fixture JSON payload mimicking its native shape
 * and the parsed {@link LlmUsage} is asserted.
 */
class ParseUsageTest {

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    @DisplayName("OpenAI shape: usage.prompt_tokens / completion_tokens")
    void openAiShape() {
        OpenAiProvider p = new OpenAiProvider();
        JsonObject response = parse("""
            {
              "id": "chatcmpl-xyz",
              "usage": {
                "prompt_tokens": 1234,
                "completion_tokens": 567,
                "total_tokens": 1801
              }
            }
            """);
        LlmUsage u = p.parseUsage(response);
        assertEquals(1234, u.inputTokens());
        assertEquals(567, u.outputTokens());
        assertEquals(LlmUsage.Source.EXACT, u.source());
    }

    @Test
    @DisplayName("OpenAI shape with cached_tokens and reasoning_tokens")
    void openAiShapeWithCachedAndReasoning() {
        OpenAiProvider p = new OpenAiProvider();
        JsonObject response = parse("""
            {
              "usage": {
                "prompt_tokens": 1000,
                "completion_tokens": 200,
                "prompt_tokens_details": {
                  "cached_tokens": 300
                },
                "completion_tokens_details": {
                  "reasoning_tokens": 50
                }
              }
            }
            """);
        LlmUsage u = p.parseUsage(response);
        assertEquals(300, u.cachedInputTokens());
        assertEquals(50, u.reasoningTokens());
    }

    @Test
    @DisplayName("Anthropic shape: usage.input_tokens / output_tokens")
    void anthropicShape() {
        ClaudeProvider p = new ClaudeProvider();
        JsonObject response = parse("""
            {
              "id": "msg_xyz",
              "usage": {
                "input_tokens": 800,
                "output_tokens": 400,
                "cache_read_input_tokens": 200
              }
            }
            """);
        LlmUsage u = p.parseUsage(response);
        assertEquals(800, u.inputTokens());
        assertEquals(400, u.outputTokens());
        assertEquals(200, u.cachedInputTokens());
        assertEquals(LlmUsage.Source.EXACT, u.source());
    }

    @Test
    @DisplayName("Ollama shape: prompt_eval_count / eval_count (no usage wrapper)")
    void ollamaShape() {
        OllamaProvider p = new OllamaProvider();
        JsonObject response = parse("""
            {
              "model": "llama3.1",
              "prompt_eval_count": 245,
              "eval_count": 88
            }
            """);
        LlmUsage u = p.parseUsage(response);
        assertEquals(245, u.inputTokens());
        assertEquals(88, u.outputTokens());
        assertEquals(LlmUsage.Source.EXACT, u.source());
    }

    @Test
    @DisplayName("missing usage block returns empty()")
    void missingUsageReturnsEmpty() {
        OpenAiProvider p = new OpenAiProvider();
        JsonObject response = parse("{\"id\":\"x\"}");
        LlmUsage u = p.parseUsage(response);
        assertEquals(LlmUsage.empty(), u);
    }

    @Test
    @DisplayName("null response returns empty()")
    void nullResponseReturnsEmpty() {
        OpenAiProvider p = new OpenAiProvider();
        assertEquals(LlmUsage.empty(), p.parseUsage(null));
    }

    @Test
    @DisplayName("default ILlmProvider.parseUsage returns empty()")
    void defaultParseUsageReturnsEmpty() {
        // A provider that doesn't override parseUsage uses the default
        ILlmProvider p = new ILlmProvider() {
            @Override public String getId() { return "x"; }
            @Override public String getDisplayName() { return "X"; }
            @Override public LlmResponse chat(List<LlmMessage> m,
                                              List<IToolDescriptor> t,
                                              LlmRequestConfig c) throws LlmException {
                return null;
            }
        };
        JsonObject anyJson = parse("{\"usage\":{\"prompt_tokens\":99}}");
        // The default implementation returns empty regardless of content
        assertEquals(LlmUsage.empty(), p.parseUsage(anyJson));
    }
}

package com.capellaagent.core.llm;

import java.util.List;

import com.capellaagent.core.tools.IToolDescriptor;

/**
 * Interface for LLM provider implementations.
 * <p>
 * Each provider adapts a specific LLM API (Anthropic Claude, OpenAI, Ollama, etc.)
 * to the unified capella-agent messaging protocol. Providers are registered via
 * the {@code com.capellaagent.core.llmProvider} extension point or programmatically
 * through {@link LlmProviderRegistry}.
 * <p>
 * Implementations must be thread-safe, as multiple agent sessions may share
 * a single provider instance.
 */
public interface ILlmProvider {

    /**
     * Returns the unique identifier for this provider.
     * <p>
     * Examples: "anthropic", "openai", "ollama".
     *
     * @return the provider ID
     */
    String getId();

    /**
     * Returns a human-readable display name for this provider.
     * <p>
     * Examples: "Anthropic Claude", "OpenAI GPT", "Ollama (Local)".
     *
     * @return the display name
     */
    String getDisplayName();

    /**
     * Sends a chat completion request to the LLM.
     * <p>
     * The provider converts the generic message list and tool descriptors into
     * its native API format, executes the HTTP call, and parses the response
     * back into a unified {@link LlmResponse}.
     *
     * @param messages the conversation history as a list of messages
     * @param tools    the tools available for the LLM to call; may be empty but not null
     * @param config   the request configuration (model, temperature, etc.)
     * @return the parsed LLM response
     * @throws LlmException if the request fails for any reason
     */
    LlmResponse chat(List<LlmMessage> messages, List<IToolDescriptor> tools,
                     LlmRequestConfig config) throws LlmException;

    /**
     * Parses the provider-specific usage data from a raw chat response into the
     * normalized {@link LlmUsage} record.
     * <p>
     * The default implementation returns {@link LlmUsage#empty()} which signals
     * "no usage data available." Each provider should override this to extract
     * the actual token counts from its native response shape:
     * <ul>
     *   <li>OpenAI / Groq / GitHub Models / DeepSeek / Mistral / OpenRouter:
     *       {@code usage.prompt_tokens} and {@code usage.completion_tokens}</li>
     *   <li>Anthropic: {@code usage.input_tokens} and {@code usage.output_tokens},
     *       plus {@code usage.cache_read_input_tokens} for caching</li>
     *   <li>Ollama: {@code prompt_eval_count} and {@code eval_count}</li>
     *   <li>Gemini: {@code usageMetadata.promptTokenCount} and
     *       {@code usageMetadata.candidatesTokenCount}</li>
     * </ul>
     * The default returning empty is safe — callers fall back to estimation.
     *
     * @param rawResponse the parsed JSON object from the provider's HTTP response
     * @return normalized usage info, or {@link LlmUsage#empty()} if not available
     */
    default LlmUsage parseUsage(com.google.gson.JsonObject rawResponse) {
        return LlmUsage.empty();
    }
}

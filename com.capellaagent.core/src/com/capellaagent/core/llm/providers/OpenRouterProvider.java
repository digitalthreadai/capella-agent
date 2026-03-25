package com.capellaagent.core.llm.providers;

import java.util.Map;

/**
 * LLM provider for OpenRouter — multi-model gateway providing access to
 * Claude, GPT-4, Llama, Mistral, and many other models through a single API.
 * <p>
 * OpenRouter implements the OpenAI Chat Completions protocol with additional
 * required headers ({@code HTTP-Referer} and {@code X-Title}).
 * <p>
 * Get an API key at <a href="https://openrouter.ai/keys">openrouter.ai/keys</a>.
 * Model routing: set model to a specific model ID (e.g., {@code anthropic/claude-sonnet-4})
 * or {@code auto} to let OpenRouter choose the best model.
 */
public class OpenRouterProvider extends OpenAiCompatibleProvider {

    @Override
    public String getId() {
        return "openrouter";
    }

    @Override
    public String getDisplayName() {
        return "OpenRouter";
    }

    @Override
    protected String getDefaultApiUrl() {
        return "https://openrouter.ai/api/v1/chat/completions";
    }

    @Override
    protected String getDefaultModel() {
        return "auto";
    }

    @Override
    protected String getApiKeyId() {
        return "openrouter";
    }

    @Override
    protected Map<String, String> getExtraHeaders() {
        return Map.of(
            "HTTP-Referer", "https://capella-agent.eclipse.dev",
            "X-Title", "Capella Agent"
        );
    }
}

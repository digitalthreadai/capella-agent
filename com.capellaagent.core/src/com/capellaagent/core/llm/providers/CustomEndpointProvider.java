package com.capellaagent.core.llm.providers;

import com.capellaagent.core.config.AgentConfiguration;

/**
 * User-configurable LLM provider for any OpenAI-compatible API endpoint.
 * <p>
 * This provider reads its endpoint URL, model name, and API key ID from
 * Eclipse preferences, allowing users to connect to any service that
 * implements the OpenAI Chat Completions protocol without writing code.
 * <p>
 * Configuration in Preferences &gt; Capella Agent &gt; LLM Provider &gt; Custom:
 * <ul>
 *   <li>{@code custom.endpoint.url} — the chat completions endpoint URL</li>
 *   <li>{@code custom.endpoint.model} — the model name</li>
 *   <li>API key stored under the {@code "custom"} key in Secure Storage</li>
 * </ul>
 * <p>
 * Examples of compatible services: vLLM, LM Studio, text-generation-inference,
 * LocalAI, FastChat, Anyscale, Fireworks AI, Together AI, Perplexity, etc.
 */
public class CustomEndpointProvider extends OpenAiCompatibleProvider {

    private static final String DEFAULT_URL = "http://localhost:8080/v1/chat/completions";
    private static final String DEFAULT_MODEL = "default";

    @Override
    public String getId() {
        return "custom";
    }

    @Override
    public String getDisplayName() {
        return "Custom Endpoint";
    }

    @Override
    protected String getDefaultApiUrl() {
        String url = AgentConfiguration.getInstance().getCustomEndpointUrl();
        return (url != null && !url.isBlank()) ? url : DEFAULT_URL;
    }

    @Override
    protected String getDefaultModel() {
        String model = AgentConfiguration.getInstance().getCustomEndpointModel();
        return (model != null && !model.isBlank()) ? model : DEFAULT_MODEL;
    }

    @Override
    protected String getApiKeyId() {
        return "custom";
    }

    @Override
    protected boolean requiresApiKey() {
        // Custom endpoints may not require an API key (e.g., local vLLM)
        return false;
    }
}

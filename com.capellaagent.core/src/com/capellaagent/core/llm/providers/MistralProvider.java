package com.capellaagent.core.llm.providers;

/**
 * LLM provider for Mistral AI — European AI lab with strong multilingual models.
 * <p>
 * Mistral implements the OpenAI Chat Completions protocol.
 * Get an API key at <a href="https://console.mistral.ai">console.mistral.ai</a>.
 */
public class MistralProvider extends OpenAiCompatibleProvider {

    @Override
    public String getId() {
        return "mistral";
    }

    @Override
    public String getDisplayName() {
        return "Mistral AI";
    }

    @Override
    protected String getDefaultApiUrl() {
        return "https://api.mistral.ai/v1/chat/completions";
    }

    @Override
    protected String getDefaultModel() {
        return "mistral-large-latest";
    }

    @Override
    protected String getApiKeyId() {
        return "mistral";
    }
}

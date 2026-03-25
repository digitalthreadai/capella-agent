package com.capellaagent.core.llm.providers;

/**
 * LLM provider for DeepSeek — high-performance reasoning models.
 * <p>
 * DeepSeek implements the OpenAI Chat Completions protocol.
 * Get an API key at <a href="https://platform.deepseek.com">platform.deepseek.com</a>.
 */
public class DeepSeekProvider extends OpenAiCompatibleProvider {

    @Override
    public String getId() {
        return "deepseek";
    }

    @Override
    public String getDisplayName() {
        return "DeepSeek";
    }

    @Override
    protected String getDefaultApiUrl() {
        return "https://api.deepseek.com/v1/chat/completions";
    }

    @Override
    protected String getDefaultModel() {
        return "deepseek-chat";
    }

    @Override
    protected String getApiKeyId() {
        return "deepseek";
    }
}

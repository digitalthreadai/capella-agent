package com.capellaagent.core.llm.providers;

/**
 * LLM provider for Groq Cloud — ultra-fast inference using custom LPU hardware.
 * <p>
 * Groq implements the OpenAI Chat Completions protocol, so this provider
 * inherits all protocol logic from {@link OpenAiCompatibleProvider}.
 * <p>
 * Get an API key at <a href="https://console.groq.com">console.groq.com</a>.
 * A free tier is available with generous rate limits.
 */
public class GroqProvider extends OpenAiCompatibleProvider {

    @Override
    public String getId() {
        return "groq";
    }

    @Override
    public String getDisplayName() {
        return "Groq Cloud";
    }

    @Override
    protected String getDefaultApiUrl() {
        return "https://api.groq.com/openai/v1/chat/completions";
    }

    @Override
    protected String getDefaultModel() {
        return "llama-3.3-70b-versatile";
    }

    @Override
    protected String getApiKeyId() {
        return "groq";
    }
}

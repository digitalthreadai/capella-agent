package com.capellaagent.core.llm.providers;

import java.net.http.HttpClient;
import java.util.Map;

/**
 * LLM provider for the OpenAI Chat Completions API.
 * <p>
 * Supports both the standard OpenAI endpoint and Azure OpenAI deployments.
 * For Azure, set the endpoint override to your Azure deployment URL and the
 * auth header will automatically switch to {@code api-key}.
 */
public class OpenAiProvider extends OpenAiCompatibleProvider {

    private static final String DEFAULT_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4o";

    private volatile String endpointOverride;

    public OpenAiProvider() {
        super();
    }

    public OpenAiProvider(HttpClient httpClient) {
        super(httpClient);
    }

    /**
     * Sets an alternative API endpoint (e.g., for Azure OpenAI).
     * <p>
     * Azure format: {@code https://{resource}.openai.azure.com/openai/deployments/{deployment}/chat/completions?api-version=2024-02-01}
     *
     * @param endpoint the full endpoint URL, or null to use the default
     */
    public void setEndpointOverride(String endpoint) {
        this.endpointOverride = endpoint;
    }

    @Override
    public String getId() {
        return "openai";
    }

    @Override
    public String getDisplayName() {
        return "OpenAI GPT";
    }

    @Override
    protected String getDefaultApiUrl() {
        return (endpointOverride != null && !endpointOverride.isBlank())
                ? endpointOverride : DEFAULT_API_URL;
    }

    @Override
    protected String getDefaultModel() {
        return DEFAULT_MODEL;
    }

    @Override
    protected String getApiKeyId() {
        return "openai";
    }

    @Override
    protected String getAuthHeaderName() {
        // Azure uses api-key header; standard OpenAI uses Authorization
        if (endpointOverride != null && endpointOverride.contains("azure")) {
            return "api-key";
        }
        return "Authorization";
    }

    @Override
    protected String getAuthHeaderValue(String apiKey) {
        // Azure passes the key directly; standard OpenAI uses Bearer token
        if (endpointOverride != null && endpointOverride.contains("azure")) {
            return apiKey;
        }
        return "Bearer " + apiKey;
    }
}

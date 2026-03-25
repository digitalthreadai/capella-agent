package com.capellaagent.core.llm;

import java.util.Objects;

/**
 * Configuration DTO controlling how an LLM request is executed.
 * <p>
 * Provides model selection, generation parameters, and an optional system prompt
 * that is prepended to the conversation. Uses sensible defaults suitable for
 * tool-augmented agent interactions.
 */
public final class LlmRequestConfig {

    /** Default temperature for agent interactions: low randomness for reliability. */
    public static final double DEFAULT_TEMPERATURE = 0.3;

    /** Default maximum tokens in the LLM response. */
    public static final int DEFAULT_MAX_TOKENS = 4096;

    private final String modelId;
    private final double temperature;
    private final int maxTokens;
    private final String systemPrompt;

    /**
     * Constructs a fully specified request configuration.
     *
     * @param modelId      the model identifier to use; if null, the provider's default is used
     * @param temperature  sampling temperature (0.0 to 2.0)
     * @param maxTokens    maximum tokens in the response
     * @param systemPrompt optional system prompt; may be null
     */
    public LlmRequestConfig(String modelId, double temperature, int maxTokens, String systemPrompt) {
        this.modelId = modelId;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.systemPrompt = systemPrompt;
    }

    /**
     * Creates a configuration with default temperature and max tokens.
     *
     * @param modelId      the model identifier; may be null for provider default
     * @param systemPrompt the system prompt; may be null
     * @return a new configuration instance
     */
    public static LlmRequestConfig withDefaults(String modelId, String systemPrompt) {
        return new LlmRequestConfig(modelId, DEFAULT_TEMPERATURE, DEFAULT_MAX_TOKENS, systemPrompt);
    }

    /**
     * Creates a configuration using all defaults and no system prompt.
     *
     * @return a new default configuration instance
     */
    public static LlmRequestConfig defaults() {
        return new LlmRequestConfig(null, DEFAULT_TEMPERATURE, DEFAULT_MAX_TOKENS, null);
    }

    /**
     * Returns the model identifier, or null if the provider default should be used.
     *
     * @return the model ID or null
     */
    public String getModelId() {
        return modelId;
    }

    /**
     * Returns the sampling temperature.
     *
     * @return the temperature value
     */
    public double getTemperature() {
        return temperature;
    }

    /**
     * Returns the maximum number of tokens to generate.
     *
     * @return the max tokens value
     */
    public int getMaxTokens() {
        return maxTokens;
    }

    /**
     * Returns the system prompt, or null if none is set.
     *
     * @return the system prompt or null
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }

    @Override
    public String toString() {
        return "LlmRequestConfig{modelId='" + modelId +
                "', temperature=" + temperature +
                ", maxTokens=" + maxTokens +
                ", systemPrompt=" + (systemPrompt != null ? "set" : "null") + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LlmRequestConfig that = (LlmRequestConfig) o;
        return Double.compare(that.temperature, temperature) == 0 &&
                maxTokens == that.maxTokens &&
                Objects.equals(modelId, that.modelId) &&
                Objects.equals(systemPrompt, that.systemPrompt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelId, temperature, maxTokens, systemPrompt);
    }
}

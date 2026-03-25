package com.capellaagent.core.config;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;
import org.osgi.service.prefs.BackingStoreException;

/**
 * Centralized configuration for the capella-agent ecosystem.
 * <p>
 * Reads and writes Eclipse preferences using the standard
 * {@link IEclipsePreferences} mechanism. API keys are stored separately
 * using Eclipse Secure Preferences ({@link ISecurePreferences}) to avoid
 * plaintext credential storage.
 * <p>
 * Preference node: {@code com.capellaagent.core}.
 * <p>
 * This is a singleton; obtain the instance via {@link #getInstance()}.
 */
public final class AgentConfiguration {

    private static final Logger LOG = Logger.getLogger(AgentConfiguration.class.getName());

    private static final AgentConfiguration INSTANCE = new AgentConfiguration();

    /** The preference node qualifier. */
    public static final String QUALIFIER = "com.capellaagent.core";

    // -- Preference keys --
    public static final String KEY_LLM_PROVIDER_ID = "llm.provider.id";
    public static final String KEY_LLM_MODEL_ID = "llm.model.id";
    public static final String KEY_LLM_TEMPERATURE = "llm.temperature";
    public static final String KEY_LLM_MAX_TOKENS = "llm.max_tokens";
    public static final String KEY_LLM_SYSTEM_PROMPT = "llm.system_prompt";
    public static final String KEY_SECURITY_ACCESS_MODE = "security.access_mode";
    public static final String KEY_SECURITY_AUDIT_ENABLED = "security.audit_enabled";
    public static final String KEY_CUSTOM_ENDPOINT_URL = "custom.endpoint.url";
    public static final String KEY_CUSTOM_ENDPOINT_MODEL = "custom.endpoint.model";

    // -- Defaults --
    private static final String DEFAULT_PROVIDER_ID = "anthropic";
    private static final String DEFAULT_MODEL_ID = "";
    private static final double DEFAULT_TEMPERATURE = 0.3;
    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final String DEFAULT_SYSTEM_PROMPT = "";
    private static final String DEFAULT_ACCESS_MODE = "READ_ONLY";
    private static final boolean DEFAULT_AUDIT_ENABLED = true;

    /** Secure preferences node for API keys. */
    private static final String SECURE_NODE = "com.capellaagent.core/apikeys";

    private AgentConfiguration() {
        // Singleton
    }

    /**
     * Returns the singleton instance.
     *
     * @return the configuration instance
     */
    public static AgentConfiguration getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the Eclipse preferences node for this plugin.
     *
     * @return the preferences node
     */
    private IEclipsePreferences getPreferences() {
        return InstanceScope.INSTANCE.getNode(QUALIFIER);
    }

    /**
     * Flushes pending preference changes to persistent storage.
     */
    private void flush() {
        try {
            getPreferences().flush();
        } catch (BackingStoreException e) {
            LOG.log(Level.WARNING, "Failed to flush preferences", e);
        }
    }

    // -- LLM Provider --

    /**
     * Returns the configured LLM provider ID.
     *
     * @return the provider ID
     */
    public String getLlmProviderId() {
        return getPreferences().get(KEY_LLM_PROVIDER_ID, DEFAULT_PROVIDER_ID);
    }

    /**
     * Sets the LLM provider ID.
     *
     * @param providerId the provider ID to set
     */
    public void setLlmProviderId(String providerId) {
        getPreferences().put(KEY_LLM_PROVIDER_ID, providerId);
        flush();
    }

    // -- LLM Model --

    /**
     * Returns the configured model ID. Empty string means use provider default.
     *
     * @return the model ID or empty string
     */
    public String getLlmModelId() {
        return getPreferences().get(KEY_LLM_MODEL_ID, DEFAULT_MODEL_ID);
    }

    /**
     * Sets the LLM model ID.
     *
     * @param modelId the model ID to set; empty string for provider default
     */
    public void setLlmModelId(String modelId) {
        getPreferences().put(KEY_LLM_MODEL_ID, modelId);
        flush();
    }

    // -- Temperature --

    /**
     * Returns the configured sampling temperature.
     *
     * @return the temperature value
     */
    public double getLlmTemperature() {
        return getPreferences().getDouble(KEY_LLM_TEMPERATURE, DEFAULT_TEMPERATURE);
    }

    /**
     * Sets the sampling temperature.
     *
     * @param temperature the temperature value (0.0 to 2.0)
     */
    public void setLlmTemperature(double temperature) {
        getPreferences().putDouble(KEY_LLM_TEMPERATURE, temperature);
        flush();
    }

    // -- Max Tokens --

    /**
     * Returns the configured maximum tokens for LLM responses.
     *
     * @return the max tokens value
     */
    public int getLlmMaxTokens() {
        return getPreferences().getInt(KEY_LLM_MAX_TOKENS, DEFAULT_MAX_TOKENS);
    }

    /**
     * Sets the maximum tokens for LLM responses.
     *
     * @param maxTokens the max tokens value
     */
    public void setLlmMaxTokens(int maxTokens) {
        getPreferences().putInt(KEY_LLM_MAX_TOKENS, maxTokens);
        flush();
    }

    // -- System Prompt --

    /**
     * Returns the configured system prompt for LLM interactions.
     *
     * @return the system prompt, or empty string if not set
     */
    public String getLlmSystemPrompt() {
        return getPreferences().get(KEY_LLM_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT);
    }

    /**
     * Sets the system prompt for LLM interactions.
     *
     * @param systemPrompt the system prompt text
     */
    public void setLlmSystemPrompt(String systemPrompt) {
        getPreferences().put(KEY_LLM_SYSTEM_PROMPT, systemPrompt);
        flush();
    }

    // -- Security: Access Mode --

    /**
     * Returns the configured security access mode string.
     *
     * @return "READ_ONLY" or "READ_WRITE"
     */
    public String getSecurityAccessMode() {
        return getPreferences().get(KEY_SECURITY_ACCESS_MODE, DEFAULT_ACCESS_MODE);
    }

    /**
     * Sets the security access mode.
     *
     * @param accessMode "READ_ONLY" or "READ_WRITE"
     */
    public void setSecurityAccessMode(String accessMode) {
        getPreferences().put(KEY_SECURITY_ACCESS_MODE, accessMode);
        flush();
    }

    // -- Security: Audit --

    /**
     * Returns whether audit logging is enabled.
     *
     * @return true if audit logging is enabled
     */
    public boolean isAuditEnabled() {
        return getPreferences().getBoolean(KEY_SECURITY_AUDIT_ENABLED, DEFAULT_AUDIT_ENABLED);
    }

    /**
     * Sets whether audit logging is enabled.
     *
     * @param enabled true to enable audit logging
     */
    public void setAuditEnabled(boolean enabled) {
        getPreferences().putBoolean(KEY_SECURITY_AUDIT_ENABLED, enabled);
        flush();
    }

    // -- Custom Endpoint --

    /**
     * Returns the custom endpoint URL for OpenAI-compatible APIs.
     *
     * @return the endpoint URL, or empty string if not set
     */
    public String getCustomEndpointUrl() {
        return getPreferences().get(KEY_CUSTOM_ENDPOINT_URL, "");
    }

    /**
     * Sets the custom endpoint URL.
     *
     * @param url the endpoint URL
     */
    public void setCustomEndpointUrl(String url) {
        getPreferences().put(KEY_CUSTOM_ENDPOINT_URL, url);
        flush();
    }

    /**
     * Returns the custom endpoint model name.
     *
     * @return the model name, or empty string if not set
     */
    public String getCustomEndpointModel() {
        return getPreferences().get(KEY_CUSTOM_ENDPOINT_MODEL, "");
    }

    /**
     * Sets the custom endpoint model name.
     *
     * @param model the model name
     */
    public void setCustomEndpointModel(String model) {
        getPreferences().put(KEY_CUSTOM_ENDPOINT_MODEL, model);
        flush();
    }

    // -- API Key Management (Secure Preferences) --

    /**
     * Retrieves the API key for the specified LLM provider from secure storage.
     *
     * @param providerId the provider ID (e.g., "anthropic", "openai")
     * @return the API key, or null if not set
     */
    public String getApiKey(String providerId) {
        try {
            ISecurePreferences root = SecurePreferencesFactory.getDefault();
            ISecurePreferences node = root.node(SECURE_NODE);
            return node.get(providerId, null);
        } catch (StorageException e) {
            LOG.log(Level.WARNING, "Failed to retrieve API key for provider: " + providerId, e);
            return null;
        }
    }

    /**
     * Stores the API key for the specified LLM provider in secure storage.
     *
     * @param providerId the provider ID
     * @param apiKey     the API key to store
     */
    public void setApiKey(String providerId, String apiKey) {
        try {
            ISecurePreferences root = SecurePreferencesFactory.getDefault();
            ISecurePreferences node = root.node(SECURE_NODE);
            node.put(providerId, apiKey, true /* encrypt */);
            node.flush();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to store API key for provider: " + providerId, e);
        }
    }

    /**
     * Removes the API key for the specified LLM provider from secure storage.
     *
     * @param providerId the provider ID
     */
    public void removeApiKey(String providerId) {
        try {
            ISecurePreferences root = SecurePreferencesFactory.getDefault();
            ISecurePreferences node = root.node(SECURE_NODE);
            node.remove(providerId);
            node.flush();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to remove API key for provider: " + providerId, e);
        }
    }

    /**
     * Builds an {@link com.capellaagent.core.llm.LlmRequestConfig} from the current
     * configuration values.
     *
     * @return a request config populated from preferences
     */
    public com.capellaagent.core.llm.LlmRequestConfig buildRequestConfig() {
        String modelId = getLlmModelId();
        return new com.capellaagent.core.llm.LlmRequestConfig(
                modelId.isEmpty() ? null : modelId,
                getLlmTemperature(),
                getLlmMaxTokens(),
                getLlmSystemPrompt().isEmpty() ? null : getLlmSystemPrompt()
        );
    }
}

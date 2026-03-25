package com.capellaagent.core.llm;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.capellaagent.core.config.AgentConfiguration;

/**
 * Thread-safe singleton registry for LLM provider implementations.
 * <p>
 * Providers register themselves at bundle activation time (via extension point
 * processing or direct registration). Agent sessions retrieve the active provider
 * via {@link #getActiveProvider()}, which reads the configured provider ID from
 * {@link AgentConfiguration}.
 */
public final class LlmProviderRegistry {

    private static final Logger LOG = Logger.getLogger(LlmProviderRegistry.class.getName());

    private static final LlmProviderRegistry INSTANCE = new LlmProviderRegistry();

    private final ConcurrentHashMap<String, ILlmProvider> providers = new ConcurrentHashMap<>();
    private volatile String activeProviderId;

    private LlmProviderRegistry() {
        // Singleton
    }

    /**
     * Returns the singleton instance of the provider registry.
     *
     * @return the registry instance
     */
    public static LlmProviderRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Registers an LLM provider. If a provider with the same ID already exists,
     * it is replaced and a warning is logged.
     *
     * @param provider the provider to register; must not be null
     */
    public void registerProvider(ILlmProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        ILlmProvider previous = providers.put(provider.getId(), provider);
        if (previous != null) {
            LOG.warning("Replaced existing LLM provider: " + provider.getId());
        }
        LOG.info("Registered LLM provider: " + provider.getId() + " (" + provider.getDisplayName() + ")");
    }

    /**
     * Retrieves a provider by its unique identifier.
     *
     * @param id the provider ID
     * @return the provider, or null if not found
     */
    public ILlmProvider getProvider(String id) {
        return providers.get(id);
    }

    /**
     * Returns the currently active LLM provider.
     * <p>
     * The active provider is determined by:
     * <ol>
     *   <li>The explicitly set active provider ID (via {@link #setActiveProviderId})</li>
     *   <li>The provider ID stored in {@link AgentConfiguration}</li>
     *   <li>The first registered provider as a fallback</li>
     * </ol>
     *
     * @return the active provider
     * @throws LlmException if no provider is available
     */
    public ILlmProvider getActiveProvider() throws LlmException {
        // Try explicitly set active ID first
        String id = activeProviderId;

        // Fall back to configuration
        if (id == null || id.isBlank()) {
            id = AgentConfiguration.getInstance().getLlmProviderId();
        }

        // Try to find the configured provider
        if (id != null && !id.isBlank()) {
            ILlmProvider provider = providers.get(id);
            if (provider != null) {
                return provider;
            }
            LOG.warning("Configured LLM provider '" + id + "' not found, falling back");
        }

        // Fall back to any available provider
        if (!providers.isEmpty()) {
            ILlmProvider fallback = providers.values().iterator().next();
            LOG.info("Using fallback LLM provider: " + fallback.getId());
            return fallback;
        }

        throw new LlmException(LlmException.ERR_PROVIDER_NOT_FOUND,
                "No LLM providers are registered. Install at least one provider bundle.");
    }

    /**
     * Sets the active provider ID, overriding the configuration value.
     *
     * @param providerId the provider ID to activate; may be null to clear the override
     */
    public void setActiveProviderId(String providerId) {
        this.activeProviderId = providerId;
        LOG.info("Active LLM provider set to: " + providerId);
    }

    /**
     * Returns an unmodifiable collection of all registered providers.
     *
     * @return all registered providers
     */
    public Collection<ILlmProvider> listProviders() {
        return Collections.unmodifiableCollection(providers.values());
    }

    /**
     * Removes all registered providers. Intended for testing only.
     */
    public void clear() {
        providers.clear();
        activeProviderId = null;
    }
}

package com.capellaagent.core;

import java.util.logging.Logger;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

import com.capellaagent.core.llm.LlmProviderRegistry;
import com.capellaagent.core.llm.providers.ClaudeProvider;
import com.capellaagent.core.llm.providers.CustomEndpointProvider;
import com.capellaagent.core.llm.providers.DeepSeekProvider;
import com.capellaagent.core.llm.providers.GeminiProvider;
import com.capellaagent.core.llm.providers.GroqProvider;
import com.capellaagent.core.llm.providers.MistralProvider;
import com.capellaagent.core.llm.providers.OllamaProvider;
import com.capellaagent.core.llm.providers.OpenAiProvider;
import com.capellaagent.core.llm.providers.OpenRouterProvider;

/**
 * OSGi bundle activator for the {@code com.capellaagent.core} plugin.
 * <p>
 * Registers built-in LLM providers on startup and performs cleanup on stop.
 */
public class Activator extends Plugin {

    private static final Logger LOG = Logger.getLogger(Activator.class.getName());

    /** The plug-in ID. */
    public static final String PLUGIN_ID = "com.capellaagent.core";

    /** The shared activator instance. */
    private static Activator plugin;

    /**
     * Returns the shared activator instance.
     *
     * @return the activator, or null if the bundle is not started
     */
    public static Activator getDefault() {
        return plugin;
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;

        LOG.info("Capella Agent Core starting...");

        // Register built-in LLM providers
        LlmProviderRegistry registry = LlmProviderRegistry.getInstance();
        registry.registerProvider(new ClaudeProvider());
        registry.registerProvider(new OpenAiProvider());
        registry.registerProvider(new GroqProvider());
        registry.registerProvider(new DeepSeekProvider());
        registry.registerProvider(new MistralProvider());
        registry.registerProvider(new OpenRouterProvider());
        registry.registerProvider(new GeminiProvider());
        registry.registerProvider(new OllamaProvider());
        registry.registerProvider(new CustomEndpointProvider());

        LOG.info("Capella Agent Core started. Registered " +
                registry.listProviders().size() + " LLM providers.");
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        LOG.info("Capella Agent Core stopping...");
        plugin = null;
        super.stop(context);
    }
}

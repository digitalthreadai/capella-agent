package com.capellaagent.modelchat;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

/**
 * Bundle activator for the Model Chat plugin.
 * <p>
 * On startup, initializes the {@link ModelChatToolRegistrar} which registers all
 * model-query and model-mutation tools with the core {@code ToolRegistry}.
 */
public class ModelChatActivator extends Plugin {

    /** The shared plugin instance. */
    private static ModelChatActivator instance;

    /** The unique plugin identifier. */
    public static final String PLUGIN_ID = "com.capellaagent.modelchat";

    private ModelChatToolRegistrar toolRegistrar;

    /**
     * Returns the shared plugin instance.
     *
     * @return the singleton activator instance, or {@code null} if the bundle has not been started
     */
    public static ModelChatActivator getDefault() {
        return instance;
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        instance = this;

        toolRegistrar = new ModelChatToolRegistrar();
        toolRegistrar.registerAll();

        getLog().info("Model Chat tools registered successfully");
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        if (toolRegistrar != null) {
            toolRegistrar.unregisterAll();
            toolRegistrar = null;
        }
        instance = null;
        super.stop(context);
    }

    /**
     * Returns the tool registrar managing this plugin's tools.
     *
     * @return the tool registrar instance
     */
    public ModelChatToolRegistrar getToolRegistrar() {
        return toolRegistrar;
    }
}

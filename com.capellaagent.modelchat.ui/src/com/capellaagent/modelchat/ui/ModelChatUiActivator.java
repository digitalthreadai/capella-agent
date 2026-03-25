package com.capellaagent.modelchat.ui;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

/**
 * Bundle activator for the Model Chat UI plugin.
 * <p>
 * Manages the lifecycle of the UI bundle, which provides the chat view,
 * command handlers, and element link adapters for the Model Chat Agent.
 */
public class ModelChatUiActivator extends Plugin {

    /** The unique plugin identifier. */
    public static final String PLUGIN_ID = "com.capellaagent.modelchat.ui";

    /** The shared plugin instance. */
    private static ModelChatUiActivator instance;

    /**
     * Returns the shared plugin instance.
     *
     * @return the singleton activator instance, or {@code null} if the bundle has not been started
     */
    public static ModelChatUiActivator getDefault() {
        return instance;
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        instance = this;
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        instance = null;
        super.stop(context);
    }
}

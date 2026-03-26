package com.capellaagent.modelchat.ui;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.Bundle;
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

    private static final Logger LOG = Logger.getLogger(ModelChatUiActivator.class.getName());

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        instance = this;

        // Force-start the modelchat bundle so its tools get registered with ToolRegistry.
        // Without this, the lazy activation policy means tools are never registered
        // because no class from com.capellaagent.modelchat is directly loaded by the UI.
        ensureBundleStarted("com.capellaagent.modelchat");
        ensureBundleStarted("com.capellaagent.core");
    }

    /**
     * Ensures the specified OSGi bundle is started (activator has run).
     */
    private void ensureBundleStarted(String symbolicName) {
        Bundle bundle = Platform.getBundle(symbolicName);
        if (bundle != null && bundle.getState() != Bundle.ACTIVE) {
            try {
                bundle.start(Bundle.START_TRANSIENT);
                LOG.info("Force-started bundle: " + symbolicName);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to start bundle: " + symbolicName, e);
            }
        }
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        instance = null;
        super.stop(context);
    }
}

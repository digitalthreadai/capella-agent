package com.capellaagent.teamcenter;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

/**
 * Bundle activator for the Teamcenter integration plugin.
 * <p>
 * On startup, initializes the {@link TcToolRegistrar} which registers all
 * Teamcenter search, query, import, and traceability tools with the core
 * {@code ToolRegistry}.
 */
public class TcActivator extends Plugin {

    /** The shared plugin instance. */
    private static TcActivator instance;

    /** The unique plugin identifier. */
    public static final String PLUGIN_ID = "com.capellaagent.teamcenter";

    private TcToolRegistrar toolRegistrar;

    /**
     * Returns the shared plugin instance.
     *
     * @return the singleton activator instance, or {@code null} if the bundle has not been started
     */
    public static TcActivator getDefault() {
        return instance;
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        instance = this;

        toolRegistrar = new TcToolRegistrar();
        toolRegistrar.registerAll();

        getLog().info("Teamcenter integration tools registered successfully");
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
    public TcToolRegistrar getToolRegistrar() {
        return toolRegistrar;
    }
}

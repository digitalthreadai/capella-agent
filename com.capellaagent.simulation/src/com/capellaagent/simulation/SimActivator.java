package com.capellaagent.simulation;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

/**
 * Bundle activator for the Simulation integration plugin.
 * <p>
 * On startup, initializes the {@link SimToolRegistrar} which registers all
 * simulation tools (engine management, parameter extraction, simulation
 * execution, result propagation, and what-if analysis) with the core
 * {@code ToolRegistry}.
 */
public class SimActivator extends Plugin {

    /** The shared plugin instance. */
    private static SimActivator instance;

    /** The unique plugin identifier. */
    public static final String PLUGIN_ID = "com.capellaagent.simulation";

    private SimToolRegistrar toolRegistrar;

    /**
     * Returns the shared plugin instance.
     *
     * @return the singleton activator instance, or {@code null} if not started
     */
    public static SimActivator getDefault() {
        return instance;
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        instance = this;

        toolRegistrar = new SimToolRegistrar();
        toolRegistrar.registerAll();

        getLog().info("Simulation integration tools registered successfully");
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
    public SimToolRegistrar getToolRegistrar() {
        return toolRegistrar;
    }
}

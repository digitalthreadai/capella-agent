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

    /**
     * System property that must be set to {@code "true"} to enable simulation tool
     * registration. Default is {@code false} — the simulation bundle is placeholder
     * scaffolding that does not connect to a real engine in this build.
     * <p>
     * Set via {@code -Dcapellaagent.simulation.enabled=true} in {@code capella.ini}
     * or the launch configuration VM arguments.
     */
    public static final String SIMULATION_ENABLED_PROP = "capellaagent.simulation.enabled";

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        instance = this;

        if (Boolean.getBoolean(SIMULATION_ENABLED_PROP)) {
            toolRegistrar = new SimToolRegistrar();
            toolRegistrar.registerAll();
            getLog().info("Simulation integration tools registered successfully");
        } else {
            getLog().info("Simulation tools NOT registered "
                    + "(set -D" + SIMULATION_ENABLED_PROP + "=true to enable)");
        }
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

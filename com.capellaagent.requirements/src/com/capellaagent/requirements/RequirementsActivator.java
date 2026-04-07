package com.capellaagent.requirements;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

/**
 * OSGi bundle activator for com.capellaagent.requirements.
 * <p>
 * On start, registers all requirements tools with the core ToolRegistry.
 * On stop, unregisters them.
 */
public class RequirementsActivator extends Plugin {

    public static final String PLUGIN_ID = "com.capellaagent.requirements";

    private static RequirementsActivator instance;
    private RequirementsToolRegistrar registrar;

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        instance = this;
        registrar = new RequirementsToolRegistrar();
        registrar.registerAll();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        if (registrar != null) {
            registrar.unregisterAll();
            registrar = null;
        }
        instance = null;
        super.stop(context);
    }

    public static RequirementsActivator getDefault() {
        return instance;
    }
}

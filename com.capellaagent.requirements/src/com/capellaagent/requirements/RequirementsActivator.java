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

        // SECURITY (I5/N5): set POI hard limits ONCE at bundle activation,
        // not per-tool. These are JVM-wide and guarantee the ZIP-bomb /
        // integer-overflow defences are in effect before any POI call site
        // runs — the earlier per-tool approach raced with other call sites.
        try {
            org.apache.poi.util.IOUtils.setByteArrayMaxOverride(100 * 1024 * 1024);
            org.apache.poi.openxml4j.util.ZipSecureFile.setMinInflateRatio(0.01);
            org.apache.poi.openxml4j.util.ZipSecureFile.setMaxEntrySize(100L * 1024 * 1024);
            org.apache.poi.openxml4j.util.ZipSecureFile.setMaxTextSize(50L * 1024 * 1024);
        } catch (Throwable t) {
            // POI version skew should not prevent the bundle from activating.
            getLog().warn("POI security limits could not be fully applied: "
                + t.getMessage());
        }

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

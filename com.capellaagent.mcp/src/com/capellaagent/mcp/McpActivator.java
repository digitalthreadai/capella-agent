package com.capellaagent.mcp;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

/**
 * OSGi bundle activator for the MCP bridge plugin.
 * <p>
 * On startup, launches a local HTTP endpoint that exposes the shared
 * {@link com.capellaagent.core.tools.ToolRegistry} to external MCP bridge
 * processes. This allows Claude Code to call Capella tools via the
 * Model Context Protocol.
 */
public class McpActivator extends Plugin {

    private static final Logger LOG = Logger.getLogger(McpActivator.class.getName());

    public static final String PLUGIN_ID = "com.capellaagent.mcp";

    /** Default port for the local HTTP bridge endpoint. */
    public static final int DEFAULT_PORT = 9847;

    private static McpActivator plugin;
    private McpHttpEndpoint httpEndpoint;

    public static McpActivator getDefault() {
        return plugin;
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;

        int port = DEFAULT_PORT;
        String portEnv = System.getenv("CAPELLA_MCP_PORT");
        if (portEnv != null && !portEnv.isBlank()) {
            try {
                port = Integer.parseInt(portEnv);
            } catch (NumberFormatException e) {
                LOG.warning("Invalid CAPELLA_MCP_PORT: " + portEnv + ", using default " + DEFAULT_PORT);
            }
        }

        try {
            httpEndpoint = new McpHttpEndpoint();
            httpEndpoint.start(port);
            LOG.info("MCP HTTP bridge started on port " + port);
            LOG.info("Configure Claude Code .mcp.json to connect via: "
                    + "java -jar capella-mcp-bridge.jar " + port);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to start MCP HTTP bridge on port " + port, e);
        }
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        if (httpEndpoint != null) {
            httpEndpoint.stop();
            LOG.info("MCP HTTP bridge stopped.");
        }
        plugin = null;
        super.stop(context);
    }
}

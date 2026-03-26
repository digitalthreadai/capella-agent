package com.capellaagent.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.capellaagent.core.tools.IToolDescriptor;
import com.capellaagent.core.tools.ToolExecutionException;
import com.capellaagent.core.tools.ToolRegistry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Lightweight HTTP server that exposes the {@link ToolRegistry} as a local-only
 * REST API for the MCP bridge subprocess.
 * <p>
 * Binds to {@code 127.0.0.1} only — never exposed to the network. The bridge
 * subprocess ({@link com.capellaagent.mcp.bridge.McpServerBridge}) connects to
 * this endpoint and translates between MCP JSON-RPC (stdio) and HTTP.
 * <p>
 * Endpoints:
 * <ul>
 *   <li>{@code GET /tools/list} — returns all registered tools as JSON</li>
 *   <li>{@code POST /tools/call} — executes a tool and returns the result</li>
 *   <li>{@code GET /health} — simple health check</li>
 * </ul>
 */
public class McpHttpEndpoint {

    private static final Logger LOG = Logger.getLogger(McpHttpEndpoint.class.getName());

    private HttpServer server;

    /**
     * Starts the HTTP server on the specified port, bound to localhost only.
     *
     * @param port the port to bind to
     * @throws IOException if the server cannot be started
     */
    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/tools/list", this::handleListTools);
        server.createContext("/tools/call", this::handleCallTool);
        server.createContext("/health", this::handleHealth);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.start();
    }

    /**
     * Stops the HTTP server.
     */
    public void stop() {
        if (server != null) {
            server.stop(1);
        }
    }

    /**
     * GET /health — simple health check.
     */
    private void handleHealth(HttpExchange exchange) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");
        response.addProperty("tools", ToolRegistry.getInstance().size());
        sendJson(exchange, 200, response.toString());
    }

    /**
     * GET /tools/list — returns all registered tools in MCP format.
     */
    private void handleListTools(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {
            List<IToolDescriptor> tools = ToolRegistry.getInstance().getTools();
            JsonObject response = McpToolAdapter.buildToolListResponse(tools);
            sendJson(exchange, 200, response.toString());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error listing tools", e);
            sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * POST /tools/call — executes a tool and returns the result.
     * <p>
     * Request body: {@code {"name": "tool_name", "arguments": {...}}}
     */
    private void handleCallTool(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String body;
        try (InputStream is = exchange.getRequestBody()) {
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        try {
            JsonObject request = JsonParser.parseString(body).getAsJsonObject();
            String toolName = request.get("name").getAsString();
            JsonObject arguments = request.has("arguments")
                    ? request.getAsJsonObject("arguments")
                    : new JsonObject();

            LOG.fine("MCP tool call: " + toolName + " args: " + arguments);

            // Execute the tool via the shared registry.
            // Tool execution may need the Eclipse UI thread for EMF access.
            // For now, execute on the HTTP server thread (tools handle
            // Display.syncExec internally in AbstractCapellaTool).
            JsonObject result = ToolRegistry.getInstance().execute(toolName, arguments);

            JsonObject response = new JsonObject();
            response.addProperty("status", "ok");
            response.add("result", result);
            sendJson(exchange, 200, response.toString());

        } catch (ToolExecutionException e) {
            LOG.log(Level.WARNING, "Tool execution failed", e);
            JsonObject error = new JsonObject();
            error.addProperty("error", true);
            error.addProperty("code", e.getErrorCode());
            error.addProperty("message", e.getMessage());
            sendJson(exchange, 200, error.toString()); // 200 with error body (MCP convention)
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error calling tool", e);
            sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}

package com.capellaagent.mcp.bridge;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Standalone MCP server bridge that translates between the MCP stdio
 * protocol (JSON-RPC 2.0 over stdin/stdout) and the HTTP endpoint
 * running inside the Eclipse Capella process.
 * <p>
 * This is the process that Claude Code spawns via {@code .mcp.json}.
 * It acts as a thin relay:
 * <ol>
 *   <li>Reads JSON-RPC requests from stdin (from Claude Code)</li>
 *   <li>Forwards tool calls to Eclipse's HTTP endpoint</li>
 *   <li>Writes JSON-RPC responses to stdout (back to Claude Code)</li>
 * </ol>
 * <p>
 * Usage: {@code java -cp <classpath> com.capellaagent.mcp.bridge.McpServerBridge [port]}
 * <p>
 * Default port: 9847 (matches {@code McpActivator.DEFAULT_PORT}).
 */
public class McpServerBridge {

    private static final String SERVER_NAME = "capella-live";
    private static final String SERVER_VERSION = "1.0.0";

    private final McpStdioTransport transport;
    private final HttpClient httpClient;
    private final String eclipseBaseUrl;

    public McpServerBridge(int eclipsePort) {
        this.transport = new McpStdioTransport();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.eclipseBaseUrl = "http://127.0.0.1:" + eclipsePort;
    }

    /**
     * Main entry point. Runs the MCP server loop until stdin is closed.
     */
    public void run() {
        transport.log("Starting MCP bridge -> " + eclipseBaseUrl);

        // Verify Eclipse endpoint is reachable
        if (!checkHealth()) {
            transport.log("ERROR: Cannot reach Eclipse MCP endpoint at " + eclipseBaseUrl);
            transport.log("Ensure Capella is running with the MCP plugin installed.");
            System.exit(1);
        }

        transport.log("Connected to Eclipse Capella. Waiting for MCP requests...");

        try {
            while (true) {
                JsonObject request = transport.readMessage();
                if (request == null) {
                    transport.log("stdin closed. Shutting down.");
                    break;
                }

                JsonObject response = handleRequest(request);
                if (response != null) {
                    transport.writeMessage(response);
                }
            }
        } catch (Exception e) {
            transport.log("Fatal error: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Dispatches a JSON-RPC request to the appropriate handler.
     */
    private JsonObject handleRequest(JsonObject request) {
        String method = request.has("method") ? request.get("method").getAsString() : "";
        Object id = extractId(request);

        // Notifications (no id) don't require responses
        if (id == null && !method.equals("notifications/initialized")) {
            return null;
        }

        try {
            return switch (method) {
                case "initialize" -> handleInitialize(id, request);
                case "notifications/initialized" -> null; // No response needed
                case "tools/list" -> handleToolsList(id);
                case "tools/call" -> handleToolsCall(id, request);
                case "ping" -> McpStdioTransport.successResponse(id, new JsonObject());
                default -> {
                    transport.log("Unknown method: " + method);
                    yield McpStdioTransport.errorResponse(id, -32601, "Method not found: " + method);
                }
            };
        } catch (Exception e) {
            transport.log("Error handling " + method + ": " + e.getMessage());
            return McpStdioTransport.errorResponse(id, -32603, e.getMessage());
        }
    }

    /**
     * Handles the MCP initialize handshake.
     */
    private JsonObject handleInitialize(Object id, JsonObject request) {
        transport.log("Initialize request received");

        JsonObject capabilities = new JsonObject();
        JsonObject toolsCap = new JsonObject();
        capabilities.add("tools", toolsCap);

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", SERVER_NAME);
        serverInfo.addProperty("version", SERVER_VERSION);

        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", "2024-11-05");
        result.add("capabilities", capabilities);
        result.add("serverInfo", serverInfo);

        return McpStdioTransport.successResponse(id, result);
    }

    /**
     * Handles tools/list by fetching the tool list from Eclipse.
     */
    private JsonObject handleToolsList(Object id) throws Exception {
        transport.log("Listing tools...");

        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(eclipseBaseUrl + "/tools/list"))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> httpResp = httpClient.send(httpReq,
                HttpResponse.BodyHandlers.ofString());

        if (httpResp.statusCode() != 200) {
            return McpStdioTransport.errorResponse(id, -32603,
                    "Eclipse returned HTTP " + httpResp.statusCode());
        }

        // Eclipse returns {"tools": [...]} — pass through directly
        JsonObject result = JsonParser.parseString(httpResp.body()).getAsJsonObject();
        transport.log("Found " + result.getAsJsonArray("tools").size() + " tools");
        return McpStdioTransport.successResponse(id, result);
    }

    /**
     * Handles tools/call by forwarding the tool call to Eclipse.
     */
    private JsonObject handleToolsCall(Object id, JsonObject request) throws Exception {
        JsonObject params = request.getAsJsonObject("params");
        String toolName = params.get("name").getAsString();
        JsonObject arguments = params.has("arguments")
                ? params.getAsJsonObject("arguments")
                : new JsonObject();

        transport.log("Calling tool: " + toolName);

        // Forward to Eclipse HTTP endpoint
        JsonObject callBody = new JsonObject();
        callBody.addProperty("name", toolName);
        callBody.add("arguments", arguments);

        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(eclipseBaseUrl + "/tools/call"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(callBody.toString()))
                .timeout(Duration.ofSeconds(120))
                .build();

        HttpResponse<String> httpResp = httpClient.send(httpReq,
                HttpResponse.BodyHandlers.ofString());

        JsonObject eclipseResult = JsonParser.parseString(httpResp.body()).getAsJsonObject();

        // Wrap in MCP content format
        boolean isError = eclipseResult.has("error")
                && eclipseResult.get("error").getAsBoolean();

        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text");
        textContent.addProperty("text", eclipseResult.toString());

        JsonArray contentArray = new JsonArray();
        contentArray.add(textContent);

        JsonObject mcpResult = new JsonObject();
        mcpResult.add("content", contentArray);
        if (isError) {
            mcpResult.addProperty("isError", true);
        }

        transport.log("Tool " + toolName + " completed" + (isError ? " (with error)" : ""));
        return McpStdioTransport.successResponse(id, mcpResult);
    }

    /**
     * Checks if the Eclipse HTTP endpoint is reachable.
     */
    private boolean checkHealth() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(eclipseBaseUrl + "/health"))
                    .GET()
                    .timeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<String> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static Object extractId(JsonObject request) {
        if (!request.has("id") || request.get("id").isJsonNull()) {
            return null;
        }
        JsonElement idElement = request.get("id");
        if (idElement.getAsJsonPrimitive().isNumber()) {
            return idElement.getAsLong();
        }
        return idElement.getAsString();
    }

    // ── Entry point ────────────────────────────────────────────────────

    public static void main(String[] args) {
        int port = 9847;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Usage: McpServerBridge [port]");
                System.err.println("  port: Eclipse MCP HTTP endpoint port (default: 9847)");
                System.exit(1);
            }
        }

        McpServerBridge bridge = new McpServerBridge(port);
        bridge.run();
    }
}

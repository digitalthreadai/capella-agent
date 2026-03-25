package com.capellaagent.mcp.bridge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * JSON-RPC 2.0 transport over stdin/stdout for the MCP protocol.
 * <p>
 * Reads newline-delimited JSON-RPC messages from stdin and writes
 * responses to stdout. All diagnostic/logging output goes to stderr
 * to avoid corrupting the protocol stream.
 * <p>
 * This class is used by the standalone bridge subprocess
 * ({@link McpServerBridge}), not by the Eclipse plugin itself.
 */
public class McpStdioTransport {

    private final BufferedReader reader;
    private final PrintStream writer;
    private final PrintStream logger;

    /**
     * Creates a transport using standard process streams.
     */
    public McpStdioTransport() {
        this.reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        this.writer = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        this.logger = System.err;
    }

    /**
     * Reads the next JSON-RPC message from stdin.
     *
     * @return the parsed JSON object, or null if EOF
     * @throws IOException if reading fails
     */
    public JsonObject readMessage() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null; // EOF — client disconnected
        }
        line = line.trim();
        if (line.isEmpty()) {
            return readMessage(); // Skip empty lines
        }
        try {
            return JsonParser.parseString(line).getAsJsonObject();
        } catch (Exception e) {
            log("Failed to parse JSON-RPC message: " + line);
            return readMessage(); // Skip malformed messages
        }
    }

    /**
     * Writes a JSON-RPC response to stdout.
     *
     * @param message the response JSON object
     */
    public void writeMessage(JsonObject message) {
        writer.println(message.toString());
        writer.flush();
    }

    /**
     * Writes a diagnostic message to stderr (never to stdout).
     *
     * @param msg the message to log
     */
    public void log(String msg) {
        logger.println("[capella-mcp] " + msg);
        logger.flush();
    }

    /**
     * Builds a JSON-RPC 2.0 success response.
     *
     * @param id     the request ID
     * @param result the result object
     * @return the response JSON
     */
    public static JsonObject successResponse(Object id, JsonObject result) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        if (id instanceof Number) {
            response.addProperty("id", ((Number) id).longValue());
        } else {
            response.addProperty("id", String.valueOf(id));
        }
        response.add("result", result);
        return response;
    }

    /**
     * Builds a JSON-RPC 2.0 error response.
     *
     * @param id      the request ID
     * @param code    the error code
     * @param message the error message
     * @return the response JSON
     */
    public static JsonObject errorResponse(Object id, int code, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);

        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        if (id instanceof Number) {
            response.addProperty("id", ((Number) id).longValue());
        } else {
            response.addProperty("id", String.valueOf(id));
        }
        response.add("error", error);
        return response;
    }
}

package com.capellaagent.mcp;

import java.util.List;

import com.capellaagent.core.tools.IToolDescriptor;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Converts Capella Agent tool descriptors to MCP-compatible JSON format.
 * <p>
 * The MCP protocol expects tools in this shape:
 * <pre>{@code
 * {
 *   "name": "tool_name",
 *   "description": "What the tool does",
 *   "inputSchema": { "type": "object", "properties": {...}, "required": [...] }
 * }
 * }</pre>
 */
public final class McpToolAdapter {

    private McpToolAdapter() {
        // Utility class
    }

    /**
     * Converts a list of tool descriptors to the MCP tools/list response format.
     *
     * @param tools the tool descriptors from ToolRegistry
     * @return a JsonObject with a "tools" array
     */
    public static JsonObject buildToolListResponse(List<IToolDescriptor> tools) {
        JsonArray toolArray = new JsonArray();
        for (IToolDescriptor tool : tools) {
            toolArray.add(toMcpTool(tool));
        }
        JsonObject response = new JsonObject();
        response.add("tools", toolArray);
        return response;
    }

    /**
     * Converts a single tool descriptor to MCP tool format.
     *
     * @param tool the tool descriptor
     * @return a JsonObject in MCP tool format
     */
    public static JsonObject toMcpTool(IToolDescriptor tool) {
        JsonObject mcpTool = new JsonObject();
        mcpTool.addProperty("name", tool.getName());
        mcpTool.addProperty("description", tool.getDescription());
        mcpTool.add("inputSchema", tool.getParametersSchema());
        return mcpTool;
    }

    /**
     * Wraps a tool execution result in MCP content format.
     *
     * @param result the raw JSON result from tool execution
     * @return a JsonObject with MCP content array
     */
    public static JsonObject wrapResult(JsonObject result) {
        JsonObject content = new JsonObject();
        content.addProperty("type", "text");
        content.addProperty("text", result.toString());

        JsonArray contentArray = new JsonArray();
        contentArray.add(content);

        JsonObject wrapped = new JsonObject();
        wrapped.add("content", contentArray);
        return wrapped;
    }

    /**
     * Wraps an error in MCP content format.
     *
     * @param code    error code
     * @param message error message
     * @return a JsonObject with MCP error content
     */
    public static JsonObject wrapError(String code, String message) {
        JsonObject errorObj = new JsonObject();
        errorObj.addProperty("error", true);
        errorObj.addProperty("code", code);
        errorObj.addProperty("message", message);

        JsonObject content = new JsonObject();
        content.addProperty("type", "text");
        content.addProperty("text", errorObj.toString());

        JsonArray contentArray = new JsonArray();
        contentArray.add(content);

        JsonObject wrapped = new JsonObject();
        wrapped.add("content", contentArray);
        wrapped.addProperty("isError", true);
        return wrapped;
    }
}

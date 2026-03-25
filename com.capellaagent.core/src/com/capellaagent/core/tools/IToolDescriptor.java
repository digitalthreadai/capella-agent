package com.capellaagent.core.tools;

import com.google.gson.JsonObject;

/**
 * Describes a tool that can be offered to an LLM for invocation.
 * <p>
 * Tool descriptors provide the metadata that LLM providers need to present
 * tools in their respective function-calling formats (e.g., Anthropic tool_use,
 * OpenAI function calling).
 */
public interface IToolDescriptor {

    /**
     * Returns the unique name of this tool, used as the function identifier
     * in LLM tool calls.
     * <p>
     * Must be a valid identifier: lowercase letters, digits, and underscores only.
     * Example: "create_component", "list_functions".
     *
     * @return the tool name
     */
    String getName();

    /**
     * Returns a human-readable description of what this tool does.
     * <p>
     * This description is sent to the LLM to help it decide when and how
     * to use the tool. Be specific and include examples of when the tool
     * should be used.
     *
     * @return the tool description
     */
    String getDescription();

    /**
     * Returns the category this tool belongs to, used for filtering and grouping.
     * <p>
     * Examples: "capella.model", "capella.diagram", "teamcenter", "simulation".
     *
     * @return the tool category
     */
    String getCategory();

    /**
     * Returns the JSON Schema describing the parameters this tool accepts.
     * <p>
     * The returned object must be a valid JSON Schema of type "object" with
     * a "properties" map and an optional "required" array. Use
     * {@link ToolSchemaBuilder} to construct this schema fluently.
     *
     * @return a JsonObject representing the JSON Schema for tool parameters
     */
    JsonObject getParametersSchema();
}

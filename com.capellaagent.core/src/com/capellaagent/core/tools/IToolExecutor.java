package com.capellaagent.core.tools;

import com.google.gson.JsonObject;

/**
 * Executes a tool with the given arguments and returns a JSON result.
 * <p>
 * Implementations must be thread-safe if they may be invoked concurrently
 * by multiple agent sessions.
 */
public interface IToolExecutor {

    /**
     * Executes the tool with the provided arguments.
     *
     * @param arguments a JsonObject containing the arguments matching the
     *                  tool's parameter schema
     * @return a JsonObject containing the result of the tool execution
     * @throws ToolExecutionException if the tool execution fails
     */
    JsonObject execute(JsonObject arguments) throws ToolExecutionException;
}

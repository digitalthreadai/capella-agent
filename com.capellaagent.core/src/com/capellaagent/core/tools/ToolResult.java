package com.capellaagent.core.tools;

import com.google.gson.JsonObject;

/**
 * Represents the result of a tool execution, encapsulating either
 * a successful result with data or an error with a message.
 * <p>
 * Use the static factory methods {@link #success(JsonObject)} and
 * {@link #error(String)} to create instances.
 */
public final class ToolResult {

    private final boolean success;
    private final JsonObject data;
    private final String errorMessage;

    private ToolResult(boolean success, JsonObject data, String errorMessage) {
        this.success = success;
        this.data = data;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a successful result containing the given data.
     *
     * @param data the result data as a JsonObject
     * @return a success ToolResult
     */
    public static ToolResult success(JsonObject data) {
        return new ToolResult(true, data, null);
    }

    /**
     * Creates a successful result with just a message (no structured data).
     *
     * @param message the success message
     * @return a success ToolResult
     */
    public static ToolResult successMessage(String message) {
        JsonObject data = new JsonObject();
        data.addProperty("status", "success");
        data.addProperty("message", message);
        return new ToolResult(true, data, null);
    }

    /**
     * Creates an error result with the given message.
     *
     * @param message the error message
     * @return an error ToolResult
     */
    public static ToolResult error(String message) {
        return new ToolResult(false, null, message);
    }

    /**
     * @return true if the tool executed successfully
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * @return the result data (null if this is an error result)
     */
    public JsonObject getData() {
        return data;
    }

    /**
     * @return the error message (null if this is a success result)
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Converts this result to a JsonObject suitable for returning to the LLM.
     * <p>
     * Success results return the data directly. Error results return a JSON
     * object with "status" = "error" and "message" fields.
     *
     * @return the result as a JsonObject
     */
    public JsonObject toJson() {
        if (success && data != null) {
            return data;
        }
        JsonObject json = new JsonObject();
        json.addProperty("status", "error");
        json.addProperty("message", errorMessage != null ? errorMessage : "Unknown error");
        return json;
    }

    @Override
    public String toString() {
        if (success) {
            return "ToolResult[success, data=" + (data != null ? data.toString() : "null") + "]";
        }
        return "ToolResult[error, message=" + errorMessage + "]";
    }
}

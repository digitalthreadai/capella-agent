package com.capellaagent.core.tools;

/**
 * Describes a single parameter for a tool, including its name, description,
 * data type, and whether it is required.
 * <p>
 * Use the static factory methods to create parameters:
 * <pre>{@code
 * ToolParameter.requiredString("layer", "Architecture layer: oa, sa, la, or pa")
 * ToolParameter.optionalInteger("max_results", "Maximum results to return (default: 100)")
 * }</pre>
 * <p>
 * The {@link AbstractCapellaTool} base class uses the list returned by
 * {@code defineParameters()} to build the JSON Schema for the LLM.
 */
public final class ToolParameter {

    private final String name;
    private final String description;
    private final String type;
    private final boolean required;

    private ToolParameter(String name, String description, String type, boolean required) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.required = required;
    }

    // -- Static Factory Methods --

    /**
     * Creates a required string parameter.
     */
    public static ToolParameter requiredString(String name, String description) {
        return new ToolParameter(name, description, "string", true);
    }

    /**
     * Creates an optional string parameter.
     */
    public static ToolParameter optionalString(String name, String description) {
        return new ToolParameter(name, description, "string", false);
    }

    /**
     * Creates a required integer parameter.
     */
    public static ToolParameter requiredInteger(String name, String description) {
        return new ToolParameter(name, description, "integer", true);
    }

    /**
     * Creates an optional integer parameter.
     */
    public static ToolParameter optionalInteger(String name, String description) {
        return new ToolParameter(name, description, "integer", false);
    }

    /**
     * Creates a required number (floating-point) parameter.
     */
    public static ToolParameter requiredNumber(String name, String description) {
        return new ToolParameter(name, description, "number", true);
    }

    /**
     * Creates an optional number (floating-point) parameter.
     */
    public static ToolParameter optionalNumber(String name, String description) {
        return new ToolParameter(name, description, "number", false);
    }

    /**
     * Creates a required boolean parameter.
     */
    public static ToolParameter requiredBoolean(String name, String description) {
        return new ToolParameter(name, description, "boolean", true);
    }

    /**
     * Creates an optional boolean parameter.
     */
    public static ToolParameter optionalBoolean(String name, String description) {
        return new ToolParameter(name, description, "boolean", false);
    }

    /**
     * Creates a required string array parameter.
     */
    public static ToolParameter requiredStringArray(String name, String description) {
        return new ToolParameter(name, description, "array", true);
    }

    /**
     * Creates an optional string array parameter.
     */
    public static ToolParameter optionalStringArray(String name, String description) {
        return new ToolParameter(name, description, "array", false);
    }

    // -- Getters --

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Returns the JSON Schema type: "string", "integer", "number", or "boolean".
     */
    public String getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }
}

package com.capellaagent.core.tools;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Describes a single parameter for a tool, including its name, description,
 * data type, whether it is required, optional enum constraints, and default value.
 * <p>
 * Use the static factory methods to create parameters:
 * <pre>{@code
 * ToolParameter.requiredString("layer", "Architecture layer: oa, sa, la, or pa")
 * ToolParameter.optionalInteger("max_results", "Maximum results to return (default: 100)")
 * ToolParameter.requiredEnum("layer", "Architecture layer", List.of("oa", "sa", "la", "pa"))
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
    private final List<String> enumValues;
    private final String defaultValue;

    private ToolParameter(String name, String description, String type, boolean required,
                           List<String> enumValues, String defaultValue) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.required = required;
        this.enumValues = enumValues;
        this.defaultValue = defaultValue;
    }

    private ToolParameter(String name, String description, String type, boolean required) {
        this(name, description, type, required, null, null);
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

    /**
     * Creates a required string parameter constrained to a set of enum values.
     * The JSON schema will include an "enum" array so the LLM only picks valid values.
     *
     * @param name        the parameter name
     * @param description a human-readable description
     * @param values      the allowed values
     * @return the configured ToolParameter
     */
    public static ToolParameter requiredEnum(String name, String description, List<String> values) {
        return new ToolParameter(name, description, "string", true, values, null);
    }

    /**
     * Creates an optional string parameter constrained to a set of enum values,
     * with a default value.
     *
     * @param name         the parameter name
     * @param description  a human-readable description
     * @param values       the allowed values
     * @param defaultValue the default value when not provided by the LLM
     * @return the configured ToolParameter
     */
    public static ToolParameter optionalEnum(String name, String description,
                                               List<String> values, String defaultValue) {
        return new ToolParameter(name, description, "string", false, values, defaultValue);
    }

    /**
     * Creates an optional string parameter with a default value.
     *
     * @param name         the parameter name
     * @param description  a human-readable description
     * @param defaultValue the default value
     * @return the configured ToolParameter
     */
    public static ToolParameter optionalStringWithDefault(String name, String description,
                                                            String defaultValue) {
        return new ToolParameter(name, description, "string", false, null, defaultValue);
    }

    // -- Getters --

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Returns the JSON Schema type: "string", "integer", "number", "boolean", or "array".
     */
    public String getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    /**
     * Returns the allowed enum values, or null if no enum constraint.
     */
    public List<String> getEnumValues() {
        return enumValues;
    }

    /**
     * Returns the default value, or null if none is set.
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Converts this parameter to a JSON Schema property object.
     * Includes "type", "description", and optionally "enum" and "default".
     *
     * @return the JSON Schema representation
     */
    public JsonObject toJsonSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", type);
        schema.addProperty("description", description);

        if (enumValues != null && !enumValues.isEmpty()) {
            JsonArray enumArray = new JsonArray();
            for (String val : enumValues) {
                enumArray.add(val);
            }
            schema.add("enum", enumArray);
        }

        if (defaultValue != null) {
            schema.addProperty("default", defaultValue);
        }

        return schema;
    }
}

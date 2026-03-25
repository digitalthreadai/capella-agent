package com.capellaagent.core.tools;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Fluent builder for constructing JSON Schema objects that describe tool parameters.
 * <p>
 * Produces schemas conforming to JSON Schema Draft 7, which is the format expected
 * by LLM function-calling APIs (Anthropic, OpenAI, Ollama).
 *
 * <pre>{@code
 * JsonObject schema = new ToolSchemaBuilder()
 *     .stringParam("name", "The name of the component", true)
 *     .stringParam("description", "Optional description", false)
 *     .enumParam("type", "Component type", List.of("LogicalComponent", "PhysicalComponent"), true)
 *     .intParam("priority", "Priority level 1-5", false)
 *     .boolParam("abstract", "Whether the component is abstract", false)
 *     .build();
 * }</pre>
 */
public final class ToolSchemaBuilder {

    private final JsonObject properties;
    private final List<String> required;

    /**
     * Creates a new empty schema builder.
     */
    public ToolSchemaBuilder() {
        this.properties = new JsonObject();
        this.required = new ArrayList<>();
    }

    /**
     * Adds a string parameter to the schema.
     *
     * @param name        the parameter name
     * @param description a description of the parameter for the LLM
     * @param isRequired  whether this parameter is required
     * @return this builder for chaining
     */
    public ToolSchemaBuilder stringParam(String name, String description, boolean isRequired) {
        JsonObject param = new JsonObject();
        param.addProperty("type", "string");
        param.addProperty("description", description);
        properties.add(name, param);
        if (isRequired) {
            required.add(name);
        }
        return this;
    }

    /**
     * Adds an integer parameter to the schema.
     *
     * @param name        the parameter name
     * @param description a description of the parameter for the LLM
     * @param isRequired  whether this parameter is required
     * @return this builder for chaining
     */
    public ToolSchemaBuilder intParam(String name, String description, boolean isRequired) {
        JsonObject param = new JsonObject();
        param.addProperty("type", "integer");
        param.addProperty("description", description);
        properties.add(name, param);
        if (isRequired) {
            required.add(name);
        }
        return this;
    }

    /**
     * Adds a number (floating-point) parameter to the schema.
     *
     * @param name        the parameter name
     * @param description a description of the parameter for the LLM
     * @param isRequired  whether this parameter is required
     * @return this builder for chaining
     */
    public ToolSchemaBuilder numberParam(String name, String description, boolean isRequired) {
        JsonObject param = new JsonObject();
        param.addProperty("type", "number");
        param.addProperty("description", description);
        properties.add(name, param);
        if (isRequired) {
            required.add(name);
        }
        return this;
    }

    /**
     * Adds a boolean parameter to the schema.
     *
     * @param name        the parameter name
     * @param description a description of the parameter for the LLM
     * @param isRequired  whether this parameter is required
     * @return this builder for chaining
     */
    public ToolSchemaBuilder boolParam(String name, String description, boolean isRequired) {
        JsonObject param = new JsonObject();
        param.addProperty("type", "boolean");
        param.addProperty("description", description);
        properties.add(name, param);
        if (isRequired) {
            required.add(name);
        }
        return this;
    }

    /**
     * Adds a string parameter with a fixed set of allowed values (enum).
     *
     * @param name        the parameter name
     * @param description a description of the parameter for the LLM
     * @param values      the allowed values
     * @param isRequired  whether this parameter is required
     * @return this builder for chaining
     */
    public ToolSchemaBuilder enumParam(String name, String description, List<String> values,
                                       boolean isRequired) {
        JsonObject param = new JsonObject();
        param.addProperty("type", "string");
        param.addProperty("description", description);
        JsonArray enumValues = new JsonArray();
        for (String value : values) {
            enumValues.add(value);
        }
        param.add("enum", enumValues);
        properties.add(name, param);
        if (isRequired) {
            required.add(name);
        }
        return this;
    }

    /**
     * Adds an array parameter to the schema.
     *
     * @param name        the parameter name
     * @param description a description of the parameter for the LLM
     * @param itemType    the type of items in the array ("string", "integer", etc.)
     * @param isRequired  whether this parameter is required
     * @return this builder for chaining
     */
    public ToolSchemaBuilder arrayParam(String name, String description, String itemType,
                                        boolean isRequired) {
        JsonObject param = new JsonObject();
        param.addProperty("type", "array");
        param.addProperty("description", description);
        JsonObject items = new JsonObject();
        items.addProperty("type", itemType);
        param.add("items", items);
        properties.add(name, param);
        if (isRequired) {
            required.add(name);
        }
        return this;
    }

    /**
     * Builds the final JSON Schema object.
     *
     * @return a JsonObject representing a valid JSON Schema of type "object"
     */
    public JsonObject build() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties.deepCopy());

        if (!required.isEmpty()) {
            JsonArray requiredArray = new JsonArray();
            for (String name : required) {
                requiredArray.add(name);
            }
            schema.add("required", requiredArray);
        }

        return schema;
    }
}

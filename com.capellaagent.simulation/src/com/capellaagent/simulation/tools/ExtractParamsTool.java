package com.capellaagent.simulation.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.tools.IToolDescriptor;
import com.capellaagent.core.tools.IToolExecutor;
import com.capellaagent.core.tools.ToolExecutionException;
import com.capellaagent.simulation.orchestrator.ParameterExtractor;
import com.capellaagent.simulation.orchestrator.ParameterMapping;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Tool: "extract_simulation_params" -- Previews parameter extraction from the Capella model.
 * <p>
 * Parameters:
 * <ul>
 *   <li>{@code element_uuids} (array of strings, required) - UUIDs of Capella elements to extract from</li>
 * </ul>
 */
public class ExtractParamsTool implements IToolDescriptor, IToolExecutor {

    private static final String TOOL_NAME = "extract_simulation_params";

    private final ParameterExtractor extractor;

    /**
     * Constructs a new ExtractParamsTool.
     *
     * @param extractor the parameter extractor
     */
    public ExtractParamsTool(ParameterExtractor extractor) {
        this.extractor = extractor;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Preview what simulation parameters would be extracted from the given "
                + "Capella model elements. Returns the element UUIDs and their available "
                + "numeric/string property values that can be used as simulation inputs.";
    }

    @Override
    public String getCategory() {
        return "simulation";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject uuidsProp = new JsonObject();
        uuidsProp.addProperty("type", "array");
        JsonObject itemSchema = new JsonObject();
        itemSchema.addProperty("type", "string");
        uuidsProp.add("items", itemSchema);
        uuidsProp.addProperty("description",
                "UUIDs of Capella model elements to extract parameters from");
        properties.add("element_uuids", uuidsProp);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("element_uuids");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) throws ToolExecutionException {
        if (!arguments.has("element_uuids") || !arguments.get("element_uuids").isJsonArray()) {
            throw new ToolExecutionException(ToolExecutionException.ERR_INVALID_ARGS,
                    "Required parameter 'element_uuids' must be an array of strings");
        }

        JsonArray uuidsArray = arguments.getAsJsonArray("element_uuids");
        List<ParameterMapping> mappings = new ArrayList<>();

        // Create a simple mapping for each UUID (extracting generic property values)
        for (JsonElement elem : uuidsArray) {
            String uuid = elem.getAsString();
            // PLACEHOLDER: In a real implementation, we would inspect the element's
            // property values and create mappings for each numeric/string property.
            mappings.add(new ParameterMapping(
                    "param_" + uuid.substring(0, Math.min(8, uuid.length())),
                    uuid,
                    "ownedPropertyValues",
                    "double"
            ));
        }

        // PLACEHOLDER: Get the active Sirius session
        Object session = null;
        Map<String, Object> extracted = extractor.extract(mappings, session);

        JsonObject response = new JsonObject();
        response.addProperty("elementCount", uuidsArray.size());

        JsonArray params = new JsonArray();
        for (Map.Entry<String, Object> entry : extracted.entrySet()) {
            JsonObject param = new JsonObject();
            param.addProperty("name", entry.getKey());
            param.addProperty("value", String.valueOf(entry.getValue()));
            param.addProperty("dataType", entry.getValue() != null
                    ? entry.getValue().getClass().getSimpleName() : "null");
            params.add(param);
        }
        response.add("parameters", params);

        response.addProperty("note",
                "PLACEHOLDER: Actual parameter extraction requires Capella model API. "
                        + "Currently returning default values.");

        return response;
    }
}

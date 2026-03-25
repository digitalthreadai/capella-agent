package com.capellaagent.teamcenter.tools;

import com.capellaagent.core.tools.IToolDescriptor;
import com.capellaagent.core.tools.IToolExecutor;
import com.capellaagent.core.tools.ToolExecutionException;
import com.capellaagent.teamcenter.api.TcObjectService;
import com.capellaagent.teamcenter.client.TcException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Tool: "tc_get_object" -- Retrieves full properties of a Teamcenter object by UID.
 * <p>
 * Parameters:
 * <ul>
 *   <li>{@code uid} (string, required) - The Teamcenter object UID</li>
 * </ul>
 */
public class TcGetObjectTool implements IToolDescriptor, IToolExecutor {

    private static final String TOOL_NAME = "tc_get_object";

    private final TcObjectService objectService;

    /**
     * Constructs a new TcGetObjectTool.
     *
     * @param objectService the Teamcenter object service
     */
    public TcGetObjectTool(TcObjectService objectService) {
        this.objectService = objectService;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Retrieve all properties of a Teamcenter object by its UID. "
                + "Returns the full property set including name, type, status, "
                + "owner, dates, and all custom attributes.";
    }

    @Override
    public String getCategory() {
        return "teamcenter";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject uidProp = new JsonObject();
        uidProp.addProperty("type", "string");
        uidProp.addProperty("description", "The Teamcenter object UID");
        properties.add("uid", uidProp);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("uid");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) throws ToolExecutionException {
        if (!arguments.has("uid") || arguments.get("uid").isJsonNull()) {
            throw new ToolExecutionException(ToolExecutionException.ERR_INVALID_ARGS,
                    "Required parameter 'uid' is missing");
        }
        String uid = arguments.get("uid").getAsString();

        try {
            JsonObject properties = objectService.getProperties(uid);

            JsonObject response = new JsonObject();
            response.addProperty("uid", uid);
            response.add("properties", properties);

            return response;

        } catch (TcException e) {
            throw new ToolExecutionException(ToolExecutionException.ERR_EXECUTION,
                    "Failed to retrieve Teamcenter object " + uid + ": " + e.getMessage(), e);
        }
    }
}

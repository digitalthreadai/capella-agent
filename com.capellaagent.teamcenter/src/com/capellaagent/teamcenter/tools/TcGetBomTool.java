package com.capellaagent.teamcenter.tools;

import com.capellaagent.core.tools.IToolDescriptor;
import com.capellaagent.core.tools.IToolExecutor;
import com.capellaagent.core.tools.ToolExecutionException;
import com.capellaagent.teamcenter.api.TcBomService;
import com.capellaagent.teamcenter.client.TcException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Tool: "tc_get_bom" -- Retrieves a Bill of Materials tree from Teamcenter.
 * <p>
 * Parameters:
 * <ul>
 *   <li>{@code uid} (string, required) - The UID of the top-level BOM line or Item Revision</li>
 *   <li>{@code depth} (integer, optional, default 3) - Maximum depth to expand</li>
 * </ul>
 */
public class TcGetBomTool implements IToolDescriptor, IToolExecutor {

    private static final String TOOL_NAME = "tc_get_bom";
    private static final int DEFAULT_DEPTH = 3;

    private final TcBomService bomService;

    /**
     * Constructs a new TcGetBomTool.
     *
     * @param bomService the Teamcenter BOM service
     */
    public TcGetBomTool(TcBomService bomService) {
        this.bomService = bomService;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Expand a Bill of Materials (BOM) tree from Teamcenter starting at a given "
                + "top-level UID. Returns a hierarchical structure of BOM lines with "
                + "name, type, quantity, and child components.";
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
        uidProp.addProperty("description", "The UID of the top-level BOM line or Item Revision");
        properties.add("uid", uidProp);

        JsonObject depthProp = new JsonObject();
        depthProp.addProperty("type", "integer");
        depthProp.addProperty("description", "Maximum depth to expand (default: 3)");
        depthProp.addProperty("default", DEFAULT_DEPTH);
        properties.add("depth", depthProp);

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
        int depth = arguments.has("depth") ? arguments.get("depth").getAsInt() : DEFAULT_DEPTH;

        try {
            JsonObject bomTree = bomService.expandBom(uid, depth);

            JsonObject response = new JsonObject();
            response.addProperty("topLineUid", uid);
            response.addProperty("expandedDepth", depth);
            response.add("bomTree", bomTree);

            return response;

        } catch (TcException e) {
            throw new ToolExecutionException(ToolExecutionException.ERR_EXECUTION,
                    "Failed to expand BOM for " + uid + ": " + e.getMessage(), e);
        }
    }
}

package com.capellaagent.teamcenter.tools;

import com.capellaagent.core.tools.IToolDescriptor;
import com.capellaagent.core.tools.IToolExecutor;
import com.capellaagent.core.tools.ToolExecutionException;
import com.capellaagent.teamcenter.api.TcObjectService;
import com.capellaagent.teamcenter.client.TcException;
import com.capellaagent.teamcenter.import_.PartImporter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Tool: "tc_import_part" -- Imports a Teamcenter part as a Capella PhysicalComponent.
 * <p>
 * Parameters:
 * <ul>
 *   <li>{@code tc_uid} (string, required) - The Teamcenter part/Item UID to import</li>
 *   <li>{@code target_layer} (string, optional, default "pa") - The Capella architecture layer</li>
 * </ul>
 */
public class TcImportPartTool implements IToolDescriptor, IToolExecutor {

    private static final String TOOL_NAME = "tc_import_part";
    private static final String DEFAULT_TARGET_LAYER = "pa";

    private final TcObjectService objectService;
    private final PartImporter importer;

    /**
     * Constructs a new TcImportPartTool.
     *
     * @param objectService the Teamcenter object service
     * @param importer      the part importer
     */
    public TcImportPartTool(TcObjectService objectService, PartImporter importer) {
        this.objectService = objectService;
        this.importer = importer;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Import a part (Item) from Teamcenter as a Capella PhysicalComponent. "
                + "Fetches the part data from Teamcenter, maps it to a Capella component, "
                + "and creates it in the specified architecture layer (defaults to Physical Architecture). "
                + "The Teamcenter UID and part number are stored for traceability.";
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

        JsonObject tcUidProp = new JsonObject();
        tcUidProp.addProperty("type", "string");
        tcUidProp.addProperty("description", "The Teamcenter part/Item UID to import");
        properties.add("tc_uid", tcUidProp);

        JsonObject layerProp = new JsonObject();
        layerProp.addProperty("type", "string");
        layerProp.addProperty("description",
                "The Capella architecture layer (default: 'pa' for Physical Architecture)");
        layerProp.addProperty("default", DEFAULT_TARGET_LAYER);
        properties.add("target_layer", layerProp);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("tc_uid");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) throws ToolExecutionException {
        String tcUid = getRequiredString(arguments, "tc_uid");
        String targetLayer = arguments.has("target_layer") && !arguments.get("target_layer").isJsonNull()
                ? arguments.get("target_layer").getAsString()
                : DEFAULT_TARGET_LAYER;

        try {
            // Fetch the part data from Teamcenter
            JsonObject tcPartData = objectService.getProperties(tcUid);

            // PLACEHOLDER: Get the active Sirius session.
            Object session = null;

            // Import the part into the Capella model
            JsonObject result = importer.importPart(tcPartData, targetLayer, session);

            return result;

        } catch (TcException e) {
            throw new ToolExecutionException(ToolExecutionException.ERR_EXECUTION,
                    "Failed to import part " + tcUid + ": " + e.getMessage(), e);
        }
    }

    private String getRequiredString(JsonObject args, String key) throws ToolExecutionException {
        if (!args.has(key) || args.get(key).isJsonNull()) {
            throw new ToolExecutionException(ToolExecutionException.ERR_INVALID_ARGS,
                    "Required parameter '" + key + "' is missing");
        }
        return args.get(key).getAsString();
    }
}

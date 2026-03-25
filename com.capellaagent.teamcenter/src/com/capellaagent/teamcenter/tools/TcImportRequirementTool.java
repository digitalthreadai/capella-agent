package com.capellaagent.teamcenter.tools;

import com.capellaagent.core.tools.IToolDescriptor;
import com.capellaagent.core.tools.IToolExecutor;
import com.capellaagent.core.tools.ToolExecutionException;
import com.capellaagent.teamcenter.api.TcRequirementsService;
import com.capellaagent.teamcenter.client.TcException;
import com.capellaagent.teamcenter.import_.RequirementImporter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Tool: "tc_import_requirement" -- Imports a Teamcenter requirement into the Capella model.
 * <p>
 * Parameters:
 * <ul>
 *   <li>{@code tc_uid} (string, required) - The Teamcenter requirement UID to import</li>
 *   <li>{@code target_layer} (string, required) - The Capella architecture layer ("oa", "sa", "la", "pa")</li>
 *   <li>{@code link_to_uuid} (string, optional) - UUID of a Capella element to link the requirement to</li>
 * </ul>
 */
public class TcImportRequirementTool implements IToolDescriptor, IToolExecutor {

    private static final String TOOL_NAME = "tc_import_requirement";

    private final TcRequirementsService reqService;
    private final RequirementImporter importer;

    /**
     * Constructs a new TcImportRequirementTool.
     *
     * @param reqService the Teamcenter requirements service
     * @param importer   the requirement importer
     */
    public TcImportRequirementTool(TcRequirementsService reqService, RequirementImporter importer) {
        this.reqService = reqService;
        this.importer = importer;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Import a requirement from Teamcenter into the active Capella model. "
                + "Fetches the requirement data from Teamcenter, maps it to a Capella "
                + "requirement element, and creates it in the specified architecture layer. "
                + "The Teamcenter UID is stored for traceability.";
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
        tcUidProp.addProperty("description", "The Teamcenter requirement UID to import");
        properties.add("tc_uid", tcUidProp);

        JsonObject layerProp = new JsonObject();
        layerProp.addProperty("type", "string");
        layerProp.addProperty("description",
                "The Capella architecture layer: 'oa' (Operational), 'sa' (System), "
                        + "'la' (Logical), 'pa' (Physical)");
        JsonArray layerEnum = new JsonArray();
        layerEnum.add("oa");
        layerEnum.add("sa");
        layerEnum.add("la");
        layerEnum.add("pa");
        layerProp.add("enum", layerEnum);
        properties.add("target_layer", layerProp);

        JsonObject linkProp = new JsonObject();
        linkProp.addProperty("type", "string");
        linkProp.addProperty("description",
                "Optional UUID of a Capella element to link the imported requirement to");
        properties.add("link_to_uuid", linkProp);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("tc_uid");
        required.add("target_layer");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) throws ToolExecutionException {
        String tcUid = getRequiredString(arguments, "tc_uid");
        String targetLayer = getRequiredString(arguments, "target_layer");
        String linkToUuid = arguments.has("link_to_uuid") && !arguments.get("link_to_uuid").isJsonNull()
                ? arguments.get("link_to_uuid").getAsString()
                : null;

        try {
            // Fetch the requirement data from Teamcenter
            JsonObject tcReqData = reqService.getRequirementSpec(tcUid);

            // PLACEHOLDER: Get the active Sirius session.
            // In a real implementation:
            //   Session session = SessionManager.INSTANCE.getSessions().iterator().next();
            Object session = null;

            // Import the requirement into the Capella model
            JsonObject result = importer.importRequirement(tcReqData, targetLayer, session);

            // If a link target was specified, add it to the result
            if (linkToUuid != null) {
                result.addProperty("linkedTo", linkToUuid);
                result.addProperty("linkNote",
                        "PLACEHOLDER: Trace link creation requires Capella model API access");
            }

            return result;

        } catch (TcException e) {
            throw new ToolExecutionException(ToolExecutionException.ERR_EXECUTION,
                    "Failed to import requirement " + tcUid + ": " + e.getMessage(), e);
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

package com.capellaagent.teamcenter.tools;

import com.capellaagent.core.tools.IToolDescriptor;
import com.capellaagent.core.tools.IToolExecutor;
import com.capellaagent.core.tools.ToolExecutionException;
import com.capellaagent.teamcenter.api.TcRequirementsService;
import com.capellaagent.teamcenter.api.TcSearchService;
import com.capellaagent.teamcenter.client.TcException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Tool: "tc_list_requirements" -- Lists requirements from Teamcenter.
 * <p>
 * Parameters:
 * <ul>
 *   <li>{@code spec_uid} (string, optional) - UID of a requirement specification to list children</li>
 *   <li>{@code query} (string, optional) - Search query to find requirements by text</li>
 * </ul>
 * At least one of {@code spec_uid} or {@code query} must be provided.
 */
public class TcListRequirementsTool implements IToolDescriptor, IToolExecutor {

    private static final String TOOL_NAME = "tc_list_requirements";

    private final TcRequirementsService reqService;

    /**
     * Constructs a new TcListRequirementsTool.
     *
     * @param reqService the Teamcenter requirements service
     */
    public TcListRequirementsTool(TcRequirementsService reqService) {
        this.reqService = reqService;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "List requirements from Teamcenter. Provide either a specification UID "
                + "to list its child requirements, or a search query to find requirements "
                + "by text content. Returns requirement UIDs, names, types, and status.";
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

        JsonObject specProp = new JsonObject();
        specProp.addProperty("type", "string");
        specProp.addProperty("description",
                "UID of a requirement specification to list its child requirements");
        properties.add("spec_uid", specProp);

        JsonObject queryProp = new JsonObject();
        queryProp.addProperty("type", "string");
        queryProp.addProperty("description", "Search query to find requirements by text");
        properties.add("query", queryProp);

        schema.add("properties", properties);

        // No required array -- at least one of spec_uid or query should be provided
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) throws ToolExecutionException {
        String specUid = arguments.has("spec_uid") && !arguments.get("spec_uid").isJsonNull()
                ? arguments.get("spec_uid").getAsString()
                : null;
        String query = arguments.has("query") && !arguments.get("query").isJsonNull()
                ? arguments.get("query").getAsString()
                : null;

        if (specUid == null && query == null) {
            throw new ToolExecutionException(ToolExecutionException.ERR_INVALID_ARGS,
                    "At least one of 'spec_uid' or 'query' must be provided");
        }

        try {
            JsonObject response = new JsonObject();

            if (specUid != null) {
                // List requirements under the given specification
                JsonObject spec = reqService.getRequirementSpec(specUid);
                JsonArray contents = reqService.getRequirementContents(specUid);

                response.addProperty("specUid", specUid);
                response.add("specification", spec);
                response.addProperty("requirementCount", contents.size());
                response.add("requirements", contents);
            } else {
                // Search for requirements by query
                // Delegate to requirement contents via the spec UID or perform a text search
                // PLACEHOLDER: This path would typically use TcSearchService with
                // objectType="Requirement". For now, return an empty result.
                response.addProperty("query", query);
                response.addProperty("requirementCount", 0);
                response.add("requirements", new JsonArray());
                response.addProperty("note",
                        "PLACEHOLDER: Text-based requirement search requires TcSearchService integration");
            }

            return response;

        } catch (TcException e) {
            throw new ToolExecutionException(ToolExecutionException.ERR_EXECUTION,
                    "Failed to list requirements: " + e.getMessage(), e);
        }
    }
}

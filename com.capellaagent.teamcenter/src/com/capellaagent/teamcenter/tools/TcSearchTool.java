package com.capellaagent.teamcenter.tools;

import com.capellaagent.core.tools.IToolDescriptor;
import com.capellaagent.core.tools.IToolExecutor;
import com.capellaagent.core.tools.ToolExecutionException;
import com.capellaagent.teamcenter.api.TcSearchService;
import com.capellaagent.teamcenter.client.TcException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Tool: "search_teamcenter" -- Searches for objects in Teamcenter.
 * <p>
 * Parameters:
 * <ul>
 *   <li>{@code query} (string, required) - The search query text</li>
 *   <li>{@code object_type} (string, optional) - Filter by Tc object type (e.g., "Item", "Requirement")</li>
 *   <li>{@code max_results} (integer, optional, default 25) - Maximum results to return</li>
 * </ul>
 */
public class TcSearchTool implements IToolDescriptor, IToolExecutor {

    private static final String TOOL_NAME = "search_teamcenter";
    private static final int DEFAULT_MAX_RESULTS = 25;

    private final TcSearchService searchService;

    /**
     * Constructs a new TcSearchTool.
     *
     * @param searchService the Teamcenter search service
     */
    public TcSearchTool(TcSearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Search for objects in Teamcenter PLM by query string. "
                + "Optionally filter by object type (e.g., Item, ItemRevision, Requirement). "
                + "Returns a list of matching objects with their UIDs, names, and types.";
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

        JsonObject queryProp = new JsonObject();
        queryProp.addProperty("type", "string");
        queryProp.addProperty("description", "The search query text");
        properties.add("query", queryProp);

        JsonObject typeProp = new JsonObject();
        typeProp.addProperty("type", "string");
        typeProp.addProperty("description",
                "Optional Teamcenter object type filter (e.g., 'Item', 'ItemRevision', 'Requirement')");
        properties.add("object_type", typeProp);

        JsonObject maxProp = new JsonObject();
        maxProp.addProperty("type", "integer");
        maxProp.addProperty("description", "Maximum number of results to return (default: 25)");
        maxProp.addProperty("default", DEFAULT_MAX_RESULTS);
        properties.add("max_results", maxProp);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("query");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) throws ToolExecutionException {
        String query = getRequiredString(arguments, "query");
        String objectType = arguments.has("object_type")
                ? arguments.get("object_type").getAsString()
                : null;
        int maxResults = arguments.has("max_results")
                ? arguments.get("max_results").getAsInt()
                : DEFAULT_MAX_RESULTS;

        try {
            JsonArray results = searchService.search(query, objectType, maxResults);

            JsonObject response = new JsonObject();
            response.addProperty("query", query);
            response.addProperty("resultCount", results.size());
            response.add("results", results);

            if (objectType != null) {
                response.addProperty("objectTypeFilter", objectType);
            }

            return response;

        } catch (TcException e) {
            throw new ToolExecutionException(ToolExecutionException.ERR_EXECUTION,
                    "Teamcenter search failed: " + e.getMessage(), e);
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

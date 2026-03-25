package com.capellaagent.teamcenter.api;

import com.capellaagent.teamcenter.client.TcException;
import com.capellaagent.teamcenter.client.TcRestClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Service for expanding Bill of Materials (BOM) structures in Teamcenter.
 * <p>
 * Retrieves hierarchical BOM data starting from a top-level BOM line,
 * expanding child components to the specified depth.
 */
public class TcBomService {

    /**
     * PLACEHOLDER: Actual Teamcenter BOM expansion endpoint.
     * May be StructureManagement-BomManagementService or similar.
     */
    private static final String EXPAND_BOM_ENDPOINT =
            "/tc/soa/StructureManagement-BomManagementService/expandBOM";

    private final TcRestClient restClient;

    /**
     * Constructs a new TcBomService.
     *
     * @param restClient the authenticated REST client
     */
    public TcBomService(TcRestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Expands a BOM starting from the given top-line UID to the specified depth.
     *
     * @param topLineUid the UID of the top-level BOM line or Item Revision
     * @param depth      the maximum depth to expand (1 = immediate children only)
     * @return a JsonObject representing the BOM tree with the following structure:
     *         <pre>{@code
     *         {
     *           "uid": "...",
     *           "name": "...",
     *           "type": "...",
     *           "quantity": 1,
     *           "children": [ { ... }, { ... } ]
     *         }
     *         }</pre>
     * @throws TcException if the BOM expansion request fails
     */
    public JsonObject expandBom(String topLineUid, int depth) throws TcException {
        // PLACEHOLDER: Build request body per Teamcenter SOA format
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("topLineUid", topLineUid);
        requestBody.addProperty("depth", depth);

        JsonObject expandOptions = new JsonObject();
        expandOptions.addProperty("includeQuantity", true);
        expandOptions.addProperty("includeOccurrenceProperties", true);
        requestBody.add("expandOptions", expandOptions);

        JsonObject response = restClient.post(EXPAND_BOM_ENDPOINT, requestBody);

        // PLACEHOLDER: Transform the raw Teamcenter BOM response into our tree format.
        // The actual response structure depends on the Teamcenter API version.
        if (response.has("bomTree")) {
            return response.getAsJsonObject("bomTree");
        }

        // Build a minimal tree from the response
        return buildTreeFromResponse(response, topLineUid, depth);
    }

    /**
     * Builds a hierarchical tree from a flat Teamcenter BOM response.
     * <p>
     * PLACEHOLDER: The actual transformation logic depends on the Teamcenter
     * response format. This is a skeleton implementation.
     */
    private JsonObject buildTreeFromResponse(JsonObject response, String rootUid, int depth) {
        JsonObject root = new JsonObject();
        root.addProperty("uid", rootUid);
        root.addProperty("name", "");
        root.addProperty("type", "");
        root.addProperty("quantity", 1);
        root.add("children", new JsonArray());

        // PLACEHOLDER: Populate tree from actual response structure.
        // Typical Teamcenter responses provide a flat list of BOM lines
        // with parent-child relationships via occurrence UIDs.

        if (response.has("bomLines")) {
            JsonArray bomLines = response.getAsJsonArray("bomLines");
            // Process BOM lines and build tree recursively
            for (int i = 0; i < bomLines.size() && i < 100; i++) {
                JsonObject line = bomLines.get(i).getAsJsonObject();
                JsonObject child = new JsonObject();
                child.addProperty("uid", line.has("uid") ? line.get("uid").getAsString() : "");
                child.addProperty("name", line.has("name") ? line.get("name").getAsString() : "");
                child.addProperty("type", line.has("type") ? line.get("type").getAsString() : "");
                child.addProperty("quantity",
                        line.has("quantity") ? line.get("quantity").getAsInt() : 1);
                child.add("children", new JsonArray());
                root.getAsJsonArray("children").add(child);
            }
        }

        return root;
    }
}

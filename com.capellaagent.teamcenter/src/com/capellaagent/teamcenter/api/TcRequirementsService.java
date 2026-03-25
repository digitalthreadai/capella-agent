package com.capellaagent.teamcenter.api;

import com.capellaagent.teamcenter.client.TcException;
import com.capellaagent.teamcenter.client.TcRestClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Service for accessing Teamcenter Requirements Management data.
 * <p>
 * Provides methods to retrieve requirement specifications and their
 * child requirements, supporting import workflows into Capella.
 */
public class TcRequirementsService {

    /**
     * PLACEHOLDER: Actual Teamcenter endpoint for requirement spec retrieval.
     */
    private static final String GET_REQ_SPEC_ENDPOINT =
            "/tc/soa/Internal-RequirementsManagement/getSpecification";

    /**
     * PLACEHOLDER: Actual Teamcenter endpoint for requirement contents.
     */
    private static final String GET_REQ_CONTENTS_ENDPOINT =
            "/tc/soa/Internal-RequirementsManagement/getRequirementContents";

    private final TcRestClient restClient;

    /**
     * Constructs a new TcRequirementsService.
     *
     * @param restClient the authenticated REST client
     */
    public TcRequirementsService(TcRestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Retrieves a Requirement Specification by its UID.
     *
     * @param uid the UID of the requirement specification
     * @return a JsonObject containing the specification metadata, including
     *         name, description, type, status, and child requirement UIDs
     * @throws TcException if the request fails or the specification is not found
     */
    public JsonObject getRequirementSpec(String uid) throws TcException {
        // PLACEHOLDER: Build request per Teamcenter SOA format
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("specificationUid", uid);

        JsonObject options = new JsonObject();
        options.addProperty("includeChildren", true);
        options.addProperty("includeContent", false);
        requestBody.add("options", options);

        JsonObject response = restClient.post(GET_REQ_SPEC_ENDPOINT, requestBody);

        // PLACEHOLDER: Extract specification object from response
        if (response.has("specification")) {
            return response.getAsJsonObject("specification");
        }
        return response;
    }

    /**
     * Retrieves the contents (child requirements) of a requirement specification.
     *
     * @param specUid the UID of the parent requirement specification
     * @return a JsonArray of requirement objects, each containing at minimum
     *         "uid", "name", "requirementText", "type", and "status"
     * @throws TcException if the request fails
     */
    public JsonArray getRequirementContents(String specUid) throws TcException {
        // PLACEHOLDER: Build request per Teamcenter SOA format
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("specificationUid", specUid);

        JsonObject options = new JsonObject();
        options.addProperty("includeRichText", true);
        options.addProperty("maxDepth", -1); // all levels
        requestBody.add("options", options);

        JsonObject response = restClient.post(GET_REQ_CONTENTS_ENDPOINT, requestBody);

        // PLACEHOLDER: Extract requirements array from response
        if (response.has("requirements")) {
            return response.getAsJsonArray("requirements");
        }

        return new JsonArray();
    }
}

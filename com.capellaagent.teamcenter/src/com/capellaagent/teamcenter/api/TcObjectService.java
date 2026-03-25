package com.capellaagent.teamcenter.api;

import java.util.Map;

import com.capellaagent.teamcenter.client.TcException;
import com.capellaagent.teamcenter.client.TcRestClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Service for retrieving Teamcenter object properties and revisions.
 * <p>
 * Provides methods to fetch the full property set of a Teamcenter managed object
 * by its UID, and to list all revisions of an Item.
 */
public class TcObjectService {

    /**
     * PLACEHOLDER: Actual Teamcenter endpoint for getting object properties.
     */
    private static final String GET_PROPERTIES_ENDPOINT = "/tc/soa/Core-DataManagementService/getProperties";

    /**
     * PLACEHOLDER: Actual Teamcenter endpoint for getting item revisions.
     */
    private static final String GET_REVISIONS_ENDPOINT = "/tc/soa/Core-DataManagementService/getItemRevisions";

    private final TcRestClient restClient;

    /**
     * Constructs a new TcObjectService.
     *
     * @param restClient the authenticated REST client
     */
    public TcObjectService(TcRestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Retrieves all properties of a Teamcenter object by its UID.
     *
     * @param uid the unique identifier of the Teamcenter object
     * @return a JsonObject containing the object's properties as key-value pairs
     * @throws TcException if the request fails or the object is not found
     */
    public JsonObject getProperties(String uid) throws TcException {
        // PLACEHOLDER: Build request body per Teamcenter SOA format
        JsonObject requestBody = new JsonObject();
        JsonArray uids = new JsonArray();
        uids.add(uid);
        requestBody.add("uids", uids);

        // PLACEHOLDER: Property names to retrieve; empty means all properties
        JsonArray propertyNames = new JsonArray();
        requestBody.add("propertyNames", propertyNames);

        JsonObject response = restClient.post(GET_PROPERTIES_ENDPOINT, requestBody);

        // PLACEHOLDER: Extract the object properties from the response.
        // Typically the response contains a "modelObjects" or "properties" map keyed by UID.
        if (response.has("modelObjects") && response.getAsJsonObject("modelObjects").has(uid)) {
            return response.getAsJsonObject("modelObjects").getAsJsonObject(uid);
        }

        // Fallback: return the full response if the expected structure is not found
        return response;
    }

    /**
     * Retrieves all revisions of a Teamcenter Item.
     *
     * @param uid the UID of the Teamcenter Item (not a revision)
     * @return a JsonArray of revision objects, each with "uid", "revisionId", and properties
     * @throws TcException if the request fails
     */
    public JsonArray getRevisions(String uid) throws TcException {
        // PLACEHOLDER: Build request body per Teamcenter SOA format
        JsonObject requestBody = new JsonObject();
        JsonArray uids = new JsonArray();
        uids.add(uid);
        requestBody.add("itemUids", uids);

        JsonObject response = restClient.post(GET_REVISIONS_ENDPOINT, requestBody);

        // PLACEHOLDER: Extract revisions from response
        if (response.has("revisions")) {
            return response.getAsJsonArray("revisions");
        }

        return new JsonArray();
    }
}

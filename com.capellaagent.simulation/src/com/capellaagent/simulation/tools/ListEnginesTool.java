package com.capellaagent.simulation.tools;

import java.util.Map;

import com.capellaagent.core.tools.IToolDescriptor;
import com.capellaagent.core.tools.IToolExecutor;
import com.capellaagent.core.tools.ToolExecutionException;
import com.capellaagent.simulation.bridge.ISimulationEngine;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Tool: "list_simulation_engines" -- Lists all available simulation engines.
 * <p>
 * No parameters required.
 */
public class ListEnginesTool implements IToolDescriptor, IToolExecutor {

    private static final String TOOL_NAME = "list_simulation_engines";

    private final Map<String, ISimulationEngine> engineRegistry;

    /**
     * Constructs a new ListEnginesTool.
     *
     * @param engineRegistry the map of registered simulation engines
     */
    public ListEnginesTool(Map<String, ISimulationEngine> engineRegistry) {
        this.engineRegistry = engineRegistry;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "List all registered simulation engines with their availability status. "
                + "Returns engine IDs, display names, and whether they are currently available "
                + "for use (installed, licensed, and accessible).";
    }

    @Override
    public String getCategory() {
        return "simulation";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) throws ToolExecutionException {
        JsonArray engines = new JsonArray();

        for (Map.Entry<String, ISimulationEngine> entry : engineRegistry.entrySet()) {
            ISimulationEngine engine = entry.getValue();
            JsonObject engineObj = new JsonObject();
            engineObj.addProperty("id", engine.getId());
            engineObj.addProperty("displayName", engine.getDisplayName());
            engineObj.addProperty("available", engine.isAvailable());
            engines.add(engineObj);
        }

        JsonObject response = new JsonObject();
        response.addProperty("engineCount", engines.size());
        response.add("engines", engines);

        return response;
    }
}

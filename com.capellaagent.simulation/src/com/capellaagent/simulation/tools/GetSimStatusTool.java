package com.capellaagent.simulation.tools;

import com.capellaagent.core.tools.IToolDescriptor;
import com.capellaagent.core.tools.IToolExecutor;
import com.capellaagent.core.tools.ToolExecutionException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Tool: "get_simulation_status" -- Checks the status of a simulation job.
 * <p>
 * Parameters:
 * <ul>
 *   <li>{@code simulation_id} (string, required) - The simulation run ID to check</li>
 * </ul>
 */
public class GetSimStatusTool implements IToolDescriptor, IToolExecutor {

    private static final String TOOL_NAME = "get_simulation_status";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Check the status of a running or completed simulation job. "
                + "Returns the current status, progress percentage, and any available results.";
    }

    @Override
    public String getCategory() {
        return "simulation";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject simIdProp = new JsonObject();
        simIdProp.addProperty("type", "string");
        simIdProp.addProperty("description", "The simulation run ID to check");
        properties.add("simulation_id", simIdProp);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("simulation_id");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) throws ToolExecutionException {
        if (!arguments.has("simulation_id") || arguments.get("simulation_id").isJsonNull()) {
            throw new ToolExecutionException(ToolExecutionException.ERR_INVALID_ARGS,
                    "Required parameter 'simulation_id' is missing");
        }

        String simulationId = arguments.get("simulation_id").getAsString();

        // PLACEHOLDER: Look up the simulation job status from a job registry.
        // In a real implementation, an async simulation manager would track
        // running and completed jobs.

        JsonObject response = new JsonObject();
        response.addProperty("simulationId", simulationId);
        response.addProperty("status", "UNKNOWN");
        response.addProperty("message",
                "PLACEHOLDER: Simulation job tracking requires an async job manager. "
                        + "Currently, simulations run synchronously via run_simulation.");

        return response;
    }
}

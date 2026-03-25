package com.capellaagent.simulation.tools;

import com.capellaagent.core.tools.IToolDescriptor;
import com.capellaagent.core.tools.IToolExecutor;
import com.capellaagent.core.tools.ToolExecutionException;
import com.capellaagent.simulation.orchestrator.ResultPropagator;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Tool: "propagate_simulation_results" -- Manually propagates simulation results to the model.
 * <p>
 * Parameters:
 * <ul>
 *   <li>{@code simulation_id} (string, required) - The simulation run ID to propagate</li>
 * </ul>
 */
public class PropagateResultsTool implements IToolDescriptor, IToolExecutor {

    private static final String TOOL_NAME = "propagate_simulation_results";

    private final ResultPropagator propagator;

    /**
     * Constructs a new PropagateResultsTool.
     *
     * @param propagator the result propagator
     */
    public PropagateResultsTool(ResultPropagator propagator) {
        this.propagator = propagator;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Manually propagate results from a completed simulation run back to the "
                + "Capella model. Use this when auto_propagate was set to false in the "
                + "original run_simulation call, or to re-propagate results.";
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
        simIdProp.addProperty("description", "The simulation run ID to propagate results from");
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

        boolean success = propagator.propagateById(simulationId);

        JsonObject response = new JsonObject();
        response.addProperty("simulationId", simulationId);
        response.addProperty("propagated", success);

        if (!success) {
            response.addProperty("message",
                    "Simulation results not found for ID: " + simulationId
                            + ". The results may have expired or the simulation may not have completed.");
        } else {
            response.addProperty("message", "Results successfully propagated to the Capella model");
        }

        return response;
    }
}

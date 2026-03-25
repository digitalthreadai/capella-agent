package com.capellaagent.simulation.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.NullProgressMonitor;

import com.capellaagent.core.tools.IToolDescriptor;
import com.capellaagent.core.tools.IToolExecutor;
import com.capellaagent.core.tools.ToolExecutionException;
import com.capellaagent.simulation.bridge.ISimulationEngine;
import com.capellaagent.simulation.bridge.SimulationEngineException;
import com.capellaagent.simulation.bridge.SimulationResult;
import com.capellaagent.simulation.orchestrator.ParameterMapping;
import com.capellaagent.simulation.orchestrator.SimulationConfig;
import com.capellaagent.simulation.orchestrator.SimulationOrchestrator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Tool: "run_simulation" -- Runs a simulation synchronously.
 * <p>
 * Parameters:
 * <ul>
 *   <li>{@code engine_id} (string, required) - The simulation engine to use</li>
 *   <li>{@code model_path} (string, required) - Path to the simulation model file</li>
 *   <li>{@code element_uuids} (array of strings, required) - Capella element UUIDs for parameters</li>
 *   <li>{@code auto_propagate} (boolean, optional, default true) - Whether to auto-propagate results</li>
 * </ul>
 */
public class RunSimulationTool implements IToolDescriptor, IToolExecutor {

    private static final String TOOL_NAME = "run_simulation";

    private final SimulationOrchestrator orchestrator;
    private final Map<String, ISimulationEngine> engineRegistry;

    /**
     * Constructs a new RunSimulationTool.
     *
     * @param orchestrator   the simulation orchestrator
     * @param engineRegistry the map of available engines
     */
    public RunSimulationTool(SimulationOrchestrator orchestrator,
                             Map<String, ISimulationEngine> engineRegistry) {
        this.orchestrator = orchestrator;
        this.engineRegistry = engineRegistry;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Run a simulation synchronously using the specified engine and model. "
                + "Extracts parameters from the given Capella model elements, executes "
                + "the simulation, and optionally propagates results back to the model. "
                + "Returns the simulation status, outputs, timing, and logs.";
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

        JsonObject engineProp = new JsonObject();
        engineProp.addProperty("type", "string");
        engineProp.addProperty("description",
                "The simulation engine ID (use list_simulation_engines to see available engines)");
        properties.add("engine_id", engineProp);

        JsonObject modelProp = new JsonObject();
        modelProp.addProperty("type", "string");
        modelProp.addProperty("description", "File system path to the simulation model");
        properties.add("model_path", modelProp);

        JsonObject uuidsProp = new JsonObject();
        uuidsProp.addProperty("type", "array");
        JsonObject itemSchema = new JsonObject();
        itemSchema.addProperty("type", "string");
        uuidsProp.add("items", itemSchema);
        uuidsProp.addProperty("description",
                "UUIDs of Capella elements to extract simulation parameters from");
        properties.add("element_uuids", uuidsProp);

        JsonObject autoProp = new JsonObject();
        autoProp.addProperty("type", "boolean");
        autoProp.addProperty("description",
                "Whether to automatically propagate results back to the Capella model (default: true)");
        autoProp.addProperty("default", true);
        properties.add("auto_propagate", autoProp);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("engine_id");
        required.add("model_path");
        required.add("element_uuids");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) throws ToolExecutionException {
        String engineId = getRequiredString(arguments, "engine_id");
        String modelPath = getRequiredString(arguments, "model_path");
        boolean autoPropagate = !arguments.has("auto_propagate")
                || arguments.get("auto_propagate").getAsBoolean();

        if (!arguments.has("element_uuids") || !arguments.get("element_uuids").isJsonArray()) {
            throw new ToolExecutionException(ToolExecutionException.ERR_INVALID_ARGS,
                    "Required parameter 'element_uuids' must be an array");
        }

        // Validate engine exists
        if (!engineRegistry.containsKey(engineId)) {
            throw new ToolExecutionException(ToolExecutionException.ERR_INVALID_ARGS,
                    "Unknown engine: '" + engineId + "'. Available: " + engineRegistry.keySet());
        }

        // Build simulation config
        SimulationConfig config = new SimulationConfig();
        config.setEngineId(engineId);
        config.setModelPath(modelPath);

        JsonArray uuidsArray = arguments.getAsJsonArray("element_uuids");
        List<ParameterMapping> inputMappings = new ArrayList<>();
        for (JsonElement elem : uuidsArray) {
            String uuid = elem.getAsString();
            inputMappings.add(new ParameterMapping(
                    "param_" + uuid.substring(0, Math.min(8, uuid.length())),
                    uuid, "ownedPropertyValues", "double"));
        }
        config.setInputMappings(inputMappings);

        // PLACEHOLDER: Get the active Sirius session
        Object session = null;

        try {
            SimulationResult result = orchestrator.execute(config, session, new NullProgressMonitor());

            return buildResponse(result, autoPropagate);

        } catch (SimulationEngineException e) {
            throw new ToolExecutionException(ToolExecutionException.ERR_EXECUTION,
                    "Simulation failed: " + e.getMessage(), e);
        }
    }

    private JsonObject buildResponse(SimulationResult result, boolean autoPropagate) {
        JsonObject response = new JsonObject();
        response.addProperty("simulationId", result.getSimulationId());
        response.addProperty("status", result.getStatus().name());
        response.addProperty("durationMs", result.getDurationMs());

        if (result.getErrorMessage() != null) {
            response.addProperty("error", result.getErrorMessage());
        }

        // Outputs
        JsonObject outputs = new JsonObject();
        for (Map.Entry<String, Object> entry : result.getOutputs().entrySet()) {
            outputs.addProperty(entry.getKey(), String.valueOf(entry.getValue()));
        }
        response.add("outputs", outputs);

        // Inputs used
        JsonObject inputs = new JsonObject();
        for (Map.Entry<String, Object> entry : result.getInputs().entrySet()) {
            inputs.addProperty(entry.getKey(), String.valueOf(entry.getValue()));
        }
        response.add("inputs", inputs);

        // Logs
        JsonArray logs = new JsonArray();
        for (String log : result.getLogs()) {
            logs.add(log);
        }
        response.add("logs", logs);

        response.addProperty("autoPropagate", autoPropagate);

        return response;
    }

    private String getRequiredString(JsonObject args, String key) throws ToolExecutionException {
        if (!args.has(key) || args.get(key).isJsonNull()) {
            throw new ToolExecutionException(ToolExecutionException.ERR_INVALID_ARGS,
                    "Required parameter '" + key + "' is missing");
        }
        return args.get(key).getAsString();
    }
}

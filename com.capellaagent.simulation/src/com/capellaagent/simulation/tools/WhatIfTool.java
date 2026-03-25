package com.capellaagent.simulation.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.NullProgressMonitor;

import com.capellaagent.core.tools.IToolDescriptor;
import com.capellaagent.core.tools.IToolExecutor;
import com.capellaagent.core.tools.ToolExecutionException;
import com.capellaagent.simulation.bridge.ISimulationEngine;
import com.capellaagent.simulation.bridge.SimulationResult;
import com.capellaagent.simulation.orchestrator.ParameterMapping;
import com.capellaagent.simulation.orchestrator.SimulationConfig;
import com.capellaagent.simulation.orchestrator.WhatIfManager;
import com.capellaagent.simulation.orchestrator.WhatIfSpec;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Tool: "run_what_if" -- Runs a parameter sweep (what-if analysis).
 * <p>
 * Parameters:
 * <ul>
 *   <li>{@code engine_id} (string, required) - The simulation engine to use</li>
 *   <li>{@code model_path} (string, required) - Path to the simulation model</li>
 *   <li>{@code element_uuids} (array of strings, required) - Capella element UUIDs</li>
 *   <li>{@code parameter_ranges} (object, required) - Parameter name to array of values</li>
 * </ul>
 */
public class WhatIfTool implements IToolDescriptor, IToolExecutor {

    private static final String TOOL_NAME = "run_what_if";

    private final WhatIfManager whatIfManager;
    private final Map<String, ISimulationEngine> engineRegistry;

    /**
     * Constructs a new WhatIfTool.
     *
     * @param whatIfManager  the what-if analysis manager
     * @param engineRegistry the map of available engines
     */
    public WhatIfTool(WhatIfManager whatIfManager, Map<String, ISimulationEngine> engineRegistry) {
        this.whatIfManager = whatIfManager;
        this.engineRegistry = engineRegistry;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Run a what-if parameter sweep analysis. Varies the specified parameters "
                + "across given value ranges and runs the simulation for each combination. "
                + "Returns all results for comparison. Useful for sensitivity analysis, "
                + "design space exploration, and trade studies.";
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
        engineProp.addProperty("description", "The simulation engine ID");
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
        uuidsProp.addProperty("description", "Capella element UUIDs for base parameters");
        properties.add("element_uuids", uuidsProp);

        JsonObject rangesProp = new JsonObject();
        rangesProp.addProperty("type", "object");
        rangesProp.addProperty("description",
                "Map of parameter name to array of numeric values to sweep. "
                        + "Example: {\"speed\": [10, 20, 30], \"mass\": [100, 200]}");
        JsonObject additionalProps = new JsonObject();
        additionalProps.addProperty("type", "array");
        JsonObject numItem = new JsonObject();
        numItem.addProperty("type", "number");
        additionalProps.add("items", numItem);
        rangesProp.add("additionalProperties", additionalProps);
        properties.add("parameter_ranges", rangesProp);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("engine_id");
        required.add("model_path");
        required.add("element_uuids");
        required.add("parameter_ranges");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) throws ToolExecutionException {
        String engineId = getRequiredString(arguments, "engine_id");
        String modelPath = getRequiredString(arguments, "model_path");

        if (!engineRegistry.containsKey(engineId)) {
            throw new ToolExecutionException(ToolExecutionException.ERR_INVALID_ARGS,
                    "Unknown engine: '" + engineId + "'. Available: " + engineRegistry.keySet());
        }

        if (!arguments.has("element_uuids") || !arguments.get("element_uuids").isJsonArray()) {
            throw new ToolExecutionException(ToolExecutionException.ERR_INVALID_ARGS,
                    "Required parameter 'element_uuids' must be an array");
        }

        if (!arguments.has("parameter_ranges") || !arguments.get("parameter_ranges").isJsonObject()) {
            throw new ToolExecutionException(ToolExecutionException.ERR_INVALID_ARGS,
                    "Required parameter 'parameter_ranges' must be an object");
        }

        // Build base config
        SimulationConfig baseConfig = new SimulationConfig();
        baseConfig.setEngineId(engineId);
        baseConfig.setModelPath(modelPath);

        JsonArray uuidsArray = arguments.getAsJsonArray("element_uuids");
        List<ParameterMapping> inputMappings = new ArrayList<>();
        for (JsonElement elem : uuidsArray) {
            String uuid = elem.getAsString();
            inputMappings.add(new ParameterMapping(
                    "param_" + uuid.substring(0, Math.min(8, uuid.length())),
                    uuid, "ownedPropertyValues", "double"));
        }
        baseConfig.setInputMappings(inputMappings);

        // Parse parameter ranges
        WhatIfSpec spec = new WhatIfSpec();
        spec.setBaseConfig(baseConfig);

        JsonObject ranges = arguments.getAsJsonObject("parameter_ranges");
        for (String paramName : ranges.keySet()) {
            JsonArray values = ranges.getAsJsonArray(paramName);
            double[] valueArray = new double[values.size()];
            for (int i = 0; i < values.size(); i++) {
                valueArray[i] = values.get(i).getAsDouble();
            }
            spec.getParameterRanges().put(paramName, valueArray);
        }

        // PLACEHOLDER: Get the active Sirius session
        Object session = null;

        try {
            List<SimulationResult> results = whatIfManager.runSweep(
                    spec, session, new NullProgressMonitor());

            return buildResponse(spec, results);

        } catch (Exception e) {
            throw new ToolExecutionException(ToolExecutionException.ERR_EXECUTION,
                    "What-if analysis failed: " + e.getMessage(), e);
        }
    }

    private JsonObject buildResponse(WhatIfSpec spec, List<SimulationResult> results) {
        JsonObject response = new JsonObject();
        response.addProperty("totalCombinations", spec.getTotalCombinations());
        response.addProperty("completedRuns", results.size());

        long successCount = results.stream()
                .filter(SimulationResult::isSuccess).count();
        response.addProperty("successfulRuns", successCount);
        response.addProperty("failedRuns", results.size() - successCount);

        JsonArray runsArray = new JsonArray();
        for (int i = 0; i < results.size(); i++) {
            SimulationResult result = results.get(i);
            JsonObject run = new JsonObject();
            run.addProperty("runIndex", i);
            run.addProperty("status", result.getStatus().name());
            run.addProperty("durationMs", result.getDurationMs());

            // Inputs used
            JsonObject inputs = new JsonObject();
            for (Map.Entry<String, Object> entry : result.getInputs().entrySet()) {
                inputs.addProperty(entry.getKey(), String.valueOf(entry.getValue()));
            }
            run.add("inputs", inputs);

            // Outputs
            JsonObject outputs = new JsonObject();
            for (Map.Entry<String, Object> entry : result.getOutputs().entrySet()) {
                outputs.addProperty(entry.getKey(), String.valueOf(entry.getValue()));
            }
            run.add("outputs", outputs);

            if (result.getErrorMessage() != null) {
                run.addProperty("error", result.getErrorMessage());
            }

            runsArray.add(run);
        }
        response.add("runs", runsArray);

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

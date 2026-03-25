package com.capellaagent.simulation.orchestrator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubMonitor;

import com.capellaagent.simulation.bridge.SimulationEngineException;
import com.capellaagent.simulation.bridge.SimulationResult;

/**
 * Manages what-if analysis by running parameter sweeps through the simulation orchestrator.
 * <p>
 * Generates all combinations of parameter values from a {@link WhatIfSpec} and
 * executes each combination as an independent simulation run. Results are collected
 * and returned as a list for comparison and analysis.
 *
 * <h3>Combination Generation</h3>
 * For a spec with parameters:
 * <pre>
 *   speed: [10, 20, 30]
 *   mass:  [100, 200]
 * </pre>
 * The manager generates 6 combinations:
 * <pre>
 *   {speed=10, mass=100}, {speed=10, mass=200},
 *   {speed=20, mass=100}, {speed=20, mass=200},
 *   {speed=30, mass=100}, {speed=30, mass=200}
 * </pre>
 */
public class WhatIfManager {

    private final SimulationOrchestrator orchestrator;

    /**
     * Constructs a new WhatIfManager.
     *
     * @param orchestrator the simulation orchestrator
     */
    public WhatIfManager(SimulationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Runs a parameter sweep as defined by the what-if specification.
     *
     * @param spec    the what-if specification containing parameter ranges
     * @param session the Sirius session
     * @param monitor a progress monitor
     * @return a list of simulation results, one per parameter combination
     */
    public List<SimulationResult> runSweep(WhatIfSpec spec, Object session,
                                            IProgressMonitor monitor) {
        if (!spec.isValid()) {
            throw new IllegalArgumentException(
                    "Invalid WhatIfSpec: requires a valid base config and at least one parameter range");
        }

        List<Map<String, Double>> combinations = generateCombinations(spec.getParameterRanges());
        int totalRuns = combinations.size();

        Platform.getLog(getClass()).info(
                "Starting what-if sweep with " + totalRuns + " parameter combinations");

        SubMonitor subMonitor = SubMonitor.convert(monitor,
                "What-If Analysis (" + totalRuns + " runs)", totalRuns);
        List<SimulationResult> results = new ArrayList<>();

        for (int i = 0; i < combinations.size(); i++) {
            if (subMonitor.isCanceled()) {
                Platform.getLog(getClass()).info(
                        "What-if sweep cancelled after " + i + "/" + totalRuns + " runs");
                break;
            }

            Map<String, Double> paramValues = combinations.get(i);
            subMonitor.subTask("Run " + (i + 1) + "/" + totalRuns + ": " + paramValues);

            try {
                // Create a modified config with the current parameter values
                SimulationConfig runConfig = createModifiedConfig(spec.getBaseConfig(), paramValues);

                SimulationResult result = orchestrator.execute(
                        runConfig, session, subMonitor.split(1));

                // Tag the result with the parameter values used
                for (Map.Entry<String, Double> entry : paramValues.entrySet()) {
                    result.getInputs().put(entry.getKey(), entry.getValue());
                }

                results.add(result);

                Platform.getLog(getClass()).info(
                        "What-if run " + (i + 1) + "/" + totalRuns + " completed: "
                                + result.getStatus());

            } catch (SimulationEngineException e) {
                SimulationResult failedResult = new SimulationResult();
                failedResult.setStatus(SimulationResult.Status.FAILED);
                failedResult.setErrorMessage(e.getMessage());
                for (Map.Entry<String, Double> entry : paramValues.entrySet()) {
                    failedResult.getInputs().put(entry.getKey(), entry.getValue());
                }
                results.add(failedResult);

                Platform.getLog(getClass()).warn(
                        "What-if run " + (i + 1) + " failed: " + e.getMessage());
            }
        }

        Platform.getLog(getClass()).info(
                "What-if sweep completed: " + results.size() + " runs executed");

        return results;
    }

    /**
     * Generates all combinations of parameter values using a Cartesian product.
     *
     * @param parameterRanges map of parameter name to array of values
     * @return list of maps, each representing one parameter combination
     */
    List<Map<String, Double>> generateCombinations(Map<String, double[]> parameterRanges) {
        List<Map<String, Double>> combinations = new ArrayList<>();
        List<String> paramNames = new ArrayList<>(parameterRanges.keySet());

        generateCombinationsRecursive(parameterRanges, paramNames, 0, new HashMap<>(), combinations);

        return combinations;
    }

    private void generateCombinationsRecursive(
            Map<String, double[]> ranges,
            List<String> paramNames,
            int index,
            Map<String, Double> current,
            List<Map<String, Double>> results) {

        if (index >= paramNames.size()) {
            results.add(new HashMap<>(current));
            return;
        }

        String paramName = paramNames.get(index);
        double[] values = ranges.get(paramName);

        for (double value : values) {
            current.put(paramName, value);
            generateCombinationsRecursive(ranges, paramNames, index + 1, current, results);
        }

        current.remove(paramName);
    }

    /**
     * Creates a copy of the base config with overridden parameter values.
     */
    private SimulationConfig createModifiedConfig(SimulationConfig base,
                                                   Map<String, Double> paramValues) {
        SimulationConfig modified = new SimulationConfig();
        modified.setEngineId(base.getEngineId());
        modified.setModelPath(base.getModelPath());
        modified.setInputMappings(base.getInputMappings());
        modified.setOutputMappings(base.getOutputMappings());

        // Merge engine options with parameter overrides
        Map<String, Object> options = new HashMap<>(base.getEngineOptions());
        options.putAll(paramValues);
        modified.setEngineOptions(options);

        return modified;
    }
}

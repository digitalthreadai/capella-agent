package com.capellaagent.simulation.orchestrator;

import java.util.Map;
import java.util.Objects;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubMonitor;

import com.capellaagent.simulation.bridge.ISimulationEngine;
import com.capellaagent.simulation.bridge.SimulationEngineException;
import com.capellaagent.simulation.bridge.SimulationResult;

/**
 * Orchestrates the full simulation cycle: extract parameters, run engine, propagate results.
 * <p>
 * Coordinates between the {@link ParameterExtractor}, a selected {@link ISimulationEngine},
 * and the {@link ResultPropagator} to execute a complete simulation workflow as defined
 * by a {@link SimulationConfig}.
 *
 * <h3>Execution Flow</h3>
 * <ol>
 *   <li>Extract input parameter values from the Capella model</li>
 *   <li>Connect to the selected simulation engine (if not already connected)</li>
 *   <li>Set parameters in the engine workspace</li>
 *   <li>Execute the simulation model</li>
 *   <li>Optionally propagate results back to the Capella model</li>
 * </ol>
 */
public class SimulationOrchestrator {

    private final Map<String, ISimulationEngine> engineRegistry;
    private final ParameterExtractor extractor;
    private final ResultPropagator propagator;

    /**
     * Constructs a new SimulationOrchestrator.
     *
     * @param engineRegistry the map of available simulation engines
     * @param extractor      the parameter extractor
     * @param propagator     the result propagator
     */
    public SimulationOrchestrator(
            Map<String, ISimulationEngine> engineRegistry,
            ParameterExtractor extractor,
            ResultPropagator propagator) {
        this.engineRegistry = Objects.requireNonNull(engineRegistry);
        this.extractor = Objects.requireNonNull(extractor);
        this.propagator = Objects.requireNonNull(propagator);
    }

    /**
     * Executes a complete simulation cycle.
     *
     * @param config  the simulation configuration
     * @param session the Sirius session (passed as Object to avoid compile-time dependency)
     * @param monitor a progress monitor for reporting progress and checking cancellation
     * @return the simulation result
     * @throws SimulationEngineException if the simulation engine encounters an error
     * @throws IllegalArgumentException  if the config is invalid or the engine is not found
     */
    public SimulationResult execute(SimulationConfig config, Object session,
                                    IProgressMonitor monitor) throws SimulationEngineException {
        Objects.requireNonNull(config, "config must not be null");

        if (!config.isValid()) {
            throw new IllegalArgumentException("Invalid simulation config: engineId and modelPath are required");
        }

        ISimulationEngine engine = engineRegistry.get(config.getEngineId());
        if (engine == null) {
            throw new IllegalArgumentException(
                    "Simulation engine not found: '" + config.getEngineId()
                            + "'. Available engines: " + engineRegistry.keySet());
        }

        SubMonitor subMonitor = SubMonitor.convert(monitor, "Simulation", 100);

        try {
            // Step 1: Extract parameters from the Capella model
            subMonitor.subTask("Extracting parameters from model");
            Map<String, Object> parameters = extractor.extract(
                    config.getInputMappings(), session);
            subMonitor.worked(20);

            Platform.getLog(getClass()).info(
                    "Extracted " + parameters.size() + " parameters for simulation");

            // Step 2: Connect to the engine
            subMonitor.subTask("Connecting to simulation engine: " + engine.getDisplayName());
            engine.connect();
            subMonitor.worked(10);

            // Step 3: Set parameters
            subMonitor.subTask("Setting simulation parameters");
            engine.setParameters(parameters);
            subMonitor.worked(10);

            // Step 4: Run the simulation
            subMonitor.subTask("Running simulation model: " + config.getModelPath());
            SimulationResult result = engine.run(
                    config.getModelPath(), subMonitor.split(50));

            // Store the inputs in the result for reference
            result.getInputs().putAll(parameters);

            // Step 5: Propagate results if simulation succeeded
            if (result.isSuccess() && !config.getOutputMappings().isEmpty()) {
                subMonitor.subTask("Propagating results to model");
                propagator.propagate(result, config.getOutputMappings(), session);
            }
            subMonitor.worked(10);

            Platform.getLog(getClass()).info("Simulation completed: " + result);

            return result;

        } catch (SimulationEngineException e) {
            SimulationResult failedResult = new SimulationResult();
            failedResult.setStatus(SimulationResult.Status.FAILED);
            failedResult.setErrorMessage(e.getMessage());
            failedResult.addLog("ERROR: " + e.getMessage());
            return failedResult;
        } finally {
            subMonitor.done();
        }
    }

    /**
     * Returns the parameter extractor.
     *
     * @return the extractor
     */
    public ParameterExtractor getExtractor() {
        return extractor;
    }

    /**
     * Returns the result propagator.
     *
     * @return the propagator
     */
    public ResultPropagator getPropagator() {
        return propagator;
    }
}

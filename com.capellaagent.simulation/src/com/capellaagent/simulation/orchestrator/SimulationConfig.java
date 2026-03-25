package com.capellaagent.simulation.orchestrator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object containing the complete configuration for a simulation run.
 * <p>
 * Specifies the engine to use, the model to run, how parameters map from the
 * Capella model to the simulation, and how results map back.
 */
public class SimulationConfig {

    private String engineId;
    private String modelPath;
    private List<ParameterMapping> inputMappings;
    private List<ResultMapping> outputMappings;
    private Map<String, Object> engineOptions;

    /**
     * Constructs an empty SimulationConfig.
     */
    public SimulationConfig() {
        this.inputMappings = new ArrayList<>();
        this.outputMappings = new ArrayList<>();
        this.engineOptions = new HashMap<>();
    }

    /**
     * Returns the simulation engine identifier.
     *
     * @return the engine ID (e.g., "matlab", "matlab_cli")
     */
    public String getEngineId() {
        return engineId;
    }

    /**
     * Sets the simulation engine identifier.
     *
     * @param engineId the engine ID
     */
    public void setEngineId(String engineId) {
        this.engineId = engineId;
    }

    /**
     * Returns the file system path to the simulation model.
     *
     * @return the model path
     */
    public String getModelPath() {
        return modelPath;
    }

    /**
     * Sets the simulation model path.
     *
     * @param modelPath the file system path to the model
     */
    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    /**
     * Returns the input parameter mappings from Capella model to simulation.
     *
     * @return the list of input mappings
     */
    public List<ParameterMapping> getInputMappings() {
        return inputMappings;
    }

    /**
     * Sets the input parameter mappings.
     *
     * @param inputMappings the input mappings
     */
    public void setInputMappings(List<ParameterMapping> inputMappings) {
        this.inputMappings = inputMappings;
    }

    /**
     * Returns the output result mappings from simulation back to Capella model.
     *
     * @return the list of output mappings
     */
    public List<ResultMapping> getOutputMappings() {
        return outputMappings;
    }

    /**
     * Sets the output result mappings.
     *
     * @param outputMappings the output mappings
     */
    public void setOutputMappings(List<ResultMapping> outputMappings) {
        this.outputMappings = outputMappings;
    }

    /**
     * Returns additional engine-specific options.
     *
     * @return the engine options map
     */
    public Map<String, Object> getEngineOptions() {
        return engineOptions;
    }

    /**
     * Sets the engine-specific options.
     *
     * @param engineOptions the engine options
     */
    public void setEngineOptions(Map<String, Object> engineOptions) {
        this.engineOptions = engineOptions;
    }

    /**
     * Validates that this configuration has all required fields.
     *
     * @return {@code true} if the configuration is valid for execution
     */
    public boolean isValid() {
        return engineId != null && !engineId.isEmpty()
                && modelPath != null && !modelPath.isEmpty();
    }
}

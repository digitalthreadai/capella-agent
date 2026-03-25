package com.capellaagent.simulation.bridge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object representing the result of a simulation execution.
 * <p>
 * Contains the simulation inputs, outputs, execution status, timing information,
 * and any diagnostic logs or error messages produced during the run.
 */
public class SimulationResult {

    /**
     * Status of a simulation execution.
     */
    public enum Status {
        /** Simulation completed successfully. */
        SUCCESS,
        /** Simulation failed with an error. */
        FAILED,
        /** Simulation was cancelled by the user or a timeout. */
        CANCELLED
    }

    private final Map<String, Object> outputs;
    private final Map<String, Object> inputs;
    private Status status;
    private long durationMs;
    private String errorMessage;
    private final List<String> logs;
    private String simulationId;

    /**
     * Constructs a new SimulationResult with empty maps and logs.
     */
    public SimulationResult() {
        this.outputs = new HashMap<>();
        this.inputs = new HashMap<>();
        this.logs = new ArrayList<>();
        this.status = Status.SUCCESS;
        this.durationMs = 0;
    }

    /**
     * Returns the simulation output values, keyed by output parameter name.
     *
     * @return the output map
     */
    public Map<String, Object> getOutputs() {
        return outputs;
    }

    /**
     * Returns the simulation input values that were used.
     *
     * @return the input map
     */
    public Map<String, Object> getInputs() {
        return inputs;
    }

    /**
     * Returns the execution status.
     *
     * @return the status
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Sets the execution status.
     *
     * @param status the status
     */
    public void setStatus(Status status) {
        this.status = status;
    }

    /**
     * Returns the execution duration in milliseconds.
     *
     * @return the duration in milliseconds
     */
    public long getDurationMs() {
        return durationMs;
    }

    /**
     * Sets the execution duration.
     *
     * @param durationMs the duration in milliseconds
     */
    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    /**
     * Returns the error message if the simulation failed.
     *
     * @return the error message, or {@code null} if successful
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Sets the error message.
     *
     * @param errorMessage the error message
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Returns the diagnostic log messages produced during execution.
     *
     * @return the list of log messages
     */
    public List<String> getLogs() {
        return logs;
    }

    /**
     * Adds a log message.
     *
     * @param message the log message
     */
    public void addLog(String message) {
        this.logs.add(message);
    }

    /**
     * Returns the unique simulation run identifier.
     *
     * @return the simulation ID
     */
    public String getSimulationId() {
        return simulationId;
    }

    /**
     * Sets the simulation run identifier.
     *
     * @param simulationId the simulation ID
     */
    public void setSimulationId(String simulationId) {
        this.simulationId = simulationId;
    }

    /**
     * Returns whether the simulation completed successfully.
     *
     * @return {@code true} if status is SUCCESS
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    @Override
    public String toString() {
        return "SimulationResult{status=" + status + ", durationMs=" + durationMs
                + ", outputs=" + outputs.size() + " values"
                + (errorMessage != null ? ", error='" + errorMessage + "'" : "")
                + '}';
    }
}

package com.capellaagent.core.bus;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Event fired when a simulation run completes and results are available.
 * <p>
 * Carries the simulation identifier, status, and a map of result data
 * that varies depending on the simulation type.
 */
public class SimulationResultEvent extends AgentEvent {

    /** Status constant for a successful simulation. */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /** Status constant for a failed simulation. */
    public static final String STATUS_FAILED = "FAILED";

    /** Status constant for a cancelled simulation. */
    public static final String STATUS_CANCELLED = "CANCELLED";

    private final String simulationId;
    private final Map<String, Object> results;
    private final String status;

    /**
     * Constructs a new simulation result event.
     *
     * @param source       the event source (e.g., "simulation-engine")
     * @param simulationId the unique identifier of the simulation run
     * @param results      the simulation results as key-value pairs
     * @param status       the simulation status (SUCCESS, FAILED, CANCELLED)
     */
    public SimulationResultEvent(String source, String simulationId,
                                  Map<String, Object> results, String status) {
        super(source);
        this.simulationId = simulationId;
        this.results = results != null ? new HashMap<>(results) : new HashMap<>();
        this.status = status;
    }

    /**
     * Returns the unique identifier of the simulation run.
     *
     * @return the simulation ID
     */
    public String getSimulationId() {
        return simulationId;
    }

    /**
     * Returns an unmodifiable view of the simulation results.
     *
     * @return the results map
     */
    public Map<String, Object> getResults() {
        return Collections.unmodifiableMap(results);
    }

    /**
     * Returns the simulation status.
     *
     * @return the status string
     */
    public String getStatus() {
        return status;
    }

    /**
     * Returns whether the simulation completed successfully.
     *
     * @return true if status is SUCCESS
     */
    public boolean isSuccess() {
        return STATUS_SUCCESS.equals(status);
    }

    @Override
    public String toString() {
        return "SimulationResultEvent{source='" + getSource() +
                "', simulationId='" + simulationId +
                "', status='" + status +
                "', results=" + results.size() + " entries}";
    }
}

package com.capellaagent.simulation.bridge;

/**
 * Exception thrown when a simulation engine operation fails.
 */
public class SimulationEngineException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new SimulationEngineException.
     *
     * @param message a human-readable description of the failure
     */
    public SimulationEngineException(String message) {
        super(message);
    }

    /**
     * Constructs a new SimulationEngineException wrapping a cause.
     *
     * @param message a human-readable description
     * @param cause   the underlying cause
     */
    public SimulationEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}

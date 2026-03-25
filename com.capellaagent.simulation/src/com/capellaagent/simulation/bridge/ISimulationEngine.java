package com.capellaagent.simulation.bridge;

import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;

/**
 * Interface for simulation engine bridges.
 * <p>
 * Implementations connect to external simulation tools (e.g., MATLAB, Simulink,
 * OpenModelica) and provide a uniform API for parameter passing, execution, and
 * result retrieval.
 * <p>
 * The lifecycle of an engine is:
 * <ol>
 *   <li>{@link #connect()} - Establish connection to the simulation tool</li>
 *   <li>{@link #setParameters(Map)} - Pass input parameters</li>
 *   <li>{@link #run(String, IProgressMonitor)} - Execute the simulation model</li>
 *   <li>{@link #disconnect()} - Release resources</li>
 * </ol>
 */
public interface ISimulationEngine {

    /**
     * Returns the unique identifier for this engine.
     *
     * @return the engine ID (e.g., "matlab", "matlab_cli", "openmodelica")
     */
    String getId();

    /**
     * Returns a human-readable display name for this engine.
     *
     * @return the display name
     */
    String getDisplayName();

    /**
     * Checks whether this engine is available on the current system.
     * <p>
     * This may check for installed software, available licenses, or
     * network connectivity to a remote simulation server.
     *
     * @return {@code true} if the engine can be used
     */
    boolean isAvailable();

    /**
     * Establishes a connection to the simulation tool.
     *
     * @throws SimulationEngineException if the connection fails
     */
    void connect() throws SimulationEngineException;

    /**
     * Sets input parameters for the next simulation run.
     *
     * @param parameters a map of parameter name to value
     * @throws SimulationEngineException if parameter setting fails
     */
    void setParameters(Map<String, Object> parameters) throws SimulationEngineException;

    /**
     * Runs the simulation model at the given path.
     *
     * @param modelPath the file system path to the simulation model
     * @param monitor   a progress monitor for reporting progress and checking cancellation
     * @return the simulation result containing outputs, timing, and status
     * @throws SimulationEngineException if the simulation execution fails
     */
    SimulationResult run(String modelPath, IProgressMonitor monitor) throws SimulationEngineException;

    /**
     * Disconnects from the simulation tool and releases resources.
     */
    void disconnect();
}

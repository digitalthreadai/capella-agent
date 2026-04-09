package com.capellaagent.simulation.bridge;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;

import com.capellaagent.simulation.config.SimulationPreferences;

/**
 * Simulation engine bridge that executes MATLAB via command-line batch mode.
 * <p>
 * This is a fallback engine for environments where the MATLAB Engine API for Java
 * is not available. It launches MATLAB as a subprocess using {@code matlab -batch},
 * communicating parameters and results through temporary files.
 *
 * <h3>Communication Protocol</h3>
 * <ol>
 *   <li>Input parameters are written to a temporary {@code .mat} file (PLACEHOLDER)</li>
 *   <li>A wrapper script loads the parameters, runs the model, and saves outputs</li>
 *   <li>The output {@code .mat} file is read back to extract results (PLACEHOLDER)</li>
 * </ol>
 *
 * <h3>PLACEHOLDER Notice</h3>
 * The MAT file I/O (reading and writing {@code .mat} files from Java) requires
 * a library such as JMatIO or MFL. These calls are marked as PLACEHOLDER.
 */
public class MatlabCommandBridge implements ISimulationEngine {

    private static final String ENGINE_ID = "matlab_cli";
    private static final String DISPLAY_NAME = "MATLAB Command Line (Batch Mode)";

    private String matlabExecutable;
    private Path workingDir;
    private Map<String, Object> currentParameters;
    private boolean connected;

    @Override
    public String getId() {
        return ENGINE_ID;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public boolean isAvailable() {
        String matlabPath = resolveMatlabPath();
        return matlabPath != null && new File(matlabPath).canExecute();
    }

    @Override
    public void connect() throws SimulationEngineException {
        if (connected) {
            return;
        }

        matlabExecutable = resolveMatlabPath();
        if (matlabExecutable == null) {
            throw new SimulationEngineException(
                    "MATLAB executable not found. Set MATLAB_HOME environment variable "
                            + "or configure simulation.matlab.path in preferences.");
        }

        SimulationPreferences prefs = new SimulationPreferences();
        String workDir = prefs.getWorkingDirectory();

        try {
            workingDir = workDir != null && !workDir.isEmpty()
                    ? Path.of(workDir)
                    : Files.createTempDirectory("capella-sim-");
            if (!Files.exists(workingDir)) {
                Files.createDirectories(workingDir);
            }
        } catch (IOException e) {
            throw new SimulationEngineException(
                    "Failed to create working directory: " + e.getMessage(), e);
        }

        connected = true;
        Platform.getLog(getClass()).info(
                "MATLAB CLI bridge connected. Executable: " + matlabExecutable
                        + ", Working dir: " + workingDir);
    }

    @Override
    public void setParameters(Map<String, Object> parameters) throws SimulationEngineException {
        if (!connected) {
            throw new SimulationEngineException("Not connected. Call connect() first.");
        }
        this.currentParameters = parameters;

        // PLACEHOLDER: Write parameters to a .mat file using JMatIO or MFL library.
        // Path paramsFile = workingDir.resolve("sim_params.mat");
        // try {
        //     MatFile matFile = MatFile.create();
        //     for (Map.Entry<String, Object> entry : parameters.entrySet()) {
        //         matFile.addArray(entry.getKey(), toMatlabArray(entry.getValue()));
        //     }
        //     MatFileWriter.write(paramsFile.toFile(), matFile);
        // } catch (IOException e) {
        //     throw new SimulationEngineException("Failed to write parameters file: " + e.getMessage(), e);
        // }

        Platform.getLog(getClass()).info(
                "PLACEHOLDER: Parameters would be written to sim_params.mat ("
                        + parameters.size() + " variables)");
    }

    @Override
    public SimulationResult run(String modelPath, IProgressMonitor monitor)
            throws SimulationEngineException {
        if (!connected) {
            throw new SimulationEngineException("Not connected. Call connect() first.");
        }

        // SECURITY (I1/N1): defence-in-depth against a future tool that
        // forwards an LLM-supplied modelPath. Reject anything outside the
        // workspace, not a .slx/.mdl file, or containing MATLAB script
        // metacharacters that could break out of the wrapper quoting.
        try {
            validateModelPath(modelPath);
        } catch (SecurityException se) {
            throw new SimulationEngineException(
                "MATLAB model path rejected: " + se.getMessage());
        }

        long startTime = System.currentTimeMillis();
        SimulationResult result = new SimulationResult();
        result.setSimulationId(UUID.randomUUID().toString());
        result.addLog("Starting MATLAB batch simulation: " + modelPath);
        // Audit every invocation (I1) — closes a blind spot before this
        // bridge becomes reachable from an LLM tool.
        com.capellaagent.core.security.AuditLogger.getInstance()
            .log("simulation.matlab.run",
                "{\"modelPath\":\"" + modelPath.replace("\"", "'") + "\"}");

        if (currentParameters != null) {
            result.getInputs().putAll(currentParameters);
        }

        SimulationPreferences prefs = new SimulationPreferences();
        int timeoutSeconds = prefs.getTimeoutSeconds();

        monitor.beginTask("Running MATLAB simulation (batch mode)", IProgressMonitor.UNKNOWN);

        try {
            // Build the MATLAB batch command
            // The wrapper script loads params, runs the model, and saves results
            String wrapperScript = buildWrapperScript(modelPath);
            Path scriptFile = workingDir.resolve("sim_wrapper_" + result.getSimulationId() + ".m");
            Files.writeString(scriptFile, wrapperScript);

            List<String> command = new ArrayList<>();
            command.add(matlabExecutable);
            command.add("-batch");
            command.add("run('" + scriptFile.toString().replace("\\", "/").replace("'", "''") + "')");

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDir.toFile());
            pb.redirectErrorStream(true);

            Platform.getLog(getClass()).info("Executing: " + String.join(" ", command));

            Process process = pb.start();

            // Read stdout/stderr
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    result.addLog(line);
                    if (monitor.isCanceled()) {
                        process.destroyForcibly();
                        result.setStatus(SimulationResult.Status.CANCELLED);
                        result.addLog("Simulation cancelled by user");
                        return result;
                    }
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                result.setStatus(SimulationResult.Status.FAILED);
                result.setErrorMessage("Simulation timed out after " + timeoutSeconds + " seconds");
                result.addLog("ERROR: Simulation timed out");
                return result;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                result.setStatus(SimulationResult.Status.FAILED);
                result.setErrorMessage("MATLAB exited with code " + exitCode);
                result.addLog("ERROR: MATLAB exit code " + exitCode);
                return result;
            }

            // PLACEHOLDER: Read output .mat file to extract results.
            // Path outputFile = workingDir.resolve("sim_results.mat");
            // if (Files.exists(outputFile)) {
            //     MatFile matFile = MatFileReader.read(outputFile.toFile());
            //     for (String name : matFile.getEntryNames()) {
            //         result.getOutputs().put(name, fromMatlabArray(matFile.getArray(name)));
            //     }
            // }

            result.setStatus(SimulationResult.Status.SUCCESS);
            result.addLog("MATLAB batch simulation completed successfully");
            result.addLog("PLACEHOLDER: Output .mat file parsing not implemented. "
                    + "Add JMatIO or MFL library for MAT file I/O.");

        } catch (IOException e) {
            result.setStatus(SimulationResult.Status.FAILED);
            result.setErrorMessage("I/O error during simulation: " + e.getMessage());
            result.addLog("ERROR: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.setStatus(SimulationResult.Status.CANCELLED);
            result.setErrorMessage("Simulation interrupted");
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            result.setDurationMs(duration);
            result.addLog("Simulation duration: " + duration + " ms");
            monitor.done();
        }

        return result;
    }

    @Override
    public void disconnect() {
        connected = false;
        matlabExecutable = null;
        currentParameters = null;
        Platform.getLog(getClass()).info("MATLAB CLI bridge disconnected");
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Resolves the MATLAB executable path from preferences or environment.
     */
    private String resolveMatlabPath() {
        // Check preferences first
        SimulationPreferences prefs = new SimulationPreferences();
        String configuredPath = prefs.getMatlabPath();
        if (configuredPath != null && !configuredPath.isEmpty() && new File(configuredPath).exists()) {
            return configuredPath;
        }

        // Check MATLAB_HOME environment variable
        String matlabHome = System.getenv("MATLAB_HOME");
        if (matlabHome != null && !matlabHome.isEmpty()) {
            File exe = new File(matlabHome, "bin/matlab.exe");
            if (exe.exists()) {
                return exe.getAbsolutePath();
            }
            exe = new File(matlabHome, "bin/matlab");
            if (exe.exists()) {
                return exe.getAbsolutePath();
            }
        }

        // Check PATH
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                File exe = new File(dir, "matlab.exe");
                if (exe.exists()) {
                    return exe.getAbsolutePath();
                }
                exe = new File(dir, "matlab");
                if (exe.exists()) {
                    return exe.getAbsolutePath();
                }
            }
        }

        return null;
    }

    /**
     * SECURITY (I1/N1): validates an LLM- or user-supplied Simulink model
     * path before it reaches {@link #buildWrapperScript} where a single-quote
     * escape is the only thing separating user input from arbitrary MATLAB
     * execution.
     * <p>
     * Enforces:
     * <ul>
     *   <li>non-null and non-blank</li>
     *   <li>extension {@code .slx} or {@code .mdl}</li>
     *   <li>no newline / tab / backtick / percent / semicolon / single-quote
     *       / {@code system(} / {@code eval(} metacharacters (MATLAB script
     *       injection primitives)</li>
     *   <li>canonical path is inside the Eclipse workspace root</li>
     *   <li>not a symbolic link (TOCTOU + path confusion)</li>
     * </ul>
     */
    private static void validateModelPath(String modelPath) {
        if (modelPath == null || modelPath.isBlank()) {
            throw new SecurityException("model path is empty");
        }
        String lower = modelPath.toLowerCase();
        if (!(lower.endsWith(".slx") || lower.endsWith(".mdl"))) {
            throw new SecurityException("only .slx and .mdl are allowed");
        }
        // Reject MATLAB script metacharacters / known dangerous tokens.
        String[] banned = {"\n", "\r", "\t", "`", "%", ";", "'", "\"",
                           "system(", "eval(", "unix(", "!", "&&", "||", "|"};
        for (String b : banned) {
            if (modelPath.contains(b)) {
                throw new SecurityException("path contains disallowed character");
            }
        }
        try {
            java.nio.file.Path p = java.nio.file.Paths.get(modelPath)
                .toAbsolutePath().normalize();
            if (java.nio.file.Files.isSymbolicLink(p)) {
                throw new SecurityException("path is a symbolic link");
            }
            // Workspace containment (falls back to user.home if Eclipse
            // workspace is not available).
            java.nio.file.Path root;
            try {
                root = java.nio.file.Paths.get(
                    org.eclipse.core.resources.ResourcesPlugin.getWorkspace()
                        .getRoot().getLocation().toOSString())
                    .toAbsolutePath().normalize();
            } catch (Exception e) {
                root = java.nio.file.Paths.get(System.getProperty("user.home"))
                    .toAbsolutePath().normalize();
            }
            if (!p.startsWith(root)) {
                throw new SecurityException("path is outside permitted root");
            }
        } catch (java.nio.file.InvalidPathException ipe) {
            throw new SecurityException("invalid path syntax");
        }
    }

    /**
     * Builds a MATLAB wrapper script that loads parameters, runs the model,
     * and saves results.
     */
    private String buildWrapperScript(String modelPath) {
        StringBuilder sb = new StringBuilder();
        sb.append("% Auto-generated wrapper script for Capella Agent simulation\n");
        sb.append("% Generated at: ").append(java.time.Instant.now()).append("\n\n");

        // Load parameters
        sb.append("% Load input parameters\n");
        sb.append("if exist('sim_params.mat', 'file')\n");
        sb.append("    load('sim_params.mat');\n");
        sb.append("end\n\n");

        // Run the model
        sb.append("% Execute the simulation model\n");
        String sanitizedPath = modelPath.replace("\\", "/").replace("'", "''");
        sb.append("try\n");
        sb.append("    run('").append(sanitizedPath).append("');\n");
        sb.append("    sim_status = 'SUCCESS';\n");
        sb.append("catch ME\n");
        sb.append("    sim_status = 'FAILED';\n");
        sb.append("    sim_error = ME.message;\n");
        sb.append("    fprintf(2, 'Simulation error: %s\\n', ME.message);\n");
        sb.append("end\n\n");

        // Save results
        sb.append("% Save output variables\n");
        sb.append("save('sim_results.mat');\n");

        return sb.toString();
    }
}

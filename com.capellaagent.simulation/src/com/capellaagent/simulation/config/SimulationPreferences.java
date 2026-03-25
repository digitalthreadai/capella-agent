package com.capellaagent.simulation.config;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;

/**
 * Reads and writes simulation preferences from Eclipse preferences store.
 * <p>
 * Preference keys:
 * <ul>
 *   <li>{@code simulation.matlab.path} - Path to the MATLAB executable</li>
 *   <li>{@code simulation.working_dir} - Working directory for simulation files</li>
 *   <li>{@code simulation.timeout_seconds} - Maximum simulation execution time (default: 300)</li>
 * </ul>
 */
public class SimulationPreferences {

    private static final String PREF_NODE = "com.capellaagent.simulation";
    private static final String KEY_MATLAB_PATH = "simulation.matlab.path";
    private static final String KEY_WORKING_DIR = "simulation.working_dir";
    private static final String KEY_TIMEOUT = "simulation.timeout_seconds";

    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    /**
     * Returns the configured MATLAB executable path.
     *
     * @return the MATLAB path, or {@code null} if not configured
     */
    public String getMatlabPath() {
        return getPreference(KEY_MATLAB_PATH, null);
    }

    /**
     * Sets the MATLAB executable path.
     *
     * @param path the path to the MATLAB executable
     */
    public void setMatlabPath(String path) {
        setPreference(KEY_MATLAB_PATH, path);
    }

    /**
     * Returns the working directory for simulation files.
     *
     * @return the working directory path, or {@code null} if not configured
     */
    public String getWorkingDirectory() {
        return getPreference(KEY_WORKING_DIR, null);
    }

    /**
     * Sets the working directory for simulation files.
     *
     * @param dir the directory path
     */
    public void setWorkingDirectory(String dir) {
        setPreference(KEY_WORKING_DIR, dir);
    }

    /**
     * Returns the simulation timeout in seconds.
     *
     * @return the timeout in seconds (default: 300)
     */
    public int getTimeoutSeconds() {
        String value = getPreference(KEY_TIMEOUT, String.valueOf(DEFAULT_TIMEOUT_SECONDS));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
    }

    /**
     * Sets the simulation timeout.
     *
     * @param seconds the timeout in seconds
     */
    public void setTimeoutSeconds(int seconds) {
        setPreference(KEY_TIMEOUT, String.valueOf(seconds));
    }

    private String getPreference(String key, String defaultValue) {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PREF_NODE);
        return prefs.get(key, defaultValue);
    }

    private void setPreference(String key, String value) {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PREF_NODE);
        if (value != null) {
            prefs.put(key, value);
        } else {
            prefs.remove(key);
        }
        try {
            prefs.flush();
        } catch (org.osgi.service.prefs.BackingStoreException e) {
            org.eclipse.core.runtime.Platform.getLog(getClass())
                    .error("Failed to save simulation preferences", e);
        }
    }
}

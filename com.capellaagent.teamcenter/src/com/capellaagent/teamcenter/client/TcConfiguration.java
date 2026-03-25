package com.capellaagent.teamcenter.client;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;

/**
 * Reads Teamcenter connection configuration from Eclipse preferences.
 * <p>
 * Preference keys:
 * <ul>
 *   <li>{@code tc.gateway.url} - The Teamcenter REST gateway URL</li>
 *   <li>{@code tc.auth.method} - Authentication method: "basic" or "sso"</li>
 *   <li>{@code tc.username} - The Teamcenter username</li>
 * </ul>
 * The password is stored securely via Eclipse Secure Storage and is never
 * persisted in plain text.
 */
public class TcConfiguration {

    private static final String PREF_NODE = "com.capellaagent.teamcenter";
    private static final String KEY_GATEWAY_URL = "tc.gateway.url";
    private static final String KEY_AUTH_METHOD = "tc.auth.method";
    private static final String KEY_USERNAME = "tc.username";
    private static final String SECURE_NODE = "/com/capellaagent/teamcenter";
    private static final String SECURE_KEY_PASSWORD = "tc.password";

    private static final String DEFAULT_GATEWAY_URL = "http://localhost:7001/tc";
    private static final String DEFAULT_AUTH_METHOD = "basic";

    /**
     * Returns the Teamcenter REST gateway URL.
     *
     * @return the gateway URL, or the default localhost URL if not configured
     */
    public String getGatewayUrl() {
        return getPreference(KEY_GATEWAY_URL, DEFAULT_GATEWAY_URL);
    }

    /**
     * Returns the configured Teamcenter username.
     *
     * @return the username, or an empty string if not configured
     */
    public String getUsername() {
        return getPreference(KEY_USERNAME, "");
    }

    /**
     * Returns the Teamcenter password from Eclipse Secure Storage.
     *
     * @return the password, or an empty string if not stored or unavailable
     */
    public String getPassword() {
        try {
            ISecurePreferences root = SecurePreferencesFactory.getDefault();
            ISecurePreferences node = root.node(SECURE_NODE);
            return node.get(SECURE_KEY_PASSWORD, "");
        } catch (StorageException e) {
            Platform.getLog(getClass()).error("Failed to read password from secure storage", e);
            return "";
        }
    }

    /**
     * Returns the authentication method.
     *
     * @return "basic" or "sso"
     */
    public String getAuthMethod() {
        return getPreference(KEY_AUTH_METHOD, DEFAULT_AUTH_METHOD);
    }

    /**
     * Stores the Teamcenter password in Eclipse Secure Storage.
     *
     * @param password the password to store
     * @throws StorageException if the secure storage operation fails
     */
    public void setPassword(String password) throws StorageException {
        ISecurePreferences root = SecurePreferencesFactory.getDefault();
        ISecurePreferences node = root.node(SECURE_NODE);
        node.put(SECURE_KEY_PASSWORD, password, true /* encrypt */);
    }

    /**
     * Validates that all required configuration values are present.
     *
     * @return {@code true} if gateway URL, username, and password are all non-empty
     */
    public boolean isValid() {
        return !getGatewayUrl().isEmpty()
                && !getUsername().isEmpty()
                && !getPassword().isEmpty();
    }

    private String getPreference(String key, String defaultValue) {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PREF_NODE);
        return prefs.get(key, defaultValue);
    }
}

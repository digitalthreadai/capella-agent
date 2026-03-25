package com.capellaagent.core.security;

import java.util.logging.Logger;

import com.capellaagent.core.config.AgentConfiguration;
import com.capellaagent.core.tools.ToolExecutionException;

/**
 * Centralized security service managing access control for agent operations.
 * <p>
 * Controls whether agents operate in read-only or read-write mode, and
 * provides guard methods that tools call before performing write operations.
 * Default settings are loaded from {@link AgentConfiguration} at first access.
 * <p>
 * This is a singleton; obtain the instance via {@link #getInstance()}.
 */
public final class SecurityService {

    private static final Logger LOG = Logger.getLogger(SecurityService.class.getName());

    private static final SecurityService INSTANCE = new SecurityService();

    private volatile AccessMode accessMode;
    private volatile boolean initialized = false;

    private SecurityService() {
        // Defaults until configuration is loaded
        this.accessMode = AccessMode.READ_ONLY;
    }

    /**
     * Returns the singleton instance.
     *
     * @return the security service instance
     */
    public static SecurityService getInstance() {
        return INSTANCE;
    }

    /**
     * Ensures that configuration defaults are loaded. Called lazily on first
     * access to configuration-dependent methods.
     */
    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    try {
                        AgentConfiguration config = AgentConfiguration.getInstance();
                        String modeStr = config.getSecurityAccessMode();
                        if (modeStr != null && !modeStr.isBlank()) {
                            this.accessMode = AccessMode.valueOf(modeStr.toUpperCase());
                        }
                        AuditLogger.getInstance().setEnabled(config.isAuditEnabled());
                    } catch (Exception e) {
                        LOG.warning("Failed to load security configuration, using defaults: " +
                                e.getMessage());
                    }
                    initialized = true;
                    LOG.info("SecurityService initialized: accessMode=" + accessMode +
                            ", audit=" + AuditLogger.getInstance().isEnabled());
                }
            }
        }
    }

    /**
     * Returns the current access mode.
     *
     * @return the access mode
     */
    public AccessMode getAccessMode() {
        ensureInitialized();
        return accessMode;
    }

    /**
     * Sets the access mode.
     *
     * @param mode the new access mode; must not be null
     */
    public void setAccessMode(AccessMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        this.accessMode = mode;
        this.initialized = true;
        LOG.info("Access mode changed to: " + mode);

        AuditLogger.getInstance().log("security.access_mode_changed",
                "Access mode set to " + mode);
    }

    /**
     * Checks that the current access mode allows write operations.
     *
     * @throws ToolExecutionException if the access mode is READ_ONLY
     */
    public void requireWriteMode() throws ToolExecutionException {
        ensureInitialized();
        if (accessMode == AccessMode.READ_ONLY) {
            throw new ToolExecutionException(ToolExecutionException.ERR_PERMISSION_DENIED,
                    "Write operation denied: agent is in READ_ONLY mode. " +
                            "Switch to READ_WRITE mode in settings to allow model modifications.");
        }
    }

    /**
     * Returns whether audit logging is enabled.
     *
     * @return true if audit logging is enabled
     */
    public boolean isAuditEnabled() {
        ensureInitialized();
        return AuditLogger.getInstance().isEnabled();
    }

    /**
     * Checks whether the current access mode is READ_WRITE.
     *
     * @return true if write operations are allowed
     */
    public boolean isWriteAllowed() {
        ensureInitialized();
        return accessMode == AccessMode.READ_WRITE;
    }
}

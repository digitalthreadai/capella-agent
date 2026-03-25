package com.capellaagent.core.security;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

import com.google.gson.JsonObject;

/**
 * Logs structured audit entries for all agent actions.
 * <p>
 * Audit entries record who did what and when, providing a traceable history
 * of agent interactions with the Capella model. All entries are written to
 * the standard Java logging framework and can be routed to file or console
 * handlers.
 * <p>
 * This is a singleton; obtain the instance via {@link #getInstance()}.
 */
public final class AuditLogger {

    private static final Logger LOG = Logger.getLogger("com.capellaagent.audit");

    private static final AuditLogger INSTANCE = new AuditLogger();

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                    .withZone(ZoneId.systemDefault());

    private volatile boolean enabled = true;

    private AuditLogger() {
        // Singleton
    }

    /**
     * Returns the singleton audit logger instance.
     *
     * @return the audit logger
     */
    public static AuditLogger getInstance() {
        return INSTANCE;
    }

    /**
     * Logs an audit entry for the given action.
     *
     * @param action  a short description of the action (e.g., "tool.execute", "model.create")
     * @param details a JsonObject with structured details about the action
     */
    public void log(String action, JsonObject details) {
        if (!enabled) {
            return;
        }

        String timestamp = TIMESTAMP_FORMAT.format(Instant.now());
        String detailsStr = (details != null) ? details.toString() : "{}";
        String entry = String.format("AUDIT [%s] %s: %s", timestamp, action, detailsStr);
        LOG.info(entry);
    }

    /**
     * Logs an audit entry with a simple message.
     *
     * @param action  a short description of the action
     * @param message a human-readable message describing what happened
     */
    public void log(String action, String message) {
        if (!enabled) {
            return;
        }

        JsonObject details = new JsonObject();
        details.addProperty("message", message);
        log(action, details);
    }

    /**
     * Logs an audit entry for a tool execution.
     *
     * @param toolName  the name of the tool that was executed
     * @param arguments the arguments passed to the tool (as JSON string)
     * @param success   whether the execution succeeded
     * @param message   optional result or error message
     */
    public void logToolExecution(String toolName, String arguments, boolean success, String message) {
        JsonObject details = new JsonObject();
        details.addProperty("tool", toolName);
        details.addProperty("arguments", arguments);
        details.addProperty("success", success);
        if (message != null) {
            details.addProperty("message", message);
        }
        log("tool.execute", details);
    }

    /**
     * Logs an audit entry for a model modification.
     *
     * @param changeType  the type of change (CREATE, UPDATE, DELETE)
     * @param elementType the type of model element affected
     * @param elementId   the unique identifier of the element
     * @param details     additional details about the modification
     */
    public void logModelChange(String changeType, String elementType, String elementId,
                               JsonObject details) {
        JsonObject entry = new JsonObject();
        entry.addProperty("changeType", changeType);
        entry.addProperty("elementType", elementType);
        entry.addProperty("elementId", elementId);
        if (details != null) {
            entry.add("changeDetails", details);
        }
        log("model." + changeType.toLowerCase(), entry);
    }

    /**
     * Sets whether audit logging is enabled.
     *
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns whether audit logging is currently enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
}

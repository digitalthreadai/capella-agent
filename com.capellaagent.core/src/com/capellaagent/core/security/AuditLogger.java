package com.capellaagent.core.security;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonObject;

/**
 * Logs structured JSON audit entries for all agent actions.
 * <p>
 * Audit entries record who did what and when, providing a traceable history
 * of agent interactions with the Capella model. Each entry is a single-line
 * JSON object containing:
 * <ul>
 *   <li>{@code timestamp} - ISO 8601 timestamp</li>
 *   <li>{@code user} - the OS user name</li>
 *   <li>{@code action} - the action category (e.g., "tool.execute", "model.create")</li>
 *   <li>{@code toolName} - the tool name (when applicable)</li>
 *   <li>{@code toolCategory} - the tool category (model_read, model_write, diagram)</li>
 *   <li>{@code arguments} - sanitized tool arguments</li>
 *   <li>{@code success} - whether the action succeeded</li>
 *   <li>{@code message} - result or error message</li>
 *   <li>{@code elementId} - the affected element UUID (when applicable)</li>
 * </ul>
 * <p>
 * Entries are written to both the standard Java logging framework and a
 * dedicated audit log file at {@code {workspace}/.capella-agent/audit.log}.
 * <p>
 * This is a singleton; obtain the instance via {@link #getInstance()}.
 */
public final class AuditLogger {

    private static final Logger LOG = Logger.getLogger("com.capellaagent.audit");

    private static final AuditLogger INSTANCE = new AuditLogger();

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                    .withZone(ZoneId.systemDefault());

    /** Maximum length for argument strings in audit entries to prevent log bloat. */
    private static final int MAX_ARGUMENTS_LENGTH = 2000;

    private volatile boolean enabled = true;

    /** Lazily initialized path to the dedicated audit log file. */
    private volatile Path auditFilePath;

    /** Cached OS user name. */
    private final String userName;

    private AuditLogger() {
        this.userName = System.getProperty("user.name", "unknown");
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
     * Logs an audit entry for the given action with structured JSON details.
     *
     * @param action  a short description of the action (e.g., "tool.execute", "model.create")
     * @param details a JsonObject with structured details about the action
     */
    public void log(String action, JsonObject details) {
        if (!enabled) {
            return;
        }

        JsonObject entry = new JsonObject();
        entry.addProperty("timestamp", TIMESTAMP_FORMAT.format(Instant.now()));
        entry.addProperty("user", userName);
        entry.addProperty("action", action);

        // Merge details into the entry
        if (details != null) {
            for (var field : details.entrySet()) {
                entry.add(field.getKey(), field.getValue());
            }
        }

        String jsonLine = entry.toString();

        // Write to JUL logger
        LOG.info(jsonLine);

        // Write to dedicated audit log file
        writeToAuditFile(jsonLine);
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
     * Logs a structured audit entry for a tool execution.
     * <p>
     * The arguments string is sanitized to prevent sensitive data leakage
     * and truncated to a maximum length to prevent log bloat.
     *
     * @param toolName  the name of the tool that was executed
     * @param arguments the arguments passed to the tool (as JSON string)
     * @param success   whether the execution succeeded
     * @param message   optional result or error message
     */
    public void logToolExecution(String toolName, String arguments, boolean success, String message) {
        if (!enabled) {
            return;
        }

        JsonObject details = new JsonObject();
        details.addProperty("toolName", toolName);
        details.addProperty("toolCategory", inferToolCategory(toolName));
        details.addProperty("arguments", sanitizeArguments(arguments));
        details.addProperty("success", success);
        if (message != null) {
            details.addProperty("message", message);
        }

        // Extract elementId from arguments if present
        String elementId = extractElementId(arguments);
        if (elementId != null) {
            details.addProperty("elementId", elementId);
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
        if (!enabled) {
            return;
        }

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

    // ========================================================================
    // Internal Helpers
    // ========================================================================

    /**
     * Infers the tool category from the tool name.
     *
     * @param toolName the tool name
     * @return the inferred category string
     */
    private String inferToolCategory(String toolName) {
        if (toolName == null) return "unknown";
        if (toolName.startsWith("list_") || toolName.startsWith("get_")
                || toolName.startsWith("search_") || toolName.startsWith("validate_")) {
            return "model_read";
        }
        if (toolName.startsWith("create_") || toolName.startsWith("update_")
                || toolName.startsWith("delete_") || toolName.startsWith("allocate_")) {
            return "model_write";
        }
        if (toolName.contains("diagram")) {
            return "diagram";
        }
        return "other";
    }

    /**
     * Sanitizes tool arguments for logging by truncating to a maximum length.
     * This prevents sensitive data or overly large payloads from bloating the log.
     *
     * @param arguments the raw arguments string
     * @return the sanitized arguments
     */
    private String sanitizeArguments(String arguments) {
        if (arguments == null) return "{}";
        if (arguments.length() > MAX_ARGUMENTS_LENGTH) {
            return arguments.substring(0, MAX_ARGUMENTS_LENGTH) + "...(truncated)";
        }
        return arguments;
    }

    /**
     * Extracts the element UUID from a JSON arguments string, if present.
     * Looks for common parameter names: "uuid", "element_uuid", "function_uuid".
     *
     * @param arguments the JSON arguments string
     * @return the element UUID, or null if not found
     */
    private String extractElementId(String arguments) {
        if (arguments == null) return null;
        // Simple extraction without full JSON parsing for performance
        for (String key : new String[]{"\"uuid\"", "\"element_uuid\"", "\"function_uuid\""}) {
            int idx = arguments.indexOf(key);
            if (idx >= 0) {
                int valueStart = arguments.indexOf('"', idx + key.length() + 1);
                if (valueStart >= 0) {
                    int valueEnd = arguments.indexOf('"', valueStart + 1);
                    if (valueEnd > valueStart) {
                        return arguments.substring(valueStart + 1, valueEnd);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Writes a JSON audit entry to the dedicated audit log file.
     * The file is created lazily at {@code {user.home}/.capella-agent/audit.log}.
     * Failures are logged to JUL but do not propagate exceptions.
     *
     * @param jsonLine the single-line JSON entry to append
     */
    private void writeToAuditFile(String jsonLine) {
        try {
            Path path = getAuditFilePath();
            if (path == null) return;

            // Ensure parent directory exists
            Files.createDirectories(path.getParent());

            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(jsonLine);
                writer.newLine();
            }
        } catch (IOException e) {
            // Log to JUL but do not fail the main operation
            LOG.log(Level.FINE, "Failed to write to audit log file: " + e.getMessage(), e);
        }
    }

    /**
     * Gets or initializes the audit log file path.
     * Uses the Eclipse workspace location if available, otherwise falls back
     * to the user home directory.
     *
     * @return the Path to the audit log file, or null if it cannot be determined
     */
    private Path getAuditFilePath() {
        if (auditFilePath != null) {
            return auditFilePath;
        }

        try {
            // Try Eclipse workspace location first
            String workspacePath = null;
            try {
                org.eclipse.core.resources.IWorkspaceRoot root =
                        org.eclipse.core.resources.ResourcesPlugin.getWorkspace().getRoot();
                if (root.getLocation() != null) {
                    workspacePath = root.getLocation().toOSString();
                }
            } catch (Exception e) {
                // Eclipse workspace not available; use fallback
            }

            if (workspacePath != null) {
                auditFilePath = Paths.get(workspacePath, ".capella-agent", "audit.log");
            } else {
                auditFilePath = Paths.get(System.getProperty("user.home"),
                        ".capella-agent", "audit.log");
            }

            return auditFilePath;
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not determine audit log path", e);
            return null;
        }
    }
}

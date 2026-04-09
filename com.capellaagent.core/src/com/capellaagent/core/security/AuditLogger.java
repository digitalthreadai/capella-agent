package com.capellaagent.core.security;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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

    /** Rotate when the live audit.log exceeds 10 MB (G4). */
    private static final long ROTATE_BYTES = 10L * 1024 * 1024;

    /** Number of rotated files to keep. */
    private static final int MAX_ROTATED = 10;

    // I2: secret scrubbing — scrub JSON key/value pairs, bearer tokens,
    // and common credential-bearing headers BEFORE the argument string
    // is truncated or written.
    private static final Pattern JSON_SECRET = Pattern.compile(
        "(?i)\"(api[_-]?key|token|password|secret|credential|authorization|bearer|x[_-]api[_-]key|client[_-]secret|access[_-]token|refresh[_-]token)\"\\s*:\\s*\"[^\"]*\"");
    private static final Pattern BEARER = Pattern.compile(
        "(?i)(authorization\\s*:\\s*bearer\\s+)[^\\s\"',]+");
    private static final Pattern HEADER_KEY = Pattern.compile(
        "(?i)(x-goog-api-key|x-api-key|api-key)\\s*:\\s*[^\\s\"',]+");

    private volatile boolean enabled = true;

    /** Lazily initialized path to the dedicated audit log file. */
    private volatile Path auditFilePath;

    /** Cached OS user name. */
    private final String userName;

    /** HMAC key used for rolling line integrity (G4). */
    private final byte[] hmacKey;

    /** Previous line's HMAC, or "GENESIS" on first write. Guarded by {@code this}. */
    private String previousHmac = "GENESIS";

    private AuditLogger() {
        this.userName = System.getProperty("user.name", "unknown");
        this.hmacKey = loadOrGenerateHmacKey();
    }

    private byte[] loadOrGenerateHmacKey() {
        try {
            org.eclipse.equinox.security.storage.ISecurePreferences root =
                org.eclipse.equinox.security.storage.SecurePreferencesFactory.getDefault();
            if (root == null) {
                return randomKey();
            }
            org.eclipse.equinox.security.storage.ISecurePreferences node =
                root.node("com.capellaagent.audit");
            String hex = node.get("hmacKey", null);
            if (hex == null || hex.isEmpty()) {
                byte[] key = randomKey();
                node.put("hmacKey", HexFormat.of().formatHex(key), true);
                node.flush();
                return key;
            }
            return HexFormat.of().parseHex(hex);
        } catch (Exception e) {
            // Secure preferences unavailable — fall back to per-process
            // ephemeral key. HMAC still detects tampering within the
            // current process lifetime.
            return randomKey();
        }
    }

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    private String computeHmac(String line) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            mac.update(previousHmac.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '|');
            byte[] out = mac.doFinal(line.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            return "HMAC_ERROR";
        }
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
        // I2: scrub secrets BEFORE truncation.
        String scrubbed = JSON_SECRET.matcher(arguments).replaceAll(m -> {
            String field = m.group(1);
            return "\"" + field + "\":\"***\"";
        });
        scrubbed = BEARER.matcher(scrubbed).replaceAll("$1***");
        scrubbed = HEADER_KEY.matcher(scrubbed).replaceAll("$1: ***");

        // G4: UTF-8 byte-safe truncation instead of char-index slicing.
        byte[] bytes = scrubbed.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_ARGUMENTS_LENGTH) {
            return scrubbed;
        }
        int limit = MAX_ARGUMENTS_LENGTH;
        // Walk backwards off continuation bytes (10xxxxxx) to avoid
        // splitting a UTF-8 codepoint.
        while (limit > 0 && (bytes[limit] & 0xC0) == 0x80) {
            limit--;
        }
        return new String(bytes, 0, limit, StandardCharsets.UTF_8) + "...(truncated)";
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
    private synchronized void writeToAuditFile(String jsonLine) {
        try {
            Path path = getAuditFilePath();
            if (path == null) return;

            Files.createDirectories(path.getParent());
            rotateIfNeeded(path);

            // G4: rolling HMAC chain — detect casual tampering. Not
            // forensically strong (an attacker with filesystem access
            // can read the key from secure preferences) but raises the
            // bar above "edit audit.log in a text editor".
            String hmac = computeHmac(jsonLine);
            String framed = jsonLine.substring(0, jsonLine.length() - 1)
                + ",\"_prev\":\"" + previousHmac + "\",\"_hmac\":\"" + hmac + "\"}";
            previousHmac = hmac;

            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(framed);
                writer.newLine();
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "Failed to write to audit log file: " + e.getMessage(), e);
        }
    }

    /**
     * Rotates audit.log if it exceeds {@link #ROTATE_BYTES}. Keeps
     * {@link #MAX_ROTATED} historical files. Oldest gets deleted.
     */
    private void rotateIfNeeded(Path path) throws IOException {
        if (!Files.exists(path)) return;
        long size = Files.size(path);
        if (size <= ROTATE_BYTES) return;

        // Delete the oldest, shift each down.
        Path oldest = path.resolveSibling(path.getFileName() + "." + MAX_ROTATED);
        if (Files.exists(oldest)) {
            Files.delete(oldest);
        }
        for (int i = MAX_ROTATED - 1; i >= 1; i--) {
            Path src = path.resolveSibling(path.getFileName() + "." + i);
            Path dst = path.resolveSibling(path.getFileName() + "." + (i + 1));
            if (Files.exists(src)) {
                Files.move(src, dst);
            }
        }
        Path firstRotated = path.resolveSibling(path.getFileName() + ".1");
        Files.move(path, firstRotated);
        // HMAC chain resets on rotation — log a rotation marker so the
        // chain gap is explicit.
        previousHmac = "ROTATED";
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

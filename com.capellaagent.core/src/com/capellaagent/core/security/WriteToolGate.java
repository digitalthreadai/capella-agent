package com.capellaagent.core.security;

import java.util.Set;

/**
 * Decides whether a write tool call should be allowed, blocked, or require
 * explicit user consent based on the security context.
 * <p>
 * This is the consent gate for the prompt-injection defense (AI Engineer
 * Issue B9). Combined with {@link ToolResultSanitizer}, it ensures that
 * destructive tool calls whose decision was influenced by untrusted Capella
 * model content cannot run silently.
 *
 * <h2>Decision matrix</h2>
 * <table>
 *   <tr><th>Tool category</th><th>Suspicious context?</th><th>Decision</th></tr>
 *   <tr><td>Read</td><td>any</td><td>{@link Decision#ALLOW}</td></tr>
 *   <tr><td>Write</td><td>no</td><td>{@link Decision#ALLOW} (read-write mode)
 *       or {@link Decision#BLOCKED_READ_ONLY} (read-only mode)</td></tr>
 *   <tr><td>Write</td><td>yes</td><td>{@link Decision#REQUIRES_CONSENT}</td></tr>
 *   <tr><td>Destructive (delete/merge/batch)</td><td>any</td><td>
 *       {@link Decision#REQUIRES_CONSENT}</td></tr>
 * </table>
 */
public final class WriteToolGate {

    /** Result of a gate check. */
    public enum Decision {
        /** Tool may run immediately, no consent dialog. */
        ALLOW,
        /** Tool execution must wait for explicit user consent in the UI. */
        REQUIRES_CONSENT,
        /** Tool is blocked because the workspace is in read-only mode. */
        BLOCKED_READ_ONLY,
        /** Tool is administratively disabled at the site level. */
        BLOCKED_ADMIN
    }

    /**
     * Default set of "destructive" tool names that ALWAYS require user consent
     * regardless of context. These match the architect's recommendation to
     * quarantine bulk and irreversible operations.
     */
    public static final Set<String> DEFAULT_DESTRUCTIVE_TOOLS = Set.of(
        "delete_element",
        "merge_elements",
        "batch_rename",
        "batch_update_properties",
        "transition_oa_to_sa",
        "transition_sa_to_la",
        "transition_la_to_pa",
        "auto_transition_all"
    );

    private final boolean readOnlyMode;
    private final Set<String> writeToolNames;
    private final Set<String> destructiveToolNames;
    private final Set<String> adminBlockedTools;

    public WriteToolGate(boolean readOnlyMode,
                         Set<String> writeToolNames,
                         Set<String> destructiveToolNames,
                         Set<String> adminBlockedTools) {
        this.readOnlyMode = readOnlyMode;
        this.writeToolNames = writeToolNames != null ? writeToolNames : Set.of();
        this.destructiveToolNames = destructiveToolNames != null
            ? destructiveToolNames : DEFAULT_DESTRUCTIVE_TOOLS;
        this.adminBlockedTools = adminBlockedTools != null ? adminBlockedTools : Set.of();
    }

    /** Convenience: read-write mode, default destructive set, no admin blocks. */
    public static WriteToolGate readWrite(Set<String> writeToolNames) {
        return new WriteToolGate(false, writeToolNames, DEFAULT_DESTRUCTIVE_TOOLS, Set.of());
    }

    /** Convenience: read-only mode (all writes blocked). */
    public static WriteToolGate readOnly() {
        return new WriteToolGate(true, Set.of(), DEFAULT_DESTRUCTIVE_TOOLS, Set.of());
    }

    /**
     * Decides whether a tool call may proceed.
     *
     * @param toolName               the tool the LLM wants to call
     * @param suspiciousContextFound whether any tool result that influenced this
     *                               decision contained injection-pattern content
     *                               (use {@link ToolResultSanitizer#containsSuspiciousContent}
     *                               on prior results to compute this)
     */
    public Decision decide(String toolName, boolean suspiciousContextFound) {
        if (toolName == null) return Decision.BLOCKED_ADMIN;

        if (adminBlockedTools.contains(toolName)) {
            return Decision.BLOCKED_ADMIN;
        }

        boolean isWrite = writeToolNames.contains(toolName);
        boolean isDestructive = destructiveToolNames.contains(toolName);

        // In read-only mode we MUST block anything that looks like a write,
        // even if writeToolNames is empty. We use the tool-name prefix as a
        // safety net: tools whose names start with create_, update_, delete_,
        // batch_, transition_, allocate_, move_, merge_, set_, refresh_, or
        // auto_ are all potentially mutating.
        if (readOnlyMode) {
            if (isWrite || isDestructive || looksLikeWrite(toolName)) {
                return Decision.BLOCKED_READ_ONLY;
            }
            return Decision.ALLOW;
        }

        if (!isWrite && !isDestructive) {
            // Pure read tool - always allowed in read-write mode
            return Decision.ALLOW;
        }

        if (isDestructive) {
            // Destructive tools always require consent, even without suspicious context
            return Decision.REQUIRES_CONSENT;
        }

        if (suspiciousContextFound) {
            // Non-destructive write but the LLM was reasoning over potentially
            // injected content - require explicit consent
            return Decision.REQUIRES_CONSENT;
        }

        return Decision.ALLOW;
    }

    /**
     * Heuristic safety net for read-only mode: returns true if a tool name
     * suggests it might mutate the model. Used only as a fallback when
     * {@code writeToolNames} is empty (e.g. {@link #readOnly()} factory).
     */
    static boolean looksLikeWrite(String toolName) {
        if (toolName == null) return false;
        String lower = toolName.toLowerCase();
        return lower.startsWith("create_")
            || lower.startsWith("update_")
            || lower.startsWith("delete_")
            || lower.startsWith("batch_")
            || lower.startsWith("transition_")
            || lower.startsWith("allocate_")
            || lower.startsWith("move_")
            || lower.startsWith("merge_")
            || lower.startsWith("set_")
            || lower.startsWith("auto_")
            || lower.startsWith("apply_")
            || lower.startsWith("clone_")
            || lower.startsWith("import_")
            || lower.startsWith("propagate_")
            || lower.startsWith("reorder_");
    }

    /**
     * Renders a user-facing message explaining why consent is needed.
     * Used by the chat UI to populate the approval card.
     */
    public static String consentMessageFor(String toolName, boolean suspicious) {
        if (DEFAULT_DESTRUCTIVE_TOOLS.contains(toolName)) {
            return "The agent wants to run a destructive operation: "
                + toolName
                + ". This affects multiple model elements and is hard to undo. "
                + "Approve to proceed.";
        }
        if (suspicious) {
            return "The agent wants to run a write tool ("
                + toolName
                + ") and one of the values it just read from your model contains "
                + "phrasing that looks like a prompt injection. Please approve "
                + "to confirm this is what you intended.";
        }
        return "The agent wants to run a write tool: " + toolName + ". Approve?";
    }
}

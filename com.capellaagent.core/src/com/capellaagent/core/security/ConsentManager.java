package com.capellaagent.core.security;

/**
 * Strategy for obtaining user consent before a write or destructive tool
 * execution. Platform-specific implementations (e.g. a native SWT dialog)
 * live in UI bundles; headless tests supply their own.
 * <p>
 * The controller blocks on {@link #requestConsent} until the user answers,
 * so implementations running on a UI thread MUST marshal to that thread
 * synchronously.
 * <p>
 * <b>Security sprint F1/F2:</b> Replaces the old "error fed back to the LLM"
 * pattern with a real in-the-loop user gate. The {@code reasoning} parameter
 * is <em>untrusted</em> — it comes from the LLM and must never be rendered
 * as HTML or executed as code by the implementation.
 */
public interface ConsentManager {

    enum Decision {
        /** User explicitly approved this single invocation. */
        APPROVED,
        /** User approved and asked to remember the choice for this session. */
        APPROVED_REMEMBER,
        /** User denied. LLM receives a denial message and may retry another tool. */
        DENIED
    }

    /**
     * Requests user consent for a pending write or destructive tool.
     *
     * @param toolName tool identifier (e.g. {@code apply_architecture_diff})
     * @param category tool category tag used to render a badge (e.g.
     *                 {@code MODEL_WRITE}, {@code DESTRUCTIVE})
     * @param toolArgs JSON-formatted tool arguments for display in the dialog
     * @param reasoning untrusted LLM-supplied reasoning snippet. The
     *                  implementation must render this as plain text with
     *                  a clear "LLM reasoning (untrusted)" label.
     * @param destructive if true, the implementation MUST disable any
     *                    "remember my choice" affordance — destructive
     *                    tools always prompt.
     * @return the user's decision; never null
     */
    Decision requestConsent(String toolName,
                            String category,
                            String toolArgs,
                            String reasoning,
                            boolean destructive);

    /**
     * Null-object implementation that always denies. Used as a safe default
     * when no UI is registered (e.g. headless builds).
     */
    ConsentManager DENY_ALL = (name, cat, args, reason, destructive) -> Decision.DENIED;

    /**
     * Test/headless helper that always approves. Must not be used in
     * production builds; auto-approval defeats the entire gate.
     */
    ConsentManager ALLOW_ALL = (name, cat, args, reason, destructive) -> Decision.APPROVED;
}

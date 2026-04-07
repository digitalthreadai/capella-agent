package com.capellaagent.core.session;

import java.util.List;

/**
 * Abstraction for a named agent mode configuration.
 * <p>
 * {@link AgentMode} implements this interface. A future extension point
 * ({@code com.capellaagent.core.agentModes}) will allow third-party bundles
 * to contribute additional modes without modifying the core enum.
 */
public interface IAgentMode {

    /** Human-readable mode name shown in the UI dropdown. */
    String displayName();

    /** One-line description shown as a tooltip. */
    String shortDescription();

    /** The system prompt prepended to every chat turn in this mode. */
    String systemPrompt();

    /**
     * Tool categories this mode prefers. Empty = all categories available.
     * This is a suggestion to the tool filter, not a hard restriction.
     */
    List<String> preferredToolCategories();

    /** Example prompts for the empty-state starter grid. */
    List<String> starterPrompts();

    /**
     * Maximum tool-call iterations allowed per turn for this mode.
     * Generative modes need higher limits than conversational modes.
     * Default is 20 for GENERAL; ARCHITECT uses 40.
     */
    default int maxToolIterations() {
        return 20;
    }
}

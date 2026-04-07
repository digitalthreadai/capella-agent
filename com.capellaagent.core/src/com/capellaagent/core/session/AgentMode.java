package com.capellaagent.core.session;

import java.util.List;

/**
 * A named configuration preset for a chat session.
 * <p>
 * Replaces the "toolbar button mutates shared state" anti-pattern flagged by
 * the architect (Issue A10) and UX architect (Issue D2). Each mode specifies
 * a system prompt, a curated tool category list, and a set of starter prompts
 * that populate the chat empty state.
 * <p>
 * When the user switches mode, the caller should fork a new
 * {@link ConversationSession} (do not mutate the current one) so the old
 * thread's system prompt remains consistent. Mode switching is a session-level
 * fork, not a runtime mutation.
 *
 * <h2>Available modes</h2>
 * <ul>
 *   <li>{@link #GENERAL} — default assistant, all tools</li>
 *   <li>{@link #SUSTAINMENT} — fault diagnosis, sustainment engineering workflow</li>
 * </ul>
 * More modes will be added in the roadmap (Requirements Analyst, Model Reviewer,
 * Architect, etc.).
 */
public enum AgentMode {

    /** General-purpose assistant with all tool categories available. */
    GENERAL(
        "General Assistant",
        "General-purpose MBSE assistant using all available tools.",
        "You are a Capella MBSE assistant. Use tools to query and modify the "
        + "user's model. Never guess — call tools for real data. Cite element "
        + "UUIDs verbatim from tool results; never invent them.",
        List.of(), // empty = all categories
        List.of(
            "List all physical functions",
            "Show me the functional chains in this model",
            "What requirements does the Avionics System trace to?",
            "Create a new logical component called Sensor Hub"
        )),

    /**
     * Sustainment engineering mode. The system prompt instructs the agent to
     * diagnose faults by chaining {@code get_functional_chain},
     * {@code impact_analysis}, and {@code get_traceability} and to return
     * structured findings (subsystem / chain / components / requirements).
     */
    SUSTAINMENT(
        "Sustainment Engineer",
        "Diagnose faults using functional chains and impact analysis.",
        "You are a sustainment engineering assistant for an MBSE model in "
        + "Eclipse Capella. The user will describe a fault or symptom from "
        + "the field. Your job is to identify likely root causes by using "
        + "these tools in sequence:\n"
        + "  1. lookup_fault_symptom — match the symptom to subsystems and ATA chapter\n"
        + "  2. classify_fault_ata — get the standard ATA spec classification\n"
        + "  3. get_functional_chain — trace the affected functional chain\n"
        + "  4. impact_analysis — find all related components and exchanges\n"
        + "  5. get_traceability — find related requirements\n"
        + "\n"
        + "Always present findings in this format:\n"
        + "  Likely subsystem: <name>\n"
        + "  Affected functional chain: <name> (uuid)\n"
        + "  Components to inspect: bullet list with UUIDs\n"
        + "  Related requirements: bullet list\n"
        + "\n"
        + "Cite element UUIDs verbatim from tool results. Never invent UUIDs. "
        + "If you don't have a UUID for something, say 'unknown' instead of guessing.",
        List.of("model_read", "analysis", "model_write"),
        List.of(
            "The Seat TV in row 14 is rebooting every time we try to play a VOD movie. Where should I look?",
            "During an airline-imposed safety video broadcast, audio is silent on the cabin terminal but video plays fine. What functions are involved in audio routing for imposed broadcasts?",
            "The Available VOD Movies List on the seat-back display shows yesterday's titles. The Applications Server was rebooted. Which functions feed that list?",
            "Which requirements does the Cabin Management Unit implement?"
        ));

    private final String displayName;
    private final String shortDescription;
    private final String systemPrompt;
    private final List<String> preferredToolCategories;
    private final List<String> starterPrompts;

    AgentMode(String displayName,
              String shortDescription,
              String systemPrompt,
              List<String> preferredToolCategories,
              List<String> starterPrompts) {
        this.displayName = displayName;
        this.shortDescription = shortDescription;
        this.systemPrompt = systemPrompt;
        this.preferredToolCategories = preferredToolCategories;
        this.starterPrompts = starterPrompts;
    }

    /** Human-readable mode name for the UI dropdown (e.g. "Sustainment Engineer"). */
    public String displayName() {
        return displayName;
    }

    /** One-line description shown as a tooltip in the mode picker. */
    public String shortDescription() {
        return shortDescription;
    }

    /** The system prompt to prepend to any chat turn in this mode. */
    public String systemPrompt() {
        return systemPrompt;
    }

    /**
     * Tool categories this mode prefers to have visible. If empty, all
     * categories are available. The LLM can still call any registered tool —
     * this is a suggestion to the tool filter, not a hard restriction.
     */
    public List<String> preferredToolCategories() {
        return preferredToolCategories;
    }

    /**
     * Example prompts shown in the empty-state starter grid when this mode is
     * active. Clicking a starter fills the input (does not send).
     */
    public List<String> starterPrompts() {
        return starterPrompts;
    }
}

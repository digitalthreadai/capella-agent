package com.capellaagent.core.tools;

/**
 * Constants for tool categories, used to group tools by agent/functionality.
 * <p>
 * Categories are used by:
 * <ul>
 *   <li>{@link ToolRegistry#getTools(String...)} to filter tools by agent</li>
 *   <li>LLM providers to select which tools to offer in a conversation</li>
 *   <li>Security policies to control access (e.g., read-only mode blocks MODEL_WRITE)</li>
 * </ul>
 */
public final class ToolCategory {

    /** Read-only model queries (list, get, search, hierarchy, traceability) */
    public static final String MODEL_READ = "model_read";

    /** Model modifications (create, update, delete elements) */
    public static final String MODEL_WRITE = "model_write";

    /** Diagram operations (add elements to diagrams, refresh) */
    public static final String DIAGRAM = "diagram";

    /** Teamcenter PLM integration (search, import, link) */
    public static final String TEAMCENTER = "teamcenter";

    /** Simulation/CAE operations (run, extract params, propagate results) */
    public static final String SIMULATION = "simulation";

    /** Model analysis tools (validation, cycle detection, impact analysis) */
    public static final String ANALYSIS = "analysis";

    /** Model export tools (CSV, JSON, reports, traceability matrices) */
    public static final String EXPORT = "export";

    /** Layer transition tools (OA->SA, SA->LA, LA->PA, reconciliation) */
    public static final String TRANSITION = "transition";

    /** AI intelligence tools (explain, generate, Q&A) */
    public static final String AI_INTELLIGENCE = "ai_intelligence";

    /** Requirements management tools (import, link, coverage) */
    public static final String REQUIREMENTS = "requirements";

    private ToolCategory() {
        // Constants only
    }
}

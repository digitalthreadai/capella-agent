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

    private ToolCategory() {
        // Constants only
    }
}

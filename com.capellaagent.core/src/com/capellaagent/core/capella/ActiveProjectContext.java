package com.capellaagent.core.capella;

/**
 * Thread-local holder for the "active Capella project" the user picked in the
 * chat view's project dropdown.
 * <p>
 * The chat orchestration thread (see {@code ChatJob}) sets this before running
 * the tool loop and clears it in a {@code finally} block. Tools that need to
 * resolve a Sirius {@code Session} call {@link #get()} and pass the value down
 * to {@link CapellaModelService#getSession(String)}, which honors a non-null
 * name when multiple projects are open in the workspace.
 * <p>
 * Why a thread-local instead of a constructor parameter on every tool: the tool
 * registry instantiates tools once at bundle activation and reuses the
 * instances across requests. Threading a project name through 100+ tool
 * signatures would touch every tool file. A thread-local scoped to one
 * orchestration job is the smaller, safer change.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@code ChatJob.run()} reads the dropdown selection</li>
 *   <li>{@code ActiveProjectContext.set(name)} before the orchestration loop</li>
 *   <li>Each tool calls {@code AbstractCapellaTool.getActiveSession()}, which
 *       reads {@code ActiveProjectContext.get()} and forwards it to the model
 *       service</li>
 *   <li>{@code ActiveProjectContext.clear()} in {@code finally}, even on
 *       exception, so the thread does not leak state into the next job</li>
 * </ol>
 *
 * @since 1.0.0.beta1+1
 */
public final class ActiveProjectContext {

    private static final ThreadLocal<String> ACTIVE = new ThreadLocal<>();

    private ActiveProjectContext() {
        // utility
    }

    /**
     * Stores the active project name for the current thread. A {@code null} or
     * blank value clears the slot.
     *
     * @param projectName the user-visible Capella project name (matches
     *                    {@code Project.getName()}), or {@code null}
     */
    public static void set(String projectName) {
        if (projectName == null || projectName.isBlank()) {
            ACTIVE.remove();
        } else {
            ACTIVE.set(projectName);
        }
    }

    /**
     * Returns the active project name for the current thread, or {@code null}
     * if none has been set. Tools should treat {@code null} as "fall back to
     * single-session auto-detection".
     */
    public static String get() {
        return ACTIVE.get();
    }

    /**
     * Removes the thread-local entry. Always call this from a {@code finally}
     * block at the end of the orchestration job to avoid leaking state into
     * subsequent jobs that reuse the same worker thread.
     */
    public static void clear() {
        ACTIVE.remove();
    }
}

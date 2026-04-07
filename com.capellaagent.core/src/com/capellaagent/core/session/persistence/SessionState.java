package com.capellaagent.core.session.persistence;

/**
 * Recovery state of a persisted conversation session.
 * <p>
 * Recorded alongside the session JSON so that on next launch the controller
 * can decide whether to silently resume, prompt the user, or treat the
 * previous session as crashed.
 */
public enum SessionState {

    /**
     * Session ended normally. Safe to restore silently if the user opens it.
     */
    CLEAN,

    /**
     * The chat was mid-LLM-call when the session was last persisted, but no
     * write tools had been issued yet. Safe to offer a one-click Resume — no
     * Capella model state was mutated.
     */
    IN_FLIGHT_NO_WRITES,

    /**
     * The chat was mid-LLM-call AND at least one write tool had already been
     * committed when the session was last persisted. The user MUST be given a
     * forced choice (Continue / Show what happened / Start fresh) because
     * Capella model state was partially mutated.
     */
    IN_FLIGHT_WITH_WRITES,

    /**
     * A Java exception escaped the controller loop. The session is in an
     * unknown state and should be presented to the user with the error trail.
     */
    CRASHED
}

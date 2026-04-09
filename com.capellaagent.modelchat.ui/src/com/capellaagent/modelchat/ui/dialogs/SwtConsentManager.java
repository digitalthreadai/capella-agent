package com.capellaagent.modelchat.ui.dialogs;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.swt.widgets.Display;

import com.capellaagent.core.security.ConsentManager;

/**
 * SWT-backed {@link ConsentManager} used by the chat controller.
 * <p>
 * Opens a {@link WriteConsentDialog} on the UI thread and blocks the caller
 * (typically a worker thread) until the user answers. Honours the
 * "remember my choice" affordance at session scope by caching approved tool
 * names in memory — the cache is cleared whenever the view is disposed.
 * <p>
 * Destructive tool names are excluded from the remember cache; every
 * destructive invocation prompts anew.
 */
public final class SwtConsentManager implements ConsentManager {

    private final Set<String> rememberedApprovals = ConcurrentHashMap.newKeySet();

    @Override
    public Decision requestConsent(String toolName, String category,
                                   String toolArgs, String reasoning,
                                   boolean destructive) {
        if (!destructive && rememberedApprovals.contains(toolName)) {
            return Decision.APPROVED_REMEMBER;
        }

        Display display = Display.getDefault();
        Decision decision = WriteConsentDialog.openBlocking(
            display, null, toolName, category, toolArgs, reasoning, destructive);

        if (decision == Decision.APPROVED_REMEMBER && !destructive) {
            rememberedApprovals.add(toolName);
        }
        return decision;
    }

    /** Clears the session-scoped remember cache (e.g. when the view closes). */
    public void clearSessionMemory() {
        rememberedApprovals.clear();
    }
}

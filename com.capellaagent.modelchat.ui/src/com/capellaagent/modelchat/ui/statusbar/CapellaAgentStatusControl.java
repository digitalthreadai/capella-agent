package com.capellaagent.modelchat.ui.statusbar;

import org.eclipse.jface.action.ControlContribution;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.dialogs.PreferencesUtil;

import com.capellaagent.core.metering.TokenUsageTracker;
import com.capellaagent.core.metering.TokenUsageTracker.Listener;
import com.capellaagent.core.config.AgentConfiguration;

/**
 * Eclipse status-bar contribution that shows the active LLM provider and
 * running token count for the current session.
 * <p>
 * Displayed in {@code toolbar:org.eclipse.ui.trim.status2} (right side of
 * the status bar). Clicking the label opens the Capella Agent preference page.
 * <p>
 * <b>Thread safety:</b> {@link TokenUsageTracker#record()} fires on the
 * ChatJob worker thread. The listener body wraps all SWT updates in
 * {@link Display#asyncExec(Runnable)} to ensure UI access is on the UI thread.
 * <p>
 * <b>Leak prevention:</b> The listener is removed both in
 * {@link #dispose()} (normal Eclipse lifecycle) and via a
 * {@code DisposeListener} on the label widget (handles perspective switches
 * where the contribution container may not call dispose).
 */
public class CapellaAgentStatusControl extends ControlContribution {

    public static final String ID =
            "com.capellaagent.modelchat.ui.statusbar.CapellaAgentStatusControl";

    private Label statusLabel;
    private Listener tokenListener;
    private long totalTokens = 0;

    public CapellaAgentStatusControl() {
        super(ID);
    }

    @Override
    protected Control createControl(Composite parent) {
        statusLabel = new Label(parent, SWT.NONE);
        statusLabel.setCursor(parent.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
        statusLabel.setToolTipText("Capella Agent — click to open preferences");
        updateLabel();

        // Click → open preferences
        statusLabel.addListener(SWT.MouseUp, e -> {
            PreferencesUtil.createPreferenceDialogOn(
                    statusLabel.getShell(),
                    "com.capellaagent.preferences",
                    new String[]{"com.capellaagent.preferences"},
                    null).open();
        });

        // Subscribe to token usage updates
        tokenListener = (snapshot) -> {
            totalTokens = snapshot.totalInputTokens() + snapshot.totalOutputTokens();
            Display display = Display.getDefault();
            if (display != null && !display.isDisposed()) {
                display.asyncExec(() -> {
                    if (statusLabel != null && !statusLabel.isDisposed()) {
                        updateLabel();
                    }
                });
            }
        };
        TokenUsageTracker.getInstance().addListener(tokenListener);

        // Belt-and-braces: also remove listener when the label widget is disposed
        // (handles perspective switches where dispose() on the contribution may not fire)
        statusLabel.addDisposeListener(e ->
                TokenUsageTracker.getInstance().removeListener(tokenListener));

        return statusLabel;
    }

    private void updateLabel() {
        if (statusLabel == null || statusLabel.isDisposed()) return;
        String providerName;
        try {
            providerName = AgentConfiguration.getInstance().getLlmProviderId();
            // Capitalize first letter for display
            if (providerName != null && !providerName.isEmpty()) {
                providerName = Character.toUpperCase(providerName.charAt(0))
                        + providerName.substring(1);
            } else {
                providerName = "?";
            }
        } catch (Exception e) {
            providerName = "?";
        }

        String text;
        if (totalTokens == 0) {
            text = "🤖 Agent · " + providerName;
        } else {
            text = "🤖 Agent · " + providerName + " · " + totalTokens + " tokens";
        }
        statusLabel.setText(text);
        // Force layout update so the label doesn't get clipped after text change
        statusLabel.getParent().layout(true);
    }

    @Override
    public void dispose() {
        if (tokenListener != null) {
            TokenUsageTracker.getInstance().removeListener(tokenListener);
            tokenListener = null;
        }
        super.dispose();
    }
}

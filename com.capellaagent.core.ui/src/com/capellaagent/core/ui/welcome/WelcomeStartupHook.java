package com.capellaagent.core.ui.welcome;

import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.capellaagent.core.config.WelcomeWizardModel;

/**
 * Implements {@link IStartup} to launch the welcome wizard on the very first
 * run, before the user has configured a provider.
 * <p>
 * <b>Timing note:</b> {@link #earlyStartup()} fires before the workbench
 * window is guaranteed to exist. The actual wizard open is therefore wrapped
 * in {@link org.eclipse.swt.widgets.Display#asyncExec(Runnable)} so it runs
 * after the workbench window is fully initialized.
 * <p>
 * <b>First-run detection:</b> reads the {@code "welcome.wizard.completed"}
 * sentinel key from {@code com.capellaagent.core} preferences — NOT the
 * provider ID, which has a non-empty default and is therefore ambiguous.
 */
public class WelcomeStartupHook implements IStartup {

    @Override
    public void earlyStartup() {
        if (WelcomeWizardModel.isConfigured()) {
            // Already set up — do nothing
            return;
        }

        org.eclipse.swt.widgets.Display display = PlatformUI.getWorkbench().getDisplay();
        if (display == null || display.isDisposed()) return;

        display.asyncExec(() -> {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window == null) return;

            WelcomeWizard wizard = new WelcomeWizard();
            WizardDialog dialog = new WizardDialog(window.getShell(), wizard);
            dialog.setMinimumPageSize(500, 400);
            dialog.open();
        });
    }
}

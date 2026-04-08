package com.capellaagent.core.ui.welcome;

import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import com.capellaagent.core.config.WelcomeWizardModel;
import com.capellaagent.core.config.WelcomeWizardModel.ConnectionTestResult;

/**
 * Wizard page 3 — optionally test the connection.
 * <p>
 * The page is complete from the start so the user can always click Finish
 * and skip the test. The "Test Connection" button runs a quick ping to the
 * provider and shows a human-readable result.
 */
public class TestConnectionPage extends WizardPage {

    private final WelcomeWizardModel model;
    private Label statusLabel;
    private Button testButton;

    public TestConnectionPage(WelcomeWizardModel model) {
        super("testConnection");
        this.model = model;
        setTitle("Test Connection (Optional)");
        setDescription("Click Test Connection to verify your settings, or click Finish to skip.");
    }

    @Override
    public void createControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(2, false));

        testButton = new Button(container, SWT.PUSH);
        testButton.setText("Test Connection");
        testButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        testButton.addListener(SWT.Selection, e -> runTest());

        statusLabel = new Label(container, SWT.NONE);
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        statusLabel.setText(statusMessage(model.lastTestResult()));

        setControl(container);
        // Always completeable — user can skip
        setPageComplete(true);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible && statusLabel != null && !statusLabel.isDisposed()) {
            statusLabel.setText(statusMessage(ConnectionTestResult.UNTESTED));
            model.setLastTestResult(ConnectionTestResult.UNTESTED);
        }
    }

    private void runTest() {
        if (testButton != null) testButton.setEnabled(false);
        if (statusLabel != null) statusLabel.setText("Testing\u2026");
        model.setLastTestResult(ConnectionTestResult.TESTING);

        // Run on background thread to keep UI responsive
        new Thread(() -> {
            ConnectionTestResult result;
            try {
                com.capellaagent.core.llm.ILlmProvider provider =
                    com.capellaagent.core.llm.LlmProviderRegistry.getInstance()
                        .getProvider(model.selectedProviderId());
                if (provider == null) {
                    result = ConnectionTestResult.FAILURE_OTHER;
                } else {
                    // Quick ping — send a 1-token "ping" message using factory method
                    java.util.List<com.capellaagent.core.llm.LlmMessage> ping = java.util.List.of(
                        com.capellaagent.core.llm.LlmMessage.user("ping"));
                    com.capellaagent.core.llm.LlmRequestConfig cfg =
                        new com.capellaagent.core.llm.LlmRequestConfig(null, 0.1, 16, null);
                    provider.chat(ping, java.util.Collections.emptyList(), cfg);
                    result = ConnectionTestResult.SUCCESS;
                }
            } catch (Exception ex) {
                // SECURITY (A3): classify based on exception *type*, not raw
                // getMessage() substring matching — message text can contain
                // echoed prompts and credential fragments, and it's also
                // fragile (a provider rewording "auth" breaks detection).
                // ErrorMessageFilter logs the full exception at SEVERE.
                com.capellaagent.core.security.ErrorMessageFilter.safeUserMessage(ex);
                Throwable cursor = ex;
                result = ConnectionTestResult.FAILURE_OTHER;
                while (cursor != null) {
                    if (cursor instanceof com.capellaagent.core.llm.LlmException le) {
                        String code = le.getErrorCode();
                        if (com.capellaagent.core.llm.LlmException.ERR_AUTHENTICATION.equals(code)) {
                            result = ConnectionTestResult.FAILURE_AUTH;
                        } else if (com.capellaagent.core.llm.LlmException.ERR_CONNECTION.equals(code)) {
                            result = ConnectionTestResult.FAILURE_NETWORK;
                        }
                        break;
                    }
                    if (cursor instanceof java.net.UnknownHostException
                            || cursor instanceof java.net.http.HttpTimeoutException
                            || cursor instanceof java.io.IOException) {
                        result = ConnectionTestResult.FAILURE_NETWORK;
                        break;
                    }
                    cursor = cursor.getCause();
                }
            }
            final ConnectionTestResult finalResult = result;
            model.setLastTestResult(finalResult);
            getShell().getDisplay().asyncExec(() -> {
                if (statusLabel != null && !statusLabel.isDisposed()) {
                    statusLabel.setText(statusMessage(finalResult));
                }
                if (testButton != null && !testButton.isDisposed()) {
                    testButton.setEnabled(true);
                }
                getContainer().updateButtons();
            });
        }, "CapellaAgent-ConnectionTest").start();
    }

    private String statusMessage(ConnectionTestResult result) {
        return switch (result) {
            case UNTESTED        -> "Not tested yet.";
            case TESTING         -> "Testing\u2026";
            case SUCCESS         -> "\u2713 Connection successful!";
            case FAILURE_AUTH    -> "\u2717 Authentication failed \u2014 check your API key.";
            case FAILURE_NETWORK -> "\u2717 Network error \u2014 check your internet connection.";
            case FAILURE_OTHER   -> "\u2717 Test failed \u2014 check logs for details.";
        };
    }
}

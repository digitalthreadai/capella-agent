package com.capellaagent.core.ui.welcome;

import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.capellaagent.core.config.WelcomeWizardModel;
import com.capellaagent.core.config.WelcomeWizardModel.ProviderOption;

/**
 * Wizard page 2 — enter an API key.
 * <p>
 * Skipped automatically when an offline provider (Ollama) is selected, because
 * {@link #isPageComplete()} returns true immediately in that case and the
 * wizard framework calls {@link #setVisible(boolean)} with {@code false}.
 * <p>
 * The page title and description update dynamically in {@link #setVisible(boolean)}
 * to reflect the currently selected provider.
 */
public class ApiKeyPage extends WizardPage {

    private final WelcomeWizardModel model;
    private Text apiKeyField;

    public ApiKeyPage(WelcomeWizardModel model) {
        super("apiKey");
        this.model = model;
        setTitle("Enter API Key");
        setDescription("Paste your API key for the selected provider.");
    }

    @Override
    public void createControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(1, false));

        Label label = new Label(container, SWT.WRAP);
        label.setText("Paste your API key below. It is stored in Eclipse secure storage "
                + "and never sent anywhere except the provider's API endpoint.");
        label.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        new Label(container, SWT.NONE); // spacer

        apiKeyField = new Text(container, SWT.BORDER | SWT.PASSWORD | SWT.SINGLE);
        apiKeyField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        apiKeyField.setMessage("sk-... or gh... or gsk_...");
        apiKeyField.addModifyListener(e -> {
            model.setApiKey(apiKeyField.getText().trim());
            setPageComplete(!apiKeyField.getText().trim().isEmpty()
                    || !model.selectedProvider().requiresApiKey());
        });

        setControl(container);
        setPageComplete(!model.selectedProvider().requiresApiKey());
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            ProviderOption option = model.selectedProvider();
            setTitle("Enter API Key — " + option.displayName());
            if (!option.requiresApiKey()) {
                setDescription(option.displayName()
                        + " does not require an API key. Click Next to test the connection.");
            } else {
                setDescription("Paste your " + option.displayName() + " API key below.");
            }
            if (apiKeyField != null && !apiKeyField.isDisposed()) {
                apiKeyField.setEnabled(option.requiresApiKey());
                if (!option.requiresApiKey()) {
                    apiKeyField.setText("");
                }
                setPageComplete(!option.requiresApiKey()
                        || !apiKeyField.getText().trim().isEmpty());
            }
        }
    }
}

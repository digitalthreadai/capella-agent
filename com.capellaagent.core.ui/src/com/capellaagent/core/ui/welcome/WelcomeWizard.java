package com.capellaagent.core.ui.welcome;

import org.eclipse.jface.wizard.Wizard;

import com.capellaagent.core.config.WelcomeWizardModel;

/**
 * First-launch wizard that guides the user through provider selection,
 * API key entry, and connection testing.
 * <p>
 * Three pages: {@link ProviderPickPage} → {@link ApiKeyPage} (skipped for
 * Ollama) → {@link TestConnectionPage}. On finish the sentinel key
 * {@code welcome.wizard.completed} is written to Eclipse preferences so the
 * wizard does not reappear.
 */
public class WelcomeWizard extends Wizard {

    private final WelcomeWizardModel model = new WelcomeWizardModel();

    private ProviderPickPage providerPage;
    private ApiKeyPage apiKeyPage;
    private TestConnectionPage testPage;

    public WelcomeWizard() {
        setWindowTitle("Capella Agent Setup");
        setNeedsProgressMonitor(false);
    }

    @Override
    public void addPages() {
        providerPage = new ProviderPickPage(model);
        apiKeyPage   = new ApiKeyPage(model);
        testPage     = new TestConnectionPage(model);
        addPage(providerPage);
        addPage(apiKeyPage);
        addPage(testPage);
    }

    @Override
    public boolean canFinish() {
        // Allow finish from any page so the user can always skip
        return true;
    }

    @Override
    public boolean performFinish() {
        // Save provider + API key to AgentConfiguration
        try {
            com.capellaagent.core.config.AgentConfiguration cfg =
                com.capellaagent.core.config.AgentConfiguration.getInstance();
            cfg.setLlmProviderId(model.selectedProviderId());
            if (!model.apiKey().isEmpty()) {
                cfg.setApiKey(model.selectedProviderId(), model.apiKey());
            }
        } catch (Exception e) {
            // Non-fatal — user can configure via Preferences
        }
        // Write sentinel to suppress future launches
        model.finish();
        return true;
    }

    /** Returns the shared wizard model (for page→page communication). */
    public WelcomeWizardModel getModel() {
        return model;
    }
}

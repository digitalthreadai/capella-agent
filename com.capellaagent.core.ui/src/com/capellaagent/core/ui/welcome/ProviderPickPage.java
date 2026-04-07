package com.capellaagent.core.ui.welcome;

import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import com.capellaagent.core.config.WelcomeWizardModel;
import com.capellaagent.core.config.WelcomeWizardModel.ProviderOption;

/**
 * Wizard page 1 — pick an LLM provider.
 * <p>
 * Iterates {@link WelcomeWizardModel#PROVIDERS} to create a radio button
 * per provider with a one-line hint. Changing selection updates the model
 * immediately so {@link ApiKeyPage} can read the new choice.
 */
public class ProviderPickPage extends WizardPage {

    private final WelcomeWizardModel model;

    public ProviderPickPage(WelcomeWizardModel model) {
        super("providerPick");
        this.model = model;
        setTitle("Choose an LLM Provider");
        setDescription("Select the AI provider you want to use with Capella Agent.");
    }

    @Override
    public void createControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(1, false));

        Label intro = new Label(container, SWT.WRAP);
        intro.setText("Capella Agent supports multiple AI providers. "
                + "You can change this at any time in Window → Preferences → Capella Agent.");
        intro.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        new Label(container, SWT.NONE); // spacer

        // One radio button per provider
        Button firstButton = null;
        for (ProviderOption option : WelcomeWizardModel.PROVIDERS) {
            Button radio = new Button(container, SWT.RADIO);
            radio.setText(option.displayName());
            radio.setToolTipText(option.oneLineHint());
            radio.setData(option);

            Label hint = new Label(container, SWT.NONE);
            hint.setText("    " + option.oneLineHint());
            GridData hintData = new GridData(SWT.FILL, SWT.TOP, true, false);
            hintData.horizontalIndent = 16;
            hint.setLayoutData(hintData);

            // Pre-select current model selection
            if (option.id().equals(model.selectedProviderId())) {
                radio.setSelection(true);
            }

            radio.addListener(SWT.Selection, e -> {
                if (radio.getSelection()) {
                    model.setSelectedProviderId(option.id());
                    // Refresh next page state
                    getContainer().updateButtons();
                }
            });

            if (firstButton == null) firstButton = radio;
        }

        setControl(container);
        setPageComplete(true);
    }
}

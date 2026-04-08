package com.capellaagent.core.ui.preferences;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.dialogs.MessageDialog;

import com.capellaagent.core.config.AgentConfiguration;
import com.capellaagent.core.llm.ILlmProvider;
import com.capellaagent.core.llm.LlmMessage;
import com.capellaagent.core.llm.LlmProviderRegistry;
import com.capellaagent.core.llm.LlmRequestConfig;
import com.capellaagent.core.llm.LlmResponse;

/**
 * Main preference page for the Capella Agent ecosystem.
 * <p>
 * Accessible via Window &rarr; Preferences &rarr; Capella Agent.
 * Provides settings for LLM provider selection, API keys,
 * model configuration, and security options.
 */
public class CapellaAgentPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

    /** Provider IDs matching the LLM provider registry. */
    private static final String[] PROVIDER_IDS = {
        "anthropic", "openai", "groq", "deepseek", "mistral",
        "openrouter", "gemini", "ollama", "github-models", "custom"
    };

    /** Display labels for the provider dropdown. */
    private static final String[] PROVIDER_LABELS = {
        "Claude (Anthropic)", "OpenAI / Azure", "Groq Cloud",
        "DeepSeek", "Mistral AI", "OpenRouter", "Google Gemini",
        "Ollama (Local)", "GitHub Models (Free)", "Custom Endpoint"
    };

    // --- UI widgets ---
    private Combo providerCombo;
    private Text apiKeyText;
    private Text modelIdText;
    private Spinner temperatureSpinner;
    private Spinner maxTokensSpinner;
    private Group customEndpointGroup;
    private Text customUrlText;
    private Text customModelText;
    private Button readOnlyRadio;
    private Button readWriteRadio;
    private Button auditCheckbox;

    @Override
    public void init(IWorkbench workbench) {
        setDescription("Configure AI agent settings for the Capella Agent ecosystem.");
    }

    @Override
    protected Control createContents(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(1, false));
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        createLlmSettingsGroup(container);
        createCustomEndpointGroup(container);
        createSecurityGroup(container);

        loadPreferences();
        updateCustomEndpointVisibility();

        return container;
    }

    /**
     * Creates the LLM Settings group with provider, API key, model, temperature, and max tokens.
     */
    private void createLlmSettingsGroup(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("LLM Provider Settings");
        group.setLayout(new GridLayout(2, false));
        group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // Provider dropdown
        new Label(group, SWT.NONE).setText("Provider:");
        providerCombo = new Combo(group, SWT.READ_ONLY | SWT.DROP_DOWN);
        providerCombo.setItems(PROVIDER_LABELS);
        providerCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        providerCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateCustomEndpointVisibility();
            }
        });

        // API Key (password-masked)
        new Label(group, SWT.NONE).setText("API Key:");
        apiKeyText = new Text(group, SWT.BORDER | SWT.PASSWORD);
        apiKeyText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        apiKeyText.setMessage("Enter API key (stored securely)");

        // Model ID (optional override)
        new Label(group, SWT.NONE).setText("Model ID (optional):");
        modelIdText = new Text(group, SWT.BORDER);
        modelIdText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        modelIdText.setMessage("Leave blank for provider default");

        // Temperature (0.0 - 2.0, using integer spinner * 0.1)
        new Label(group, SWT.NONE).setText("Temperature:");
        temperatureSpinner = new Spinner(group, SWT.BORDER);
        temperatureSpinner.setMinimum(0);
        temperatureSpinner.setMaximum(20);
        temperatureSpinner.setDigits(1);
        temperatureSpinner.setIncrement(1);
        temperatureSpinner.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        // Max Tokens
        new Label(group, SWT.NONE).setText("Max Tokens:");
        maxTokensSpinner = new Spinner(group, SWT.BORDER);
        maxTokensSpinner.setMinimum(256);
        maxTokensSpinner.setMaximum(131072);
        maxTokensSpinner.setIncrement(1024);
        maxTokensSpinner.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        // Test Connection button
        new Label(group, SWT.NONE); // spacer
        Button testButton = new Button(group, SWT.PUSH);
        testButton.setText("Test Connection");
        testButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        testButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                testConnection();
            }
        });
    }

    /**
     * Creates the Custom Endpoint group (visible only when provider = "custom").
     */
    private void createCustomEndpointGroup(Composite parent) {
        customEndpointGroup = new Group(parent, SWT.NONE);
        customEndpointGroup.setText("Custom Endpoint (OpenAI-Compatible)");
        customEndpointGroup.setLayout(new GridLayout(2, false));
        customEndpointGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        new Label(customEndpointGroup, SWT.NONE).setText("Endpoint URL:");
        customUrlText = new Text(customEndpointGroup, SWT.BORDER);
        customUrlText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        customUrlText.setMessage("https://api.example.com/v1/chat/completions");

        new Label(customEndpointGroup, SWT.NONE).setText("Model Name:");
        customModelText = new Text(customEndpointGroup, SWT.BORDER);
        customModelText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        customModelText.setMessage("model-name");
    }

    /**
     * Creates the Security group with access mode and audit toggle.
     */
    private void createSecurityGroup(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Security");
        group.setLayout(new GridLayout(2, false));
        group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        new Label(group, SWT.NONE).setText("Access Mode:");
        Composite radioContainer = new Composite(group, SWT.NONE);
        radioContainer.setLayout(new GridLayout(2, false));
        readOnlyRadio = new Button(radioContainer, SWT.RADIO);
        readOnlyRadio.setText("Read Only");
        readWriteRadio = new Button(radioContainer, SWT.RADIO);
        readWriteRadio.setText("Read / Write");

        new Label(group, SWT.NONE); // spacer
        auditCheckbox = new Button(group, SWT.CHECK);
        auditCheckbox.setText("Enable audit logging for all agent operations");
    }

    /**
     * Shows/hides the custom endpoint group based on selected provider.
     */
    private void updateCustomEndpointVisibility() {
        int index = providerCombo.getSelectionIndex();
        boolean isCustom = index >= 0 && "custom".equals(PROVIDER_IDS[index]);
        customEndpointGroup.setVisible(isCustom);
        ((GridData) customEndpointGroup.getLayoutData()).exclude = !isCustom;
        customEndpointGroup.getParent().layout(true, true);
    }

    /**
     * Loads current preferences into UI widgets.
     */
    private void loadPreferences() {
        AgentConfiguration config = AgentConfiguration.getInstance();

        // Provider
        String providerId = config.getLlmProviderId();
        for (int i = 0; i < PROVIDER_IDS.length; i++) {
            if (PROVIDER_IDS[i].equals(providerId)) {
                providerCombo.select(i);
                break;
            }
        }
        if (providerCombo.getSelectionIndex() < 0) {
            providerCombo.select(0); // default to Claude
        }

        // API Key — SECURITY (A5): never pre-populate the password field with
        // the stored key. Even SWT.PASSWORD widgets keep the plaintext in the
        // widget's internal buffer, and the value lingers in JVM heap dumps,
        // screen-reader accessibility APIs, and copy/paste buffers. Show a
        // placeholder hint instead; only write on performOk() if the user
        // actually typed something.
        String storedKey = config.getApiKey(providerId);
        apiKeyText.setText("");
        if (storedKey != null && !storedKey.isEmpty()) {
            apiKeyText.setMessage("\u2022\u2022\u2022\u2022\u2022\u2022 (saved — leave blank to keep)");
        } else {
            apiKeyText.setMessage("Paste your API key");
        }

        // Model
        modelIdText.setText(config.getLlmModelId());

        // Temperature (stored as double, spinner uses int * 10)
        temperatureSpinner.setSelection((int) (config.getLlmTemperature() * 10));

        // Max Tokens
        maxTokensSpinner.setSelection(config.getLlmMaxTokens());

        // Custom Endpoint
        customUrlText.setText(config.getCustomEndpointUrl());
        customModelText.setText(config.getCustomEndpointModel());

        // Security
        String accessMode = config.getSecurityAccessMode();
        readOnlyRadio.setSelection("READ_ONLY".equals(accessMode));
        readWriteRadio.setSelection("READ_WRITE".equals(accessMode));
        auditCheckbox.setSelection(config.isAuditEnabled());
    }

    @Override
    public boolean performOk() {
        savePreferences();
        return true;
    }

    @Override
    protected void performApply() {
        savePreferences();
    }

    @Override
    protected void performDefaults() {
        providerCombo.select(0); // Claude
        apiKeyText.setText("");
        modelIdText.setText("");
        temperatureSpinner.setSelection(3); // 0.3
        maxTokensSpinner.setSelection(4096);
        customUrlText.setText("");
        customModelText.setText("");
        readOnlyRadio.setSelection(true);
        readWriteRadio.setSelection(false);
        auditCheckbox.setSelection(true);
        updateCustomEndpointVisibility();
        super.performDefaults();
    }

    /**
     * Saves all preference values to Eclipse preferences and secure storage.
     */
    private void savePreferences() {
        AgentConfiguration config = AgentConfiguration.getInstance();

        int index = providerCombo.getSelectionIndex();
        String providerId = index >= 0 ? PROVIDER_IDS[index] : "anthropic";

        config.setLlmProviderId(providerId);
        config.setLlmModelId(modelIdText.getText().trim());
        config.setLlmTemperature(temperatureSpinner.getSelection() / 10.0);
        config.setLlmMaxTokens(maxTokensSpinner.getSelection());
        config.setCustomEndpointUrl(customUrlText.getText().trim());
        config.setCustomEndpointModel(customModelText.getText().trim());

        // Save API key to secure storage
        String apiKey = apiKeyText.getText().trim();
        if (!apiKey.isEmpty()) {
            config.setApiKey(providerId, apiKey);
        }

        // Security
        config.setSecurityAccessMode(readOnlyRadio.getSelection() ? "READ_ONLY" : "READ_WRITE");
        config.setAuditEnabled(auditCheckbox.getSelection());
    }

    /**
     * Tests the LLM connection with the current settings.
     */
    private void testConnection() {
        try {
            // Save current settings temporarily
            savePreferences();

            // Get the active provider
            ILlmProvider provider = LlmProviderRegistry.getInstance().getActiveProvider();

            // Send a simple test message
            List<LlmMessage> messages = new ArrayList<>();
            messages.add(LlmMessage.user("Say 'Connection successful' in one word."));
            LlmRequestConfig config = new LlmRequestConfig(null, 0.1, 50, null);
            LlmResponse response = provider.chat(messages, Collections.emptyList(), config);

            if (response != null && response.hasTextContent()) {
                MessageDialog.openInformation(getShell(), "Test Connection",
                    "Connection successful!\n\n"
                    + "Provider: " + provider.getDisplayName() + "\n"
                    + "Response: " + response.getTextContent().substring(0,
                        Math.min(100, response.getTextContent().length())));
            } else {
                MessageDialog.openWarning(getShell(), "Test Connection",
                    "Connection established but no response received.");
            }
        } catch (Exception e) {
            // SECURITY (A3): do not echo raw exception messages — they can
            // contain echoed prompts, stack frame data, or credential fragments
            // leaked from the provider's error body. ErrorMessageFilter logs
            // the full exception at SEVERE and returns a safe short string.
            MessageDialog.openError(getShell(), "Test Connection Failed",
                "Could not connect to LLM provider.\n\n"
                + com.capellaagent.core.security.ErrorMessageFilter.safeUserMessage(e));
        }
    }
}

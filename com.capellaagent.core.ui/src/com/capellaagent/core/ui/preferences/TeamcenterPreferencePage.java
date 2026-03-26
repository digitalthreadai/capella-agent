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
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.capellaagent.core.config.AgentConfiguration;

/**
 * Preference page for Teamcenter Active Workspace connection settings.
 * <p>
 * Accessible via Window &rarr; Preferences &rarr; Capella Agent &rarr; Teamcenter.
 */
public class TeamcenterPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

    // Preference keys (stored under com.capellaagent.core node)
    private static final String KEY_TC_GATEWAY_URL = "tc.gateway.url";
    private static final String KEY_TC_AUTH_METHOD = "tc.auth.method";
    private static final String KEY_TC_USERNAME = "tc.username";

    private static final String[] AUTH_METHODS = { "Basic", "SSO" };

    private Text gatewayUrlText;
    private Combo authMethodCombo;
    private Text usernameText;
    private Text passwordText;

    @Override
    public void init(IWorkbench workbench) {
        setDescription("Configure Siemens Teamcenter Active Workspace connection.");
    }

    @Override
    protected Control createContents(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(1, false));
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        createConnectionGroup(container);
        createCredentialsGroup(container);

        loadPreferences();
        return container;
    }

    private void createConnectionGroup(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Connection");
        group.setLayout(new GridLayout(2, false));
        group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        new Label(group, SWT.NONE).setText("Gateway URL:");
        gatewayUrlText = new Text(group, SWT.BORDER);
        gatewayUrlText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        gatewayUrlText.setMessage("https://tc-server:7001/tc");

        new Label(group, SWT.NONE).setText("Auth Method:");
        authMethodCombo = new Combo(group, SWT.READ_ONLY | SWT.DROP_DOWN);
        authMethodCombo.setItems(AUTH_METHODS);
        authMethodCombo.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        // Test Connection button
        new Label(group, SWT.NONE); // spacer
        Button testButton = new Button(group, SWT.PUSH);
        testButton.setText("Test Connection");
        testButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                testTcConnection();
            }
        });
    }

    private void createCredentialsGroup(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Credentials");
        group.setLayout(new GridLayout(2, false));
        group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        new Label(group, SWT.NONE).setText("Username:");
        usernameText = new Text(group, SWT.BORDER);
        usernameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(group, SWT.NONE).setText("Password:");
        passwordText = new Text(group, SWT.BORDER | SWT.PASSWORD);
        passwordText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        passwordText.setMessage("Stored in Eclipse Secure Storage");
    }

    private void loadPreferences() {
        AgentConfiguration config = AgentConfiguration.getInstance();
        var prefs = org.eclipse.core.runtime.preferences.InstanceScope.INSTANCE
                .getNode(AgentConfiguration.QUALIFIER);

        gatewayUrlText.setText(prefs.get(KEY_TC_GATEWAY_URL, ""));
        usernameText.setText(prefs.get(KEY_TC_USERNAME, ""));

        String authMethod = prefs.get(KEY_TC_AUTH_METHOD, "Basic");
        for (int i = 0; i < AUTH_METHODS.length; i++) {
            if (AUTH_METHODS[i].equals(authMethod)) {
                authMethodCombo.select(i);
                break;
            }
        }
        if (authMethodCombo.getSelectionIndex() < 0) {
            authMethodCombo.select(0);
        }

        // Load password from secure storage
        String tcPassword = config.getApiKey("teamcenter");
        if (tcPassword != null) {
            passwordText.setText(tcPassword);
        }
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
        gatewayUrlText.setText("");
        authMethodCombo.select(0);
        usernameText.setText("");
        passwordText.setText("");
        super.performDefaults();
    }

    private void savePreferences() {
        var prefs = org.eclipse.core.runtime.preferences.InstanceScope.INSTANCE
                .getNode(AgentConfiguration.QUALIFIER);

        prefs.put(KEY_TC_GATEWAY_URL, gatewayUrlText.getText().trim());
        prefs.put(KEY_TC_AUTH_METHOD,
                authMethodCombo.getSelectionIndex() >= 0
                    ? AUTH_METHODS[authMethodCombo.getSelectionIndex()]
                    : "Basic");
        prefs.put(KEY_TC_USERNAME, usernameText.getText().trim());

        try {
            prefs.flush();
        } catch (org.osgi.service.prefs.BackingStoreException e) {
            // Log but don't block
        }

        // Save password to secure storage
        String password = passwordText.getText().trim();
        if (!password.isEmpty()) {
            AgentConfiguration.getInstance().setApiKey("teamcenter", password);
        }
    }

    private void testTcConnection() {
        org.eclipse.jface.dialogs.MessageDialog.openInformation(
            getShell(),
            "Test Teamcenter Connection",
            "Connection test is not yet implemented.\n\n"
            + "Gateway: " + gatewayUrlText.getText() + "\n"
            + "Auth: " + AUTH_METHODS[authMethodCombo.getSelectionIndex()] + "\n"
            + "User: " + usernameText.getText()
        );
    }
}

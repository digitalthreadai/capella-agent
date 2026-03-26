package com.capellaagent.core.ui.preferences;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

/**
 * Preference page for Simulation / CAE engine settings.
 * <p>
 * Accessible via Window &rarr; Preferences &rarr; Capella Agent &rarr; Simulation.
 */
public class SimulationPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

    /** Preference node for simulation settings. */
    private static final String SIM_QUALIFIER = "com.capellaagent.simulation";

    // Preference keys (matching SimulationPreferences in the simulation bundle)
    private static final String KEY_MATLAB_PATH = "matlab.path";
    private static final String KEY_WORKING_DIR = "matlab.working.dir";
    private static final String KEY_TIMEOUT = "matlab.timeout.seconds";

    private static final String DEFAULT_MATLAB_PATH = "";
    private static final String DEFAULT_WORKING_DIR = "";
    private static final int DEFAULT_TIMEOUT = 300;

    private Text matlabPathText;
    private Text workingDirText;
    private Spinner timeoutSpinner;

    @Override
    public void init(IWorkbench workbench) {
        setDescription("Configure simulation engine integration (MATLAB/Simulink, FMI).");
    }

    @Override
    protected Control createContents(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(1, false));
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        createMatlabGroup(container);

        loadPreferences();
        return container;
    }

    private void createMatlabGroup(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("MATLAB / Simulink");
        group.setLayout(new GridLayout(3, false));
        group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // MATLAB installation path
        new Label(group, SWT.NONE).setText("MATLAB Path:");
        matlabPathText = new Text(group, SWT.BORDER);
        matlabPathText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        matlabPathText.setMessage("C:\\Program Files\\MATLAB\\R2024b");
        Button browseMatlab = new Button(group, SWT.PUSH);
        browseMatlab.setText("Browse...");
        browseMatlab.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                DirectoryDialog dialog = new DirectoryDialog(getShell());
                dialog.setMessage("Select MATLAB installation directory");
                String path = dialog.open();
                if (path != null) {
                    matlabPathText.setText(path);
                }
            }
        });

        // Working directory
        new Label(group, SWT.NONE).setText("Working Directory:");
        workingDirText = new Text(group, SWT.BORDER);
        workingDirText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        workingDirText.setMessage("Directory for simulation temp files");
        Button browseWorkDir = new Button(group, SWT.PUSH);
        browseWorkDir.setText("Browse...");
        browseWorkDir.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                DirectoryDialog dialog = new DirectoryDialog(getShell());
                dialog.setMessage("Select simulation working directory");
                String path = dialog.open();
                if (path != null) {
                    workingDirText.setText(path);
                }
            }
        });

        // Timeout
        new Label(group, SWT.NONE).setText("Timeout (seconds):");
        timeoutSpinner = new Spinner(group, SWT.BORDER);
        timeoutSpinner.setMinimum(10);
        timeoutSpinner.setMaximum(7200);
        timeoutSpinner.setIncrement(30);
        timeoutSpinner.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        new Label(group, SWT.NONE); // spacer for 3rd column
    }

    private void loadPreferences() {
        var prefs = org.eclipse.core.runtime.preferences.InstanceScope.INSTANCE
                .getNode(SIM_QUALIFIER);

        matlabPathText.setText(prefs.get(KEY_MATLAB_PATH, DEFAULT_MATLAB_PATH));
        workingDirText.setText(prefs.get(KEY_WORKING_DIR, DEFAULT_WORKING_DIR));
        timeoutSpinner.setSelection(prefs.getInt(KEY_TIMEOUT, DEFAULT_TIMEOUT));
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
        matlabPathText.setText(DEFAULT_MATLAB_PATH);
        workingDirText.setText(DEFAULT_WORKING_DIR);
        timeoutSpinner.setSelection(DEFAULT_TIMEOUT);
        super.performDefaults();
    }

    private void savePreferences() {
        var prefs = org.eclipse.core.runtime.preferences.InstanceScope.INSTANCE
                .getNode(SIM_QUALIFIER);

        prefs.put(KEY_MATLAB_PATH, matlabPathText.getText().trim());
        prefs.put(KEY_WORKING_DIR, workingDirText.getText().trim());
        prefs.putInt(KEY_TIMEOUT, timeoutSpinner.getSelection());

        try {
            prefs.flush();
        } catch (org.osgi.service.prefs.BackingStoreException e) {
            // Log but don't block
        }
    }
}

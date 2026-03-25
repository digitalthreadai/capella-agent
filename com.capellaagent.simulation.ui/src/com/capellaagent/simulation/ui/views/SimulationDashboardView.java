package com.capellaagent.simulation.ui.views;

import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import com.capellaagent.simulation.SimActivator;
import com.capellaagent.simulation.SimToolRegistrar;
import com.capellaagent.simulation.bridge.ISimulationEngine;
import com.capellaagent.simulation.bridge.SimulationEngineException;
import com.capellaagent.simulation.bridge.SimulationResult;
import com.capellaagent.simulation.orchestrator.SimulationConfig;
import com.capellaagent.simulation.orchestrator.SimulationOrchestrator;

/**
 * Eclipse ViewPart providing a dashboard for simulation execution and monitoring.
 * <p>
 * The view is organized into three sections:
 * <ol>
 *   <li><strong>Top:</strong> Engine selection, model path, and Run button</li>
 *   <li><strong>Middle:</strong> Parameter table showing extracted model values</li>
 *   <li><strong>Bottom:</strong> Results table showing simulation outputs</li>
 * </ol>
 * <p>
 * Simulation execution is performed in an Eclipse Job to avoid blocking the UI thread.
 * Progress is reported through a progress bar.
 */
public class SimulationDashboardView extends ViewPart {

    /** The unique view identifier. */
    public static final String VIEW_ID = "com.capellaagent.simulation.ui.views.SimulationDashboardView";

    private Combo engineCombo;
    private Text modelPathText;
    private Button runButton;
    private Table parameterTable;
    private Table resultTable;
    private ProgressBar progressBar;
    private Label statusLabel;

    @Override
    public void createPartControl(Composite parent) {
        Composite main = new Composite(parent, SWT.NONE);
        GridLayout mainLayout = new GridLayout(1, false);
        mainLayout.marginWidth = 8;
        mainLayout.marginHeight = 8;
        main.setLayout(mainLayout);

        createTopSection(main);
        createParameterSection(main);
        createResultSection(main);
        createStatusSection(main);

        populateEngineCombo();
    }

    /**
     * Creates the top control section with engine selection, model path, and run button.
     */
    private void createTopSection(Composite parent) {
        Composite top = new Composite(parent, SWT.NONE);
        top.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout topLayout = new GridLayout(5, false);
        topLayout.marginWidth = 0;
        top.setLayout(topLayout);

        // Engine selection
        Label engineLabel = new Label(top, SWT.NONE);
        engineLabel.setText("Engine:");

        engineCombo = new Combo(top, SWT.READ_ONLY | SWT.DROP_DOWN);
        engineCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

        // Model path
        Label modelLabel = new Label(top, SWT.NONE);
        modelLabel.setText("Model:");

        modelPathText = new Text(top, SWT.BORDER | SWT.SINGLE);
        modelPathText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        modelPathText.setMessage("Path to simulation model file...");

        // Run button
        runButton = new Button(top, SWT.PUSH);
        runButton.setText("Run");
        GridData runData = new GridData(SWT.END, SWT.CENTER, false, false);
        runData.widthHint = 80;
        runButton.setLayoutData(runData);

        runButton.addListener(SWT.Selection, e -> runSimulation());
    }

    /**
     * Creates the parameter table section showing input parameters extracted from the model.
     */
    private void createParameterSection(Composite parent) {
        Label paramLabel = new Label(parent, SWT.NONE);
        paramLabel.setText("Parameters (from Capella model):");
        paramLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        parameterTable = new Table(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
        parameterTable.setHeaderVisible(true);
        parameterTable.setLinesVisible(true);
        GridData paramTableData = new GridData(SWT.FILL, SWT.FILL, true, true);
        paramTableData.heightHint = 150;
        parameterTable.setLayoutData(paramTableData);

        TableColumn nameCol = new TableColumn(parameterTable, SWT.NONE);
        nameCol.setText("Parameter Name");
        nameCol.setWidth(200);

        TableColumn valueCol = new TableColumn(parameterTable, SWT.NONE);
        valueCol.setText("Value");
        valueCol.setWidth(150);

        TableColumn sourceCol = new TableColumn(parameterTable, SWT.NONE);
        sourceCol.setText("Source Element");
        sourceCol.setWidth(250);
    }

    /**
     * Creates the results table section showing simulation outputs.
     */
    private void createResultSection(Composite parent) {
        Label resultLabel = new Label(parent, SWT.NONE);
        resultLabel.setText("Results:");
        resultLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        resultTable = new Table(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
        resultTable.setHeaderVisible(true);
        resultTable.setLinesVisible(true);
        GridData resultTableData = new GridData(SWT.FILL, SWT.FILL, true, true);
        resultTableData.heightHint = 150;
        resultTable.setLayoutData(resultTableData);

        TableColumn outputNameCol = new TableColumn(resultTable, SWT.NONE);
        outputNameCol.setText("Output Name");
        outputNameCol.setWidth(200);

        TableColumn outputValueCol = new TableColumn(resultTable, SWT.NONE);
        outputValueCol.setText("Value");
        outputValueCol.setWidth(150);

        TableColumn statusCol = new TableColumn(resultTable, SWT.NONE);
        statusCol.setText("Status");
        statusCol.setWidth(100);
    }

    /**
     * Creates the status bar with progress indicator.
     */
    private void createStatusSection(Composite parent) {
        Composite statusComposite = new Composite(parent, SWT.NONE);
        statusComposite.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
        GridLayout statusLayout = new GridLayout(2, false);
        statusLayout.marginWidth = 0;
        statusComposite.setLayout(statusLayout);

        progressBar = new ProgressBar(statusComposite, SWT.SMOOTH);
        progressBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        progressBar.setMinimum(0);
        progressBar.setMaximum(100);

        statusLabel = new Label(statusComposite, SWT.NONE);
        statusLabel.setText("Ready");
        GridData statusData = new GridData(SWT.END, SWT.CENTER, false, false);
        statusData.widthHint = 200;
        statusLabel.setLayoutData(statusData);
    }

    /**
     * Populates the engine combo box with registered simulation engines.
     */
    private void populateEngineCombo() {
        SimActivator activator = SimActivator.getDefault();
        if (activator == null || activator.getToolRegistrar() == null) {
            engineCombo.add("(no engines available)");
            engineCombo.select(0);
            runButton.setEnabled(false);
            return;
        }

        Map<String, ISimulationEngine> engines = activator.getToolRegistrar().getEngineRegistry();
        if (engines.isEmpty()) {
            engineCombo.add("(no engines available)");
            engineCombo.select(0);
            runButton.setEnabled(false);
            return;
        }

        for (Map.Entry<String, ISimulationEngine> entry : engines.entrySet()) {
            ISimulationEngine engine = entry.getValue();
            String label = engine.getDisplayName();
            if (!engine.isAvailable()) {
                label += " (unavailable)";
            }
            engineCombo.add(label);
            engineCombo.setData(String.valueOf(engineCombo.getItemCount() - 1), entry.getKey());
        }
        engineCombo.select(0);
    }

    /**
     * Runs the simulation in a background Eclipse Job.
     */
    private void runSimulation() {
        String modelPath = modelPathText.getText().trim();
        if (modelPath.isEmpty()) {
            statusLabel.setText("Error: No model path specified");
            return;
        }

        int selIndex = engineCombo.getSelectionIndex();
        if (selIndex < 0) {
            statusLabel.setText("Error: No engine selected");
            return;
        }
        String engineId = (String) engineCombo.getData(String.valueOf(selIndex));
        if (engineId == null) {
            statusLabel.setText("Error: Invalid engine selection");
            return;
        }

        // Disable controls during execution
        setRunning(true);

        Job simulationJob = new Job("Running Simulation") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    SimActivator activator = SimActivator.getDefault();
                    if (activator == null) {
                        updateStatus("Error: Simulation plugin not active");
                        return Status.CANCEL_STATUS;
                    }

                    Map<String, ISimulationEngine> engines =
                            activator.getToolRegistrar().getEngineRegistry();
                    ISimulationEngine engine = engines.get(engineId);
                    if (engine == null) {
                        updateStatus("Error: Engine not found: " + engineId);
                        return Status.CANCEL_STATUS;
                    }

                    // Build a minimal config
                    SimulationConfig config = new SimulationConfig();
                    config.setEngineId(engineId);
                    config.setModelPath(modelPath);

                    // Execute using orchestrator
                    SimulationOrchestrator orchestrator = new SimulationOrchestrator(
                            engines,
                            new com.capellaagent.simulation.orchestrator.ParameterExtractor(),
                            new com.capellaagent.simulation.orchestrator.ResultPropagator());

                    SimulationResult result = orchestrator.execute(config, null, monitor);

                    // Update UI with results
                    Display.getDefault().asyncExec(() -> displayResult(result));

                    return Status.OK_STATUS;

                } catch (SimulationEngineException e) {
                    updateStatus("Error: " + e.getMessage());
                    return new Status(IStatus.ERROR,
                            "com.capellaagent.simulation.ui", e.getMessage(), e);
                } finally {
                    Display.getDefault().asyncExec(() -> setRunning(false));
                }
            }
        };

        simulationJob.setUser(true);
        simulationJob.schedule();
    }

    /**
     * Displays simulation results in the result table.
     */
    private void displayResult(SimulationResult result) {
        if (resultTable.isDisposed()) {
            return;
        }

        resultTable.removeAll();

        for (Map.Entry<String, Object> entry : result.getOutputs().entrySet()) {
            TableItem item = new TableItem(resultTable, SWT.NONE);
            item.setText(0, entry.getKey());
            item.setText(1, String.valueOf(entry.getValue()));
            item.setText(2, result.getStatus().name());
        }

        // If no outputs, add a status row
        if (result.getOutputs().isEmpty()) {
            TableItem item = new TableItem(resultTable, SWT.NONE);
            item.setText(0, "(no outputs)");
            item.setText(1, "-");
            item.setText(2, result.getStatus().name());
        }

        statusLabel.setText(result.getStatus().name() + " ("
                + result.getDurationMs() + " ms)");
        progressBar.setSelection(100);
    }

    /**
     * Enables or disables the run controls based on execution state.
     */
    private void setRunning(boolean running) {
        if (runButton.isDisposed()) {
            return;
        }
        runButton.setEnabled(!running);
        engineCombo.setEnabled(!running);
        modelPathText.setEnabled(!running);

        if (running) {
            progressBar.setSelection(0);
            statusLabel.setText("Running...");
            resultTable.removeAll();
        }
    }

    /**
     * Updates the status label from a background thread.
     */
    private void updateStatus(String message) {
        Display.getDefault().asyncExec(() -> {
            if (!statusLabel.isDisposed()) {
                statusLabel.setText(message);
            }
        });
    }

    @Override
    public void setFocus() {
        if (engineCombo != null && !engineCombo.isDisposed()) {
            engineCombo.setFocus();
        }
    }
}

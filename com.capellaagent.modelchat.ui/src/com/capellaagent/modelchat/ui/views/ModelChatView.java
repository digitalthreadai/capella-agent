package com.capellaagent.modelchat.ui.views;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import com.capellaagent.core.config.AgentConfiguration;
import com.capellaagent.core.llm.ILlmProvider;
import com.capellaagent.core.llm.LlmProviderRegistry;
import com.capellaagent.core.session.ConversationSession;
import com.capellaagent.core.util.WorkspaceUtil;
import com.capellaagent.modelchat.ui.views.ChatHtmlRenderer;

import org.eclipse.core.resources.IProject;

/**
 * Eclipse ViewPart that provides the AI Model Chat interface.
 * <p>
 * The view is composed of three main areas:
 * <ol>
 *   <li><b>Top toolbar</b> - LLM provider selection, active project selector,
 *       clear history, export, and detach buttons</li>
 *   <li><b>Main area</b> - {@link Browser} widget rendering the conversation
 *       as styled HTML with clickable UUID links</li>
 *   <li><b>Bottom input</b> - Text input field and Send button for composing messages</li>
 * </ol>
 * <p>
 * Messages are sent asynchronously via {@link ChatJob}, and responses are rendered
 * back to the UI thread using {@link Display#asyncExec(Runnable)}.
 */
public class ModelChatView extends ViewPart {

    /** The unique view identifier, matching the plugin.xml registration. */
    public static final String VIEW_ID = "com.capellaagent.modelchat.ui.views.ModelChatView";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    // UI widgets
    private Label providerInfoLabel;
    private ComboViewer projectCombo;
    private Browser chatBrowser;
    private Text inputField;
    private Button sendButton;

    // HTML rendering
    private StringBuilder fullHtmlContent = new StringBuilder();
    private ChatHtmlRenderer renderer = new ChatHtmlRenderer();
    private DetachableChatSupport detachSupport = new DetachableChatSupport();

    // State
    private ConversationSession session;
    private ChatJob activeJob;

    // BrowserFunction instances — stored so they can be disposed on view close
    private org.eclipse.swt.browser.BrowserFunction javaNavigateFunc;
    private org.eclipse.swt.browser.BrowserFunction javaActionFunc;

    /** Guards against sending a second message while a job is running. */
    private volatile boolean jobInFlight = false;

    @Override
    public void createPartControl(Composite parent) {
        // Initialize conversation session
        session = new ConversationSession();

        // Root layout
        Composite root = new Composite(parent, SWT.NONE);
        GridLayout rootLayout = new GridLayout(1, false);
        rootLayout.marginWidth = 0;
        rootLayout.marginHeight = 0;
        rootLayout.verticalSpacing = 0;
        root.setLayout(rootLayout);

        // Top toolbar area
        createToolbar(root);

        // Main conversation area (Browser widget)
        createConversationArea(root);

        // Bottom input area
        createInputArea(root);

        // Configure view toolbar actions
        configureViewToolbar();
    }

    /**
     * Creates the top toolbar composite with provider and project selectors.
     */
    private void createToolbar(Composite parent) {
        Composite toolbar = new Composite(parent, SWT.NONE);
        GridLayout toolbarLayout = new GridLayout(4, false);
        toolbarLayout.marginWidth = 8;
        toolbarLayout.marginHeight = 4;
        toolbar.setLayout(toolbarLayout);
        toolbar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // LLM Provider label (read-only, configured via Preferences)
        Label providerLabel = new Label(toolbar, SWT.NONE);
        providerLabel.setText("Provider:");

        providerInfoLabel = new Label(toolbar, SWT.NONE);
        providerInfoLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        updateProviderLabel();

        // Active project selector
        Label projectLabel = new Label(toolbar, SWT.NONE);
        projectLabel.setText("Project:");

        projectCombo = new ComboViewer(toolbar, SWT.READ_ONLY | SWT.DROP_DOWN);
        projectCombo.setContentProvider(ArrayContentProvider.getInstance());
        projectCombo.setLabelProvider(new LabelProvider());
        projectCombo.getControl().setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Populate from workspace Capella projects
        refreshProjectList();
    }

    /**
     * Updates the provider info label from current preferences.
     */
    private void updateProviderLabel() {
        try {
            ILlmProvider provider = LlmProviderRegistry.getInstance().getActiveProvider();
            providerInfoLabel.setText(provider.getDisplayName());
        } catch (Exception e) {
            providerInfoLabel.setText("(not configured)");
        }
    }

    /**
     * Returns the project name currently selected in the project dropdown,
     * or {@code null} if nothing real is selected (e.g. the
     * "(No Capella project found)" placeholder).
     */
    private String getSelectedProjectName() {
        if (projectCombo == null || projectCombo.getControl().isDisposed()) {
            return null;
        }
        IStructuredSelection sel = projectCombo.getStructuredSelection();
        if (sel == null || sel.isEmpty()) {
            return null;
        }
        Object first = sel.getFirstElement();
        if (!(first instanceof String)) {
            return null;
        }
        String name = (String) first;
        if (name.isBlank() || name.startsWith("(No Capella project")) {
            return null;
        }
        return name;
    }

    /**
     * Refreshes the project dropdown with current workspace Capella projects.
     */
    private void refreshProjectList() {
        List<String> projectNames = new ArrayList<>();
        try {
            List<IProject> capellaProjects = WorkspaceUtil.getCapellaProjects();
            for (IProject p : capellaProjects) {
                projectNames.add(p.getName());
            }
        } catch (Exception e) {
            // Fall through to empty list
        }

        if (projectNames.isEmpty()) {
            projectNames.add("(No Capella project found)");
        }

        projectCombo.setInput(projectNames);
        projectCombo.setSelection(new StructuredSelection(projectNames.get(0)));
    }

    /**
     * Creates the main conversation display area using a {@link Browser} widget.
     * <p>
     * The browser renders HTML content produced by {@link ChatHtmlRenderer} and
     * exposes Java callbacks via {@link BrowserFunction} for UUID navigation
     * and generic actions.
     */
    private void createConversationArea(Composite parent) {
        // Conversation area composite
        Composite conversationArea = new Composite(parent, SWT.NONE);
        conversationArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout conversationLayout = new GridLayout(1, false);
        conversationLayout.marginWidth = 0;
        conversationLayout.marginHeight = 0;
        conversationArea.setLayout(conversationLayout);

        // Try Edge (Chromium) first, fall back to default browser
        Browser browser;
        try {
            browser = new Browser(conversationArea, SWT.EDGE);
        } catch (Exception e) {
            browser = new Browser(conversationArea, SWT.NONE);
        }
        chatBrowser = browser;
        chatBrowser.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // Load base HTML template
        String baseHtml = renderer.getBaseHtmlTemplate();
        fullHtmlContent.append(baseHtml);
        chatBrowser.setText(baseHtml);

        // Register Java callback for UUID navigation (called from HTML links)
        javaNavigateFunc = new BrowserFunction(chatBrowser, "javaNavigate") {
            @Override
            public Object function(Object[] args) {
                if (args.length > 0) {
                    String uuid = args[0].toString();
                    Display.getDefault().asyncExec(() -> navigateToElement(uuid));
                }
                return null;
            }
        };

        // Register Java callback for generic actions from the browser
        javaActionFunc = new BrowserFunction(chatBrowser, "javaAction") {
            @Override
            public Object function(Object[] args) {
                if (args.length >= 2) {
                    String action = args[0].toString();
                    String data = args[1].toString();
                    handleBrowserAction(action, data);
                }
                return null;
            }
        };
    }

    /**
     * Creates the bottom input area with text field and send button.
     */
    private void createInputArea(Composite parent) {
        Composite inputArea = new Composite(parent, SWT.NONE);
        GridLayout inputLayout = new GridLayout(2, false);
        inputLayout.marginWidth = 8;
        inputLayout.marginHeight = 6;
        inputArea.setLayout(inputLayout);
        inputArea.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));

        // Input text field
        inputField = new Text(inputArea, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData inputData = new GridData(SWT.FILL, SWT.FILL, true, true);
        inputData.heightHint = 60;
        inputData.minimumHeight = 40;
        inputField.setLayoutData(inputData);
        inputField.setMessage("Ask about your model or request changes...");

        // Enter key sends message (Shift+Enter for newline)
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR) {
                    if ((e.stateMask & SWT.SHIFT) == 0) {
                        e.doit = false;
                        sendMessage();
                    }
                }
            }
        });

        // Send button
        sendButton = new Button(inputArea, SWT.PUSH);
        sendButton.setText("Send");
        GridData buttonData = new GridData(SWT.RIGHT, SWT.BOTTOM, false, false);
        buttonData.widthHint = 80;
        buttonData.heightHint = 32;
        sendButton.setLayoutData(buttonData);
        sendButton.addListener(SWT.Selection, event -> sendMessage());
    }

    /**
     * Configures the view's toolbar with clear, export, and detach actions.
     */
    private void configureViewToolbar() {
        IToolBarManager toolbarManager = getViewSite().getActionBars().getToolBarManager();

        Action clearAction = new Action("Clear History") {
            @Override
            public void run() {
                clearConversation();
            }
        };
        clearAction.setToolTipText("Clear conversation history and start a new session");

        Action exportAction = new Action("Export") {
            @Override
            public void run() {
                exportConversation();
            }
        };
        exportAction.setToolTipText("Export conversation to a text file");

        // Detach/Reattach button
        Action detachAction = new Action("Detach", IAction.AS_PUSH_BUTTON) {
            @Override
            public void run() {
                if (detachSupport.isDetached()) {
                    detachSupport.reattach();
                    setText("Detach");
                    setToolTipText("Open chat in floating window");
                } else {
                    String currentHtml = null;
                    try {
                        currentHtml = (String) chatBrowser.evaluate(
                                "return document.documentElement.outerHTML;");
                    } catch (Exception e) {
                        currentHtml = renderer.getBaseHtmlTemplate();
                    }
                    detachSupport.detach(getSite().getShell(), currentHtml);
                    setText("Reattach");
                    setToolTipText("Return chat to docked view");
                }
            }
        };
        detachAction.setToolTipText("Open chat in floating window");

        // Theme toggle button
        Action themeAction = new Action("Theme", IAction.AS_PUSH_BUTTON) {
            @Override
            public void run() {
                if (chatBrowser != null && !chatBrowser.isDisposed()) {
                    chatBrowser.execute("toggleTheme()");
                    detachSupport.executeOnFloating("toggleTheme()");
                }
            }
        };
        themeAction.setToolTipText("Toggle light/dark theme");

        toolbarManager.add(clearAction);
        toolbarManager.add(new Separator());
        toolbarManager.add(exportAction);
        toolbarManager.add(new Separator());
        toolbarManager.add(themeAction);
        toolbarManager.add(detachAction);
    }

    /**
     * Sends the current input text as a user message and starts the chat job.
     * <p>
     * The input field is cleared and disabled during processing. A {@link ChatJob}
     * is created to handle the LLM interaction asynchronously.
     */
    public void sendMessage() {
        String message = inputField.getText().trim();
        if (message.isEmpty()) {
            return;
        }

        // Prevent sending while a job is running
        if (jobInFlight) {
            return;
        }

        // Display user message
        appendUserMessage(message);

        // Clear input and disable during processing
        inputField.setText("");
        setInputEnabled(false);

        // Get selected provider name
        String selectedProvider = getSelectedProvider();

        // Read the project dropdown so the orchestration thread can resolve
        // the right Sirius session when multiple Capella projects are open.
        String selectedProject = getSelectedProjectName();

        // Create and schedule the chat job
        activeJob = new ChatJob(
                session,
                message,
                selectedProvider,
                selectedProject,
                this::appendAssistantMessage,
                this::appendToolNotification,
                () -> {
                    jobInFlight = false;
                    Display.getDefault().asyncExec(() -> setInputEnabled(true));
                },
                (toolName, category, fullResult) -> {
                    String html = renderer.renderToolResult(category, toolName, fullResult);
                    String escapedHtml = renderer.escapeJs(html);
                    Display.getDefault().asyncExec(() -> {
                        if (chatBrowser != null && !chatBrowser.isDisposed()) {
                            chatBrowser.execute("appendMessage('" + escapedHtml + "')");
                            detachSupport.executeOnFloating("appendMessage('" + escapedHtml + "')");
                        }
                    });
                }
        );
        activeJob.setUser(false);
        jobInFlight = true;
        activeJob.schedule();
    }

    /**
     * Appends a user message to the conversation display.
     *
     * @param message the user's message text
     */
    private void appendUserMessage(String message) {
        String timestamp = LocalTime.now().format(TIME_FORMAT);
        String html = renderer.renderUserMessage(message, timestamp);
        String escapedHtml = renderer.escapeJs(html);

        Display.getDefault().asyncExec(() -> {
            if (chatBrowser != null && !chatBrowser.isDisposed()) {
                chatBrowser.execute("appendMessage('" + escapedHtml + "')");
                detachSupport.executeOnFloating("appendMessage('" + escapedHtml + "')");
            }
        });
    }

    /**
     * Appends an assistant response message to the conversation display.
     *
     * @param message the assistant's response text
     */
    private void appendAssistantMessage(String message) {
        String timestamp = LocalTime.now().format(TIME_FORMAT);
        String html = renderer.renderAssistantMessage(message, timestamp);
        String escapedHtml = renderer.escapeJs(html);

        Display.getDefault().asyncExec(() -> {
            if (chatBrowser != null && !chatBrowser.isDisposed()) {
                chatBrowser.execute("appendMessage('" + escapedHtml + "')");
                detachSupport.executeOnFloating("appendMessage('" + escapedHtml + "')");
            }
        });
    }

    /**
     * Appends a tool execution notification to the conversation display.
     *
     * @param toolName the name of the tool being executed
     */
    private void appendToolNotification(String toolName) {
        String html = renderer.renderToolNotification(toolName, "");
        String escapedHtml = renderer.escapeJs(html);

        Display.getDefault().asyncExec(() -> {
            if (chatBrowser != null && !chatBrowser.isDisposed()) {
                chatBrowser.execute("appendMessage('" + escapedHtml + "')");
                detachSupport.executeOnFloating("appendMessage('" + escapedHtml + "')");
            }
        });
    }

    /**
     * Appends a tool result with structured data to the conversation display.
     *
     * @param toolName the name of the tool that produced the result
     * @param category the tool category (e.g., "model_read", "analysis")
     * @param data     the JSON result data from the tool
     */
    private void appendToolResult(String toolName, String category, com.google.gson.JsonObject data) {
        String html = renderer.renderToolResult(category, toolName, data);
        String escapedHtml = renderer.escapeJs(html);

        Display.getDefault().asyncExec(() -> {
            if (chatBrowser != null && !chatBrowser.isDisposed()) {
                chatBrowser.execute("appendMessage('" + escapedHtml + "')");
                detachSupport.executeOnFloating("appendMessage('" + escapedHtml + "')");
            }
        });
    }

    /**
     * Navigates to a Capella model element in the Project Explorer.
     * <p>
     * Called from the browser via the {@code javaNavigate} callback when
     * the user clicks a UUID link in the chat.
     *
     * @param uuid the UUID of the Capella element to navigate to
     */
    private void navigateToElement(String uuid) {
        try {
            // Find the element by UUID across all open Sirius sessions
            org.eclipse.emf.ecore.EObject element = null;
            for (org.eclipse.sirius.business.api.session.Session s :
                    org.eclipse.sirius.business.api.session.SessionManager.INSTANCE.getSessions()) {
                for (org.eclipse.emf.ecore.resource.Resource r : s.getSemanticResources()) {
                    org.eclipse.emf.common.util.TreeIterator<org.eclipse.emf.ecore.EObject> it = r.getAllContents();
                    while (it.hasNext()) {
                        org.eclipse.emf.ecore.EObject obj = it.next();
                        // Match by EMF URI fragment or Capella element ID
                        String fragment = obj.eResource() != null ? obj.eResource().getURIFragment(obj) : "";
                        if (uuid.equals(fragment)) {
                            element = obj;
                            break;
                        }
                        // Also try Capella's getId() via reflection
                        try {
                            java.lang.reflect.Method getId = obj.getClass().getMethod("getId");
                            Object id = getId.invoke(obj);
                            if (uuid.equals(id)) {
                                element = obj;
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                    if (element != null) break;
                }
                if (element != null) break;
            }

            if (element == null) return;

            // Select and reveal in Project Explorer
            org.eclipse.ui.IViewPart explorer = getSite().getPage().showView("capella.project.explorer");
            if (explorer instanceof org.eclipse.ui.navigator.CommonNavigator) {
                ((org.eclipse.ui.navigator.CommonNavigator) explorer)
                    .selectReveal(new org.eclipse.jface.viewers.StructuredSelection(element));
            }
        } catch (Exception e) {
            try {
                getSite().getPage().showView("org.polarsys.capella.core.ui.semantic.browser.view");
            } catch (Exception ex) { /* ignore */ }
        }
    }

    /**
     * Handles generic actions dispatched from the browser via the
     * {@code javaAction} callback.
     *
     * @param action the action identifier
     * @param data   associated data for the action
     */
    private void handleBrowserAction(String action, String data) {
        switch (action) {
            case "copy":
                // Copy text to clipboard via SWT (works in embedded browser)
                org.eclipse.swt.dnd.Clipboard clipboard =
                        new org.eclipse.swt.dnd.Clipboard(Display.getDefault());
                clipboard.setContents(
                        new Object[]{data},
                        new org.eclipse.swt.dnd.Transfer[]{
                                org.eclipse.swt.dnd.TextTransfer.getInstance()});
                clipboard.dispose();
                if (chatBrowser != null && !chatBrowser.isDisposed()) {
                    chatBrowser.execute("showCopiedTooltip()");
                }
                break;
            case "navigate":
                navigateToElement(data);
                break;
            default:
                // Unknown action, ignore
                break;
        }
    }

    /**
     * Clears the conversation history and resets the session.
     */
    public void clearConversation() {
        session = new ConversationSession();
        if (chatBrowser != null && !chatBrowser.isDisposed()) {
            chatBrowser.setText(renderer.getBaseHtmlTemplate());
        }
        detachSupport.executeOnFloating(
                "document.getElementById('chat-container').innerHTML = "
                + "document.getElementById('welcome-msg') "
                + "? document.getElementById('welcome-msg').outerHTML : ''");
    }

    /**
     * Exports the current conversation text to a file.
     */
    private void exportConversation() {
        org.eclipse.swt.widgets.FileDialog dialog = new org.eclipse.swt.widgets.FileDialog(
            getSite().getShell(), SWT.SAVE);
        dialog.setFilterExtensions(new String[]{"*.html", "*.txt", "*.*"});
        dialog.setFilterNames(new String[]{"HTML Files (styled)", "Text Files (plain)", "All Files"});
        dialog.setFileName("chat_export.html");
        dialog.setOverwrite(true);
        String path = dialog.open();
        if (path != null) {
            try {
                String content;
                if (path.endsWith(".html") || path.endsWith(".htm")) {
                    content = (String) chatBrowser.evaluate("return document.documentElement.outerHTML;");
                } else {
                    content = (String) chatBrowser.evaluate(
                        "return document.getElementById('chat-container').innerText;");
                }
                try (java.io.FileWriter writer = new java.io.FileWriter(path)) {
                    writer.write(content != null ? content : "");
                }
            } catch (Exception ex) {
                org.eclipse.jface.dialogs.MessageDialog.openError(
                    getSite().getShell(), "Export Failed", ex.getMessage());
            }
        }
    }

    /**
     * Returns the currently selected LLM provider name.
     *
     * @return the provider name string
     */
    private String getSelectedProvider() {
        // Provider is now configured via Preferences page, not the chat view combo
        return AgentConfiguration.getInstance().getLlmProviderId();
    }

    /**
     * Enables or disables the input field and send button.
     *
     * @param enabled true to enable input, false to disable
     */
    private void setInputEnabled(boolean enabled) {
        if (!inputField.isDisposed()) {
            inputField.setEnabled(enabled);
        }
        if (!sendButton.isDisposed()) {
            sendButton.setEnabled(enabled);
        }
        if (enabled && !inputField.isDisposed()) {
            inputField.setFocus();
        }
    }

    @Override
    public void setFocus() {
        if (inputField != null && !inputField.isDisposed()) {
            inputField.setFocus();
        }
    }

    /**
     * Cancels the currently active ChatJob. Called by CancelJobHandler (ESC key).
     * Re-enables input immediately since the job cancellation may take a moment.
     */
    public void cancelActiveJob() {
        if (activeJob != null) {
            activeJob.cancel();
        }
        jobInFlight = false;
        Display.getDefault().asyncExec(() -> {
            setInputEnabled(true);
            appendSystemMessage("Job cancelled.");
        });
    }

    /** Appends a system/info message (grey, italic styling) to the chat display. */
    private void appendSystemMessage(String message) {
        String html = "<div style=\"color:#888;font-style:italic;padding:4px 8px;"
                + "margin:2px 0;border-left:3px solid #ccc;\">"
                + message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                + "</div>";
        String escaped = renderer.escapeJs(html);
        Display.getDefault().asyncExec(() -> {
            if (chatBrowser != null && !chatBrowser.isDisposed()) {
                chatBrowser.execute("appendMessage('" + escaped + "')");
                detachSupport.executeOnFloating("appendMessage('" + escaped + "')");
            }
        });
    }

    @Override
    public void dispose() {
        // Cancel any active job
        if (activeJob != null) {
            activeJob.cancel();
        }
        jobInFlight = false;

        // Dispose BrowserFunction callbacks to prevent native handle leaks
        if (javaNavigateFunc != null && !javaNavigateFunc.isDisposed()) {
            javaNavigateFunc.dispose();
        }
        if (javaActionFunc != null && !javaActionFunc.isDisposed()) {
            javaActionFunc.dispose();
        }

        // Dispose floating chat window
        detachSupport.dispose();

        super.dispose();
    }
}

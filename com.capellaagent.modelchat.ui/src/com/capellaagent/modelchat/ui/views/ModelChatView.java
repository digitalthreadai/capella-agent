package com.capellaagent.modelchat.ui.views;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
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
import com.capellaagent.modelchat.ui.adapters.ElementLinkAdapter;

import org.eclipse.core.resources.IProject;

/**
 * Eclipse ViewPart that provides the AI Model Chat interface.
 * <p>
 * The view is composed of three main areas:
 * <ol>
 *   <li><b>Top toolbar</b> - LLM provider selection, active project selector,
 *       clear history and export buttons</li>
 *   <li><b>Main area</b> - Scrollable {@link StyledText} widget displaying the
 *       conversation with styled user and assistant messages</li>
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
    private StyledText conversationText;
    private Text inputField;
    private Button sendButton;

    // State
    private ConversationSession session;
    private ChatJob activeJob;
    private ElementLinkAdapter linkAdapter;

    // Colors (disposed in dispose())
    private Color userMessageColor;
    private Color assistantMessageColor;
    private Color toolExecutionColor;
    private Color timestampColor;
    private Color backgroundColor;
    private Font boldFont;

    @Override
    public void createPartControl(Composite parent) {
        // Initialize colors
        Display display = parent.getDisplay();
        userMessageColor = new Color(display, 0, 102, 204);       // Blue
        assistantMessageColor = new Color(display, 51, 51, 51);    // Dark gray
        toolExecutionColor = new Color(display, 128, 128, 128);    // Gray
        timestampColor = new Color(display, 153, 153, 153);        // Light gray
        backgroundColor = new Color(display, 250, 250, 250);       // Off-white

        FontData[] fontData = display.getSystemFont().getFontData();
        for (FontData fd : fontData) {
            fd.setStyle(SWT.BOLD);
        }
        boldFont = new Font(display, fontData);

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

        // Main conversation area
        createConversationArea(root);

        // Bottom input area
        createInputArea(root);

        // Configure view toolbar actions
        configureViewToolbar();

        // Initialize link adapter for UUID detection in chat text
        linkAdapter = new ElementLinkAdapter(conversationText);
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
     * Creates the main scrollable conversation display area.
     */
    private void createConversationArea(Composite parent) {
        conversationText = new StyledText(parent, SWT.MULTI | SWT.V_SCROLL | SWT.WRAP | SWT.READ_ONLY);
        conversationText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        conversationText.setBackground(backgroundColor);
        conversationText.setMargins(12, 8, 12, 8);
        conversationText.setLineSpacing(4);

        // Welcome message
        appendSystemMessage("AI Model Chat ready. Ask questions about your Capella model "
                + "or request changes using natural language.");
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
     * Configures the view's toolbar with clear and export actions.
     */
    private void configureViewToolbar() {
        IToolBarManager toolbarManager = getViewSite().getActionBars().getToolBarManager();

        Action clearAction = new Action("Clear History") {
            @Override
            public void run() {
                clearHistory();
            }
        };
        clearAction.setToolTipText("Clear conversation history and start a new session");
        // clearAction.setImageDescriptor(ImageDescriptor.createFromFile(...));

        Action exportAction = new Action("Export") {
            @Override
            public void run() {
                exportConversation();
            }
        };
        exportAction.setToolTipText("Export conversation to a text file");

        toolbarManager.add(clearAction);
        toolbarManager.add(new Separator());
        toolbarManager.add(exportAction);
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
        if (activeJob != null && activeJob.getResult() == null) {
            return;
        }

        // Display user message
        appendUserMessage(message);

        // Clear input and disable during processing
        inputField.setText("");
        setInputEnabled(false);

        // Get selected provider name
        String selectedProvider = getSelectedProvider();

        // Create and schedule the chat job
        activeJob = new ChatJob(
                session,
                message,
                selectedProvider,
                this::appendAssistantMessage,
                this::appendToolExecution,
                () -> Display.getDefault().asyncExec(() -> setInputEnabled(true))
        );
        activeJob.setUser(false);
        activeJob.schedule();
    }

    /**
     * Appends a user message to the conversation display.
     *
     * @param message the user's message text
     */
    public void appendUserMessage(String message) {
        Display.getDefault().asyncExec(() -> {
            if (conversationText.isDisposed()) return;

            String timestamp = LocalTime.now().format(TIME_FORMAT);
            String prefix = "\n[" + timestamp + "] You:\n";
            String fullText = prefix + message + "\n";

            int startOffset = conversationText.getCharCount();
            conversationText.append(fullText);

            // Style the prefix (timestamp + "You:")
            StyleRange prefixStyle = new StyleRange();
            prefixStyle.start = startOffset;
            prefixStyle.length = prefix.length();
            prefixStyle.foreground = userMessageColor;
            prefixStyle.font = boldFont;
            conversationText.setStyleRange(prefixStyle);

            // Style the message body
            StyleRange messageStyle = new StyleRange();
            messageStyle.start = startOffset + prefix.length();
            messageStyle.length = message.length();
            messageStyle.foreground = userMessageColor;
            conversationText.setStyleRange(messageStyle);

            scrollToBottom();
        });
    }

    /**
     * Appends an assistant response message to the conversation display.
     *
     * @param message the assistant's response text
     */
    public void appendAssistantMessage(String message) {
        Display.getDefault().asyncExec(() -> {
            if (conversationText.isDisposed()) return;

            String timestamp = LocalTime.now().format(TIME_FORMAT);
            String prefix = "\n[" + timestamp + "] Assistant:\n";
            String fullText = prefix + message + "\n";

            int startOffset = conversationText.getCharCount();
            conversationText.append(fullText);

            // Style the prefix
            StyleRange prefixStyle = new StyleRange();
            prefixStyle.start = startOffset;
            prefixStyle.length = prefix.length();
            prefixStyle.foreground = assistantMessageColor;
            prefixStyle.font = boldFont;
            conversationText.setStyleRange(prefixStyle);

            // Style the message body
            StyleRange messageStyle = new StyleRange();
            messageStyle.start = startOffset + prefix.length();
            messageStyle.length = message.length();
            messageStyle.foreground = assistantMessageColor;
            conversationText.setStyleRange(messageStyle);

            // Detect and style UUID links in the message
            if (linkAdapter != null) {
                linkAdapter.applyLinkStyles(startOffset + prefix.length(), message);
            }

            scrollToBottom();
        });
    }

    /**
     * Appends a tool execution notification to the conversation display.
     *
     * @param toolName the name of the tool being executed
     */
    public void appendToolExecution(String toolName) {
        Display.getDefault().asyncExec(() -> {
            if (conversationText.isDisposed()) return;

            String text = "\n  >> Executing tool: " + toolName + " ...\n";

            int startOffset = conversationText.getCharCount();
            conversationText.append(text);

            StyleRange style = new StyleRange();
            style.start = startOffset;
            style.length = text.length();
            style.foreground = toolExecutionColor;
            style.fontStyle = SWT.ITALIC;
            conversationText.setStyleRange(style);

            scrollToBottom();
        });
    }

    /**
     * Appends a system informational message (not part of the LLM conversation).
     *
     * @param message the system message text
     */
    private void appendSystemMessage(String message) {
        String text = message + "\n";

        int startOffset = conversationText.getCharCount();
        conversationText.append(text);

        StyleRange style = new StyleRange();
        style.start = startOffset;
        style.length = text.length();
        style.foreground = timestampColor;
        style.fontStyle = SWT.ITALIC;
        conversationText.setStyleRange(style);
    }

    /**
     * Clears the conversation history and resets the session.
     */
    public void clearHistory() {
        conversationText.setText("");
        session = new ConversationSession();
        appendSystemMessage("Conversation cleared. Starting a new session.");
    }

    /**
     * Exports the current conversation text to a file.
     */
    private void exportConversation() {
        // PLACEHOLDER: Implement file save dialog and export
        // FileDialog dialog = new FileDialog(getSite().getShell(), SWT.SAVE);
        // dialog.setFilterExtensions(new String[]{"*.txt", "*.md", "*.*"});
        // dialog.setFilterNames(new String[]{"Text Files", "Markdown Files", "All Files"});
        // dialog.setFileName("model-chat-export.txt");
        // String path = dialog.open();
        // if (path != null) {
        //     Files.writeString(Path.of(path), conversationText.getText());
        // }
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

    /**
     * Scrolls the conversation text to the bottom to show the latest message.
     */
    private void scrollToBottom() {
        conversationText.setTopIndex(conversationText.getLineCount() - 1);
        conversationText.setCaretOffset(conversationText.getCharCount());
    }

    @Override
    public void setFocus() {
        if (inputField != null && !inputField.isDisposed()) {
            inputField.setFocus();
        }
    }

    @Override
    public void dispose() {
        // Cancel any active job
        if (activeJob != null) {
            activeJob.cancel();
        }

        // Dispose colors and fonts
        if (userMessageColor != null && !userMessageColor.isDisposed()) {
            userMessageColor.dispose();
        }
        if (assistantMessageColor != null && !assistantMessageColor.isDisposed()) {
            assistantMessageColor.dispose();
        }
        if (toolExecutionColor != null && !toolExecutionColor.isDisposed()) {
            toolExecutionColor.dispose();
        }
        if (timestampColor != null && !timestampColor.isDisposed()) {
            timestampColor.dispose();
        }
        if (backgroundColor != null && !backgroundColor.isDisposed()) {
            backgroundColor.dispose();
        }
        if (boldFont != null && !boldFont.isDisposed()) {
            boldFont.dispose();
        }

        // Dispose link adapter
        if (linkAdapter != null) {
            linkAdapter.dispose();
        }

        super.dispose();
    }
}

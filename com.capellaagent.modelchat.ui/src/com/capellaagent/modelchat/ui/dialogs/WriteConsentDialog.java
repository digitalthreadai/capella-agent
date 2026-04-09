package com.capellaagent.modelchat.ui.dialogs;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.capellaagent.core.security.ConsentManager;

/**
 * Native SWT modal dialog that asks the user to approve or deny a write or
 * destructive tool invocation proposed by the LLM.
 * <p>
 * Security invariants (F1 from the security sprint):
 * <ul>
 *   <li><b>Deny is the default button.</b> Pressing Enter = Deny.</li>
 *   <li><b>Closing with the window X = Deny.</b> ({@code handleShellCloseEvent})</li>
 *   <li><b>The "reasoning" field is untrusted LLM output</b> and is rendered
 *       as plain text via {@code StyledText#setText(...)}, stripped of
 *       newlines, and capped at 300 characters with a prefix label.</li>
 *   <li><b>Destructive tools disable the "remember" checkbox.</b> Every
 *       destructive invocation must prompt.</li>
 * </ul>
 */
public final class WriteConsentDialog extends TitleAreaDialog {

    private static final int MAX_REASONING_CHARS = 300;
    private static final int DENY_BUTTON_ID = IDialogConstants.CANCEL_ID;
    private static final int APPROVE_BUTTON_ID = IDialogConstants.OK_ID;

    private final String toolName;
    private final String category;
    private final String toolArgs;
    private final String reasoning;
    private final boolean destructive;

    private Button rememberChoice;
    private ConsentManager.Decision result = ConsentManager.Decision.DENIED;

    public WriteConsentDialog(Shell parent, String toolName, String category,
                              String toolArgs, String reasoning,
                              boolean destructive) {
        super(parent);
        this.toolName = toolName != null ? toolName : "(unknown tool)";
        this.category = category != null ? category : "MODEL_WRITE";
        this.toolArgs = toolArgs != null ? toolArgs : "{}";
        this.reasoning = reasoning != null ? reasoning : "";
        this.destructive = destructive;
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    public ConsentManager.Decision getDecision() {
        return result;
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText("Approve model change");
        // Closing via window X (or Alt+F4) must map to DENY.
        newShell.addShellListener(new ShellAdapter() {
            @Override
            public void shellClosed(ShellEvent e) {
                result = ConsentManager.Decision.DENIED;
            }
        });
    }

    @Override
    public void create() {
        super.create();
        String title = destructive
            ? "Destructive operation requires approval"
            : "Model write requires approval";
        setTitle(title);
        setMessage("The AI wants to run a tool that modifies the model. "
            + "Review the arguments and decide.",
            destructive ? IMessageProviderLevels.WARNING : IMessageProviderLevels.INFORMATION);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 12;
        layout.marginHeight = 12;
        layout.horizontalSpacing = 10;
        layout.verticalSpacing = 8;
        container.setLayout(layout);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        addLabelRow(container, "Tool:", toolName);
        addLabelRow(container, "Category:",
            category + (destructive ? "  \u26A0 DESTRUCTIVE" : ""));

        // Arguments (read-only, monospace)
        Label argsLabel = new Label(container, SWT.NONE);
        argsLabel.setText("Arguments:");
        argsLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));

        StyledText argsText = new StyledText(container,
            SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY | SWT.MULTI);
        GridData argsGd = new GridData(SWT.FILL, SWT.FILL, true, true);
        argsGd.heightHint = 140;
        argsGd.widthHint = 520;
        argsText.setLayoutData(argsGd);
        argsText.setText(toolArgs);
        argsText.setFont(monospaceFont(argsText));

        // Untrusted LLM reasoning — labelled, plain-text, newline-stripped,
        // capped at 300 chars. NEVER rendered as HTML or markdown.
        Label reasoningLabel = new Label(container, SWT.NONE);
        reasoningLabel.setText("LLM reasoning\n(untrusted):");
        reasoningLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));

        StyledText reasoningText = new StyledText(container,
            SWT.BORDER | SWT.WRAP | SWT.READ_ONLY | SWT.MULTI);
        GridData rgd = new GridData(SWT.FILL, SWT.FILL, true, false);
        rgd.heightHint = 60;
        reasoningText.setLayoutData(rgd);
        reasoningText.setText(sanitizeReasoning(reasoning));
        Color warn = new Color(container.getDisplay(), 120, 60, 0);
        reasoningText.setForeground(warn);
        reasoningText.addDisposeListener(ev -> warn.dispose());

        // Remember-my-choice checkbox (disabled for destructive tools).
        new Label(container, SWT.NONE); // spacer column
        rememberChoice = new Button(container, SWT.CHECK);
        rememberChoice.setText("Remember my choice for this tool in this session");
        rememberChoice.setEnabled(!destructive);
        if (destructive) {
            rememberChoice.setToolTipText(
                "Destructive tools always prompt — this cannot be remembered.");
        }

        return area;
    }

    private static String sanitizeReasoning(String raw) {
        if (raw == null || raw.isBlank()) {
            return "(no reasoning provided)";
        }
        String stripped = raw.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (stripped.length() > MAX_REASONING_CHARS) {
            stripped = stripped.substring(0, MAX_REASONING_CHARS) + "\u2026";
        }
        return stripped;
    }

    private static void addLabelRow(Composite parent, String label, String value) {
        Label l = new Label(parent, SWT.NONE);
        l.setText(label);
        l.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        Label v = new Label(parent, SWT.WRAP);
        v.setText(value);
        v.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    private static Font monospaceFont(Control owner) {
        FontData base = owner.getFont().getFontData()[0];
        FontData mono = new FontData(
            isWindows() ? "Consolas" : "Monospaced",
            base.getHeight(),
            SWT.NORMAL);
        Font font = new Font(owner.getDisplay(), mono);
        owner.addDisposeListener(ev -> font.dispose());
        return font;
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        // Deny is the default — pressing Enter denies. X also denies
        // (handled by shellClosed above).
        createButton(parent, DENY_BUTTON_ID, "Deny", true /* default */);
        createButton(parent, APPROVE_BUTTON_ID,
            destructive ? "Approve destructive action" : "Approve", false);
    }

    @Override
    protected void buttonPressed(int buttonId) {
        if (buttonId == APPROVE_BUTTON_ID) {
            result = (rememberChoice != null && rememberChoice.getSelection() && !destructive)
                ? ConsentManager.Decision.APPROVED_REMEMBER
                : ConsentManager.Decision.APPROVED;
            setReturnCode(OK);
            close();
        } else {
            result = ConsentManager.Decision.DENIED;
            setReturnCode(CANCEL);
            close();
        }
    }

    /**
     * Convenience: opens the dialog on the SWT display thread (synchronously)
     * and returns the user's decision. Safe to call from a worker thread.
     */
    public static ConsentManager.Decision openBlocking(
            Display display, Shell parent,
            String toolName, String category, String toolArgs,
            String reasoning, boolean destructive) {
        final ConsentManager.Decision[] box = new ConsentManager.Decision[1];
        box[0] = ConsentManager.Decision.DENIED;
        display.syncExec(() -> {
            Shell active = parent != null ? parent : display.getActiveShell();
            WriteConsentDialog dlg = new WriteConsentDialog(
                active, toolName, category, toolArgs, reasoning, destructive);
            dlg.open();
            box[0] = dlg.getDecision();
        });
        return box[0];
    }

    /** Bridge to {@link org.eclipse.jface.dialogs.IMessageProvider} levels. */
    private static final class IMessageProviderLevels {
        static final int INFORMATION = org.eclipse.jface.dialogs.IMessageProvider.INFORMATION;
        static final int WARNING = org.eclipse.jface.dialogs.IMessageProvider.WARNING;
    }
}

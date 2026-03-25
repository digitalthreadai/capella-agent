package com.capellaagent.core.ui.widgets;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
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

import com.capellaagent.core.ui.rendering.MarkdownRenderer;

/**
 * Reusable SWT composite that provides a chat-style interface for agent interactions.
 * <p>
 * The composite is divided into two main areas:
 * <ul>
 *   <li>A scrollable message area displaying conversation history with color-coded
 *       messages by role (user, assistant, tool)</li>
 *   <li>An input area at the bottom with a text field and send button</li>
 * </ul>
 * <p>
 * Messages are rendered with basic Markdown formatting using {@link MarkdownRenderer}.
 *
 * <h3>Usage example:</h3>
 * <pre>{@code
 * ChatComposite chat = new ChatComposite(parent, SWT.NONE);
 * chat.setOnSend(message -> {
 *     chat.addMessage("user", message);
 *     // ... process message and add response
 *     chat.addMessage("assistant", response);
 * });
 * }</pre>
 */
public class ChatComposite extends Composite {

    /** Background color for user messages (light blue). */
    private static final int[] COLOR_USER_BG = {219, 234, 254};

    /** Background color for assistant messages (white). */
    private static final int[] COLOR_ASSISTANT_BG = {255, 255, 255};

    /** Background color for tool result messages (light gray). */
    private static final int[] COLOR_TOOL_BG = {243, 244, 246};

    private final ScrolledComposite scrolledComposite;
    private final Composite messagesContainer;
    private final Text inputText;
    private final Button sendButton;
    private final MarkdownRenderer markdownRenderer;
    private final List<Composite> messageBubbles;

    private Consumer<String> onSendCallback;
    private Color userBgColor;
    private Color assistantBgColor;
    private Color toolBgColor;
    private Color borderColor;
    private Font roleFont;

    /**
     * Constructs a new ChatComposite.
     *
     * @param parent the parent composite
     * @param style  the SWT style bits
     */
    public ChatComposite(Composite parent, int style) {
        super(parent, style);
        this.markdownRenderer = new MarkdownRenderer();
        this.messageBubbles = new ArrayList<>();

        initializeColors();
        initializeFonts();

        GridLayout mainLayout = new GridLayout(1, false);
        mainLayout.marginWidth = 0;
        mainLayout.marginHeight = 0;
        mainLayout.verticalSpacing = 0;
        setLayout(mainLayout);

        // --- Scrollable message area ---
        scrolledComposite = new ScrolledComposite(this, SWT.V_SCROLL | SWT.BORDER);
        scrolledComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        scrolledComposite.setExpandHorizontal(true);
        scrolledComposite.setExpandVertical(true);

        messagesContainer = new Composite(scrolledComposite, SWT.NONE);
        GridLayout messagesLayout = new GridLayout(1, false);
        messagesLayout.marginWidth = 8;
        messagesLayout.marginHeight = 8;
        messagesLayout.verticalSpacing = 12;
        messagesContainer.setLayout(messagesLayout);
        messagesContainer.setBackground(getDisplay().getSystemColor(SWT.COLOR_WHITE));

        scrolledComposite.setContent(messagesContainer);

        // Recompute size when the scrolled composite is resized
        scrolledComposite.addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                recomputeMessageSizes();
            }
        });

        // --- Input area ---
        Composite inputArea = new Composite(this, SWT.NONE);
        inputArea.setLayoutData(new GridData(SWT.FILL, SWT.END, true, false));
        GridLayout inputLayout = new GridLayout(2, false);
        inputLayout.marginWidth = 8;
        inputLayout.marginHeight = 8;
        inputArea.setLayout(inputLayout);

        inputText = new Text(inputArea, SWT.BORDER | SWT.MULTI | SWT.WRAP);
        GridData inputData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        inputData.heightHint = 60;
        inputText.setLayoutData(inputData);
        inputText.setMessage("Type a message...");

        sendButton = new Button(inputArea, SWT.PUSH);
        sendButton.setText("Send");
        GridData buttonData = new GridData(SWT.END, SWT.END, false, false);
        buttonData.heightHint = 36;
        buttonData.widthHint = 80;
        sendButton.setLayoutData(buttonData);

        // Enter key sends the message (Shift+Enter for newline)
        inputText.addListener(SWT.KeyDown, event -> {
            if (event.keyCode == SWT.CR && (event.stateMask & SWT.SHIFT) == 0) {
                event.doit = false;
                handleSend();
            }
        });

        sendButton.addListener(SWT.Selection, event -> handleSend());
    }

    /**
     * Adds a message to the chat display.
     *
     * @param role    the message role: "user", "assistant", or "tool"
     * @param content the message content, which may include Markdown formatting
     */
    public void addMessage(String role, String content) {
        Display display = getDisplay();
        if (display.isDisposed()) {
            return;
        }

        Runnable addAction = () -> {
            if (isDisposed()) {
                return;
            }

            Composite bubble = createMessageBubble(role, content);
            messageBubbles.add(bubble);

            recomputeMessageSizes();
            scrollToBottom();
        };

        if (Display.getCurrent() != null) {
            addAction.run();
        } else {
            display.asyncExec(addAction);
        }
    }

    /**
     * Clears all messages from the chat display.
     */
    public void clearMessages() {
        Display display = getDisplay();
        Runnable clearAction = () -> {
            if (isDisposed()) {
                return;
            }
            for (Composite bubble : messageBubbles) {
                if (!bubble.isDisposed()) {
                    bubble.dispose();
                }
            }
            messageBubbles.clear();
            recomputeMessageSizes();
        };

        if (Display.getCurrent() != null) {
            clearAction.run();
        } else {
            display.asyncExec(clearAction);
        }
    }

    /**
     * Sets the callback invoked when the user sends a message (via Send button or Enter key).
     *
     * @param callback a consumer that receives the user's input text
     */
    public void setOnSend(Consumer<String> callback) {
        this.onSendCallback = callback;
    }

    /**
     * Enables or disables the input area.
     *
     * @param enabled {@code true} to enable input, {@code false} to disable
     */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!isDisposed()) {
            inputText.setEnabled(enabled);
            sendButton.setEnabled(enabled);
        }
    }

    /**
     * Returns the underlying input text widget.
     *
     * @return the input text widget
     */
    public Text getInputText() {
        return inputText;
    }

    /**
     * Returns the send button widget.
     *
     * @return the send button
     */
    public Button getSendButton() {
        return sendButton;
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private void initializeColors() {
        Display display = getDisplay();
        userBgColor = new Color(display, COLOR_USER_BG[0], COLOR_USER_BG[1], COLOR_USER_BG[2]);
        assistantBgColor = new Color(display, COLOR_ASSISTANT_BG[0], COLOR_ASSISTANT_BG[1], COLOR_ASSISTANT_BG[2]);
        toolBgColor = new Color(display, COLOR_TOOL_BG[0], COLOR_TOOL_BG[1], COLOR_TOOL_BG[2]);
        borderColor = new Color(display, 209, 213, 219);

        addDisposeListener(e -> {
            userBgColor.dispose();
            assistantBgColor.dispose();
            toolBgColor.dispose();
            borderColor.dispose();
        });
    }

    private void initializeFonts() {
        Display display = getDisplay();
        Font systemFont = display.getSystemFont();
        FontData[] fontData = systemFont.getFontData();
        for (FontData fd : fontData) {
            fd.setStyle(SWT.BOLD);
            fd.setHeight(fd.getHeight());
        }
        roleFont = new Font(display, fontData);

        addDisposeListener(e -> roleFont.dispose());
    }

    private Composite createMessageBubble(String role, String content) {
        Composite bubble = new Composite(messagesContainer, SWT.NONE);
        GridData bubbleData = new GridData(SWT.FILL, SWT.TOP, true, false);
        bubble.setLayoutData(bubbleData);

        GridLayout bubbleLayout = new GridLayout(1, false);
        bubbleLayout.marginWidth = 12;
        bubbleLayout.marginHeight = 8;
        bubbleLayout.verticalSpacing = 4;
        bubble.setLayout(bubbleLayout);

        Color bgColor = getBackgroundColorForRole(role);
        bubble.setBackground(bgColor);

        // Role label
        Label roleLabel = new Label(bubble, SWT.NONE);
        roleLabel.setText(formatRoleName(role));
        roleLabel.setFont(roleFont);
        roleLabel.setBackground(bgColor);
        roleLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // Content as StyledText with Markdown rendering
        StyledText styledContent = new StyledText(bubble, SWT.READ_ONLY | SWT.WRAP | SWT.MULTI);
        styledContent.setBackground(bgColor);
        styledContent.setEditable(false);
        styledContent.setWordWrap(true);
        styledContent.setCaret(null);

        GridData contentData = new GridData(SWT.FILL, SWT.TOP, true, false);
        styledContent.setLayoutData(contentData);

        // Apply markdown rendering
        MarkdownRenderer.RenderResult result = markdownRenderer.render(content, styledContent);
        styledContent.setText(result.plainText());
        if (result.styles() != null && result.styles().length > 0) {
            styledContent.setStyleRanges(result.styles());
        }

        return bubble;
    }

    private Color getBackgroundColorForRole(String role) {
        return switch (role.toLowerCase()) {
            case "user" -> userBgColor;
            case "assistant" -> assistantBgColor;
            case "tool", "tool_result" -> toolBgColor;
            default -> assistantBgColor;
        };
    }

    private String formatRoleName(String role) {
        return switch (role.toLowerCase()) {
            case "user" -> "You";
            case "assistant" -> "Assistant";
            case "tool", "tool_result" -> "Tool Result";
            default -> role.substring(0, 1).toUpperCase() + role.substring(1);
        };
    }

    private void handleSend() {
        String text = inputText.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        inputText.setText("");
        if (onSendCallback != null) {
            onSendCallback.accept(text);
        }
    }

    private void recomputeMessageSizes() {
        if (messagesContainer.isDisposed() || scrolledComposite.isDisposed()) {
            return;
        }
        messagesContainer.setSize(
                messagesContainer.computeSize(scrolledComposite.getClientArea().width, SWT.DEFAULT));
        scrolledComposite.setMinSize(
                messagesContainer.computeSize(scrolledComposite.getClientArea().width, SWT.DEFAULT));
        messagesContainer.layout(true, true);
    }

    private void scrollToBottom() {
        getDisplay().asyncExec(() -> {
            if (!scrolledComposite.isDisposed()) {
                scrolledComposite.setOrigin(0, messagesContainer.getSize().y);
            }
        });
    }
}

package com.capellaagent.modelchat.ui.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Shell;

/**
 * Manages a detachable floating chat window for the AI Model Chat.
 * <p>
 * When the user detaches the chat, a new top-level {@link Shell} is created
 * containing its own {@link Browser} widget, allowing the chat to float
 * independently of the Eclipse workbench. Messages are synchronized between
 * the docked and floating views via JavaScript execution.
 * <p>
 * The floating window can optionally be set to always-on-top. Closing the
 * floating window automatically reattaches the chat to the docked view.
 */
public class DetachableChatSupport {

    private Shell floatingShell;
    private Browser floatingBrowser;
    private boolean detached = false;
    private boolean alwaysOnTop = false;

    private static final int DEFAULT_WIDTH = 650;
    private static final int DEFAULT_HEIGHT = 850;

    /**
     * Detaches the chat into a floating window.
     * <p>
     * Creates a new top-level {@link Shell} with its own {@link Browser} widget,
     * loads the current HTML content, and centers it on the primary monitor.
     * Closing the floating window triggers a reattach.
     *
     * @param parent      the parent shell (typically the workbench window)
     * @param currentHtml the current HTML content to display in the floating browser
     */
    public void detach(Shell parent, String currentHtml) {
        if (detached) {
            return;
        }

        int style = alwaysOnTop ? SWT.SHELL_TRIM | SWT.ON_TOP : SWT.SHELL_TRIM;
        floatingShell = new Shell(parent.getDisplay(), style);
        floatingShell.setText("AI Model Chat \u2014 Detached");
        floatingShell.setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        floatingShell.setLayout(new FillLayout());

        // Try SWT.EDGE first (Chromium-based), fall back to default
        Browser browser;
        try {
            browser = new Browser(floatingShell, SWT.EDGE);
        } catch (Exception e) {
            browser = new Browser(floatingShell, SWT.NONE);
        }
        floatingBrowser = browser;
        floatingBrowser.setText(currentHtml);

        // Center on primary monitor
        Rectangle screenBounds = parent.getDisplay().getPrimaryMonitor().getBounds();
        floatingShell.setLocation(
                (screenBounds.width - DEFAULT_WIDTH) / 2,
                (screenBounds.height - DEFAULT_HEIGHT) / 2);

        // Handle close: prevent actual close and reattach instead
        floatingShell.addShellListener(new ShellAdapter() {
            @Override
            public void shellClosed(ShellEvent e) {
                e.doit = false;
                reattach();
            }
        });

        floatingShell.open();
        detached = true;
    }

    /**
     * Reattaches the chat by disposing the floating shell.
     * <p>
     * After calling this method, the docked view should resume displaying
     * conversation content.
     */
    public void reattach() {
        if (floatingShell != null && !floatingShell.isDisposed()) {
            floatingShell.dispose();
        }
        floatingShell = null;
        floatingBrowser = null;
        detached = false;
    }

    /**
     * Executes JavaScript on the floating browser widget.
     * <p>
     * Used to synchronize messages between the docked and floating views.
     * If the chat is not detached or the browser is disposed, this is a no-op.
     *
     * @param js the JavaScript code to execute
     */
    public void executeOnFloating(String js) {
        if (detached && floatingBrowser != null && !floatingBrowser.isDisposed()) {
            floatingBrowser.execute(js);
        }
    }

    /**
     * Toggles the always-on-top state of the floating window.
     * <p>
     * Because {@code SWT.ON_TOP} can only be set at shell creation time,
     * toggling requires recreating the floating shell. The current HTML content
     * is preserved and restored in the new shell.
     */
    public void toggleAlwaysOnTop() {
        alwaysOnTop = !alwaysOnTop;
        if (floatingShell != null && !floatingShell.isDisposed()) {
            // Capture current content before destroying
            String html = null;
            try {
                html = (String) floatingBrowser.evaluate("return document.documentElement.outerHTML;");
            } catch (Exception e) {
                // Fall back: content will be lost
            }
            Shell parent = floatingShell.getParent() instanceof Shell
                    ? (Shell) floatingShell.getParent()
                    : floatingShell.getDisplay().getActiveShell();

            // Dispose current floating shell
            floatingShell.dispose();
            floatingShell = null;
            floatingBrowser = null;
            detached = false;

            // Recreate with new style
            if (html != null && parent != null) {
                detach(parent, html);
            }
        }
    }

    /**
     * Registers a {@link BrowserFunction} on the floating browser.
     * <p>
     * This allows Java callbacks (e.g., UUID navigation) to work in the
     * floating window the same way they do in the docked view.
     *
     * @param name     the JavaScript function name
     * @param function the browser function to delegate to
     */
    public void registerFunction(String name, BrowserFunction function) {
        if (floatingBrowser != null && !floatingBrowser.isDisposed()) {
            new BrowserFunction(floatingBrowser, name) {
                @Override
                public Object function(Object[] args) {
                    return function.function(args);
                }
            };
        }
    }

    /**
     * Returns whether the chat is currently detached into a floating window.
     *
     * @return {@code true} if detached, {@code false} otherwise
     */
    public boolean isDetached() {
        return detached;
    }

    /**
     * Returns the floating browser widget, or {@code null} if not detached.
     *
     * @return the floating {@link Browser} or {@code null}
     */
    public Browser getFloatingBrowser() {
        return floatingBrowser;
    }

    /**
     * Returns whether the always-on-top flag is set.
     *
     * @return {@code true} if always-on-top is enabled
     */
    public boolean isAlwaysOnTop() {
        return alwaysOnTop;
    }

    /**
     * Disposes the floating shell if it exists.
     * Should be called when the parent view is disposed.
     */
    public void dispose() {
        if (floatingShell != null && !floatingShell.isDisposed()) {
            floatingShell.dispose();
        }
        floatingShell = null;
        floatingBrowser = null;
        detached = false;
    }
}

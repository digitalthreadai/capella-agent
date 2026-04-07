package com.capellaagent.modelchat.ui.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

import com.capellaagent.modelchat.ui.views.ModelChatView;

/**
 * Command handler that opens (or focuses) the AI Model Chat view.
 * <p>
 * Bound to the {@code com.capellaagent.modelchat.ui.commands.openChatView}
 * command with the keyboard shortcut Ctrl+Shift+M (M1+M2+M in Eclipse notation).
 * If the view is already open it is merely activated; if not it is opened.
 */
public class OpenChatViewHandler extends AbstractHandler {

    public static final String COMMAND_ID =
            "com.capellaagent.modelchat.ui.commands.openChatView";

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
        if (page == null) return null;
        try {
            page.showView(ModelChatView.VIEW_ID);
        } catch (PartInitException e) {
            throw new ExecutionException("Could not open AI Model Chat view", e);
        }
        return null;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}

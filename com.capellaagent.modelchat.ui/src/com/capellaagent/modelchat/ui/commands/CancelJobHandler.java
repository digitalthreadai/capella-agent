package com.capellaagent.modelchat.ui.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.handlers.HandlerUtil;

import com.capellaagent.modelchat.ui.views.ModelChatView;

/**
 * Command handler that cancels the currently running chat job.
 * <p>
 * Bound to the ESC key when the Model Chat view is active. Delegates to
 * {@link ModelChatView#cancelActiveJob()}, which cancels the Eclipse Job,
 * clears the in-flight flag, and immediately re-enables the input field.
 */
public class CancelJobHandler extends AbstractHandler {

    public static final String COMMAND_ID =
            "com.capellaagent.modelchat.ui.commands.cancelJob";

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        ModelChatView chatView = findChatView(event);
        if (chatView != null) {
            chatView.cancelActiveJob();
        }
        return null;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    private ModelChatView findChatView(ExecutionEvent event) {
        IWorkbenchPart activePart = HandlerUtil.getActivePart(event);
        if (activePart instanceof ModelChatView view) {
            return view;
        }
        IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
        if (page != null) {
            IWorkbenchPart viewPart = page.findView(ModelChatView.VIEW_ID);
            if (viewPart instanceof ModelChatView view) {
                return view;
            }
        }
        return null;
    }
}

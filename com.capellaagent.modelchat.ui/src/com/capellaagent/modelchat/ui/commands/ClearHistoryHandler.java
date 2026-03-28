package com.capellaagent.modelchat.ui.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.handlers.HandlerUtil;

import com.capellaagent.modelchat.ui.views.ModelChatView;

/**
 * Command handler that clears the conversation history in the Model Chat view.
 * <p>
 * This handler is bound to the {@code com.capellaagent.modelchat.ui.commands.clearHistory}
 * command and is activated when the Model Chat view is the active part. It delegates
 * to {@link ModelChatView#clearHistory()} to reset the conversation state.
 *
 * @see ModelChatView#clearHistory()
 */
public class ClearHistoryHandler extends AbstractHandler {

    /** The command identifier matching the plugin.xml registration. */
    public static final String COMMAND_ID = "com.capellaagent.modelchat.ui.commands.clearHistory";

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        ModelChatView chatView = getModelChatView(event);
        if (chatView != null) {
            chatView.clearConversation();
        }
        return null;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * Resolves the {@link ModelChatView} from the current workbench state.
     *
     * @param event the execution event providing workbench context
     * @return the ModelChatView instance, or null if not found
     */
    private ModelChatView getModelChatView(ExecutionEvent event) {
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

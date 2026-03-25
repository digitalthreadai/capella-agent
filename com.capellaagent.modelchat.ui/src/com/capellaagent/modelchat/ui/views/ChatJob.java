package com.capellaagent.modelchat.ui.views;

import java.util.List;
import java.util.function.Consumer;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import com.capellaagent.core.llm.LlmMessage;
import com.capellaagent.core.llm.LlmResponse;
import com.capellaagent.core.llm.LlmToolCall;
import com.capellaagent.core.llm.LlmToolResult;
import com.capellaagent.core.session.ConversationSession;
import com.capellaagent.core.tools.ToolRegistry;
import com.capellaagent.core.tools.ToolResult;
import com.capellaagent.modelchat.ui.ModelChatUiActivator;

// PLACEHOLDER imports for LLM provider registry
// import com.capellaagent.core.llm.LlmProvider;
// import com.capellaagent.core.llm.LlmProviderRegistry;
// import com.capellaagent.core.llm.ChatConfig;

/**
 * Eclipse {@link Job} that handles the asynchronous LLM chat orchestration loop.
 * <p>
 * The job:
 * <ol>
 *   <li>Adds the user message to the conversation session</li>
 *   <li>Retrieves the active LLM provider and available tools</li>
 *   <li>Enters the orchestration loop:
 *     <ul>
 *       <li>Sends messages + tools to the LLM provider</li>
 *       <li>If the response contains tool calls, executes each tool and feeds results back</li>
 *       <li>If the response contains text, passes it to the UI callback</li>
 *       <li>Continues until the LLM produces a final text response with no tool calls</li>
 *     </ul>
 *   </li>
 *   <li>Signals completion to the UI via the onComplete callback</li>
 * </ol>
 * <p>
 * All UI callbacks are invoked from the job thread; the caller (ModelChatView) is
 * responsible for dispatching to the SWT UI thread via {@code Display.asyncExec()}.
 */
public class ChatJob extends Job {

    private static final String JOB_NAME = "AI Model Chat";
    private static final int MAX_TOOL_ITERATIONS = 20;

    private final ConversationSession session;
    private final String userMessage;
    private final String providerName;
    private final Consumer<String> onTextResponse;
    private final Consumer<String> onToolExecution;
    private final Runnable onComplete;

    /**
     * Constructs a new ChatJob.
     *
     * @param session         the conversation session maintaining message history
     * @param userMessage     the new user message to process
     * @param providerName    the name of the LLM provider to use
     * @param onTextResponse  callback invoked with the assistant's text response
     * @param onToolExecution callback invoked when a tool execution starts (receives tool name)
     * @param onComplete      callback invoked when the job finishes (success or failure)
     */
    public ChatJob(ConversationSession session, String userMessage, String providerName,
                   Consumer<String> onTextResponse, Consumer<String> onToolExecution,
                   Runnable onComplete) {
        super(JOB_NAME);
        this.session = session;
        this.userMessage = userMessage;
        this.providerName = providerName;
        this.onTextResponse = onTextResponse;
        this.onToolExecution = onToolExecution;
        this.onComplete = onComplete;
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
        try {
            monitor.beginTask("Processing chat message", IProgressMonitor.UNKNOWN);

            // Add user message to the session
            session.addUserMessage(userMessage);

            // PLACEHOLDER: Get the LLM provider from registry
            // LlmProvider provider = LlmProviderRegistry.getInstance().getProvider(providerName);
            // if (provider == null) {
            //     onTextResponse.accept("Error: LLM provider '" + providerName + "' not found.");
            //     return Status.OK_STATUS;
            // }

            // Get available tools from the registry
            ToolRegistry toolRegistry = ToolRegistry.getInstance();
            List<String> toolCategories = List.of("model_read", "model_write", "diagram");

            // PLACEHOLDER: Build tool definitions for the LLM
            // List<ToolDefinition> toolDefs = toolRegistry.getToolDefinitions(toolCategories);

            // Build chat configuration
            // ChatConfig config = ChatConfig.builder()
            //     .maxTokens(4096)
            //     .temperature(0.1)
            //     .build();

            // Orchestration loop
            boolean done = false;
            int iterations = 0;

            while (!done && !monitor.isCanceled() && iterations < MAX_TOOL_ITERATIONS) {
                iterations++;

                // PLACEHOLDER: Send messages + tools to the LLM provider
                // LlmResponse response = provider.chat(
                //     session.getMessages(),
                //     toolDefs,
                //     config
                // );
                LlmResponse response = callLlmProvider(session, providerName);

                if (response == null) {
                    onTextResponse.accept("Error: No response from LLM provider.");
                    done = true;
                    continue;
                }

                // Handle tool calls
                if (response.hasToolCalls()) {
                    // Add the assistant message with tool calls to session
                    // PLACEHOLDER: session.addAssistantMessageWithToolCalls(response);

                    for (LlmToolCall toolCall : response.getToolCalls()) {
                        if (monitor.isCanceled()) {
                            break;
                        }

                        String toolName = toolCall.getName();
                        String toolArgs = toolCall.getArguments();
                        String toolCallId = toolCall.getId();

                        // Notify UI about tool execution
                        onToolExecution.accept(toolName);

                        // Execute the tool
                        ToolResult toolResult;
                        try {
                            toolResult = toolRegistry.executeTool(toolName, toolArgs);
                        } catch (Exception e) {
                            toolResult = ToolResult.error(
                                    "Tool execution failed: " + e.getMessage());
                        }

                        // Add tool result to session
                        session.addToolResult(toolCallId, toolResult.toJson());
                    }
                }

                // Handle text content
                if (response.hasTextContent()) {
                    String text = response.getTextContent();
                    onTextResponse.accept(text);

                    // Add assistant text to session
                    session.addAssistantMessage(text);
                }

                // If no tool calls in this iteration, we are done
                if (!response.hasToolCalls()) {
                    done = true;
                }
            }

            if (monitor.isCanceled()) {
                onTextResponse.accept("[Chat cancelled by user]");
                return Status.CANCEL_STATUS;
            }

            if (iterations >= MAX_TOOL_ITERATIONS) {
                onTextResponse.accept("[Reached maximum tool iteration limit ("
                        + MAX_TOOL_ITERATIONS + "). Stopping.]");
            }

            return Status.OK_STATUS;

        } catch (Exception e) {
            onTextResponse.accept("Error: " + e.getMessage());
            return new Status(IStatus.ERROR, ModelChatUiActivator.PLUGIN_ID,
                    "Chat job failed", e);
        } finally {
            if (onComplete != null) {
                onComplete.run();
            }
            monitor.done();
        }
    }

    /**
     * Calls the LLM provider with the current session messages and tool definitions.
     * <p>
     * PLACEHOLDER: This method wraps the actual LLM provider invocation. The real
     * implementation should retrieve the provider from the registry and call its
     * chat method with the session messages and tool definitions.
     *
     * @param session      the conversation session
     * @param providerName the name of the LLM provider
     * @return the LLM response, or null if the provider could not be reached
     */
    private LlmResponse callLlmProvider(ConversationSession session, String providerName) {
        // PLACEHOLDER: Replace with actual provider invocation
        //
        // LlmProvider provider = LlmProviderRegistry.getInstance().getProvider(providerName);
        // if (provider == null) return null;
        //
        // List<ToolDefinition> toolDefs = ToolRegistry.getInstance()
        //     .getToolDefinitions(List.of("model_read", "model_write", "diagram"));
        //
        // ChatConfig config = ChatConfig.builder()
        //     .maxTokens(4096)
        //     .temperature(0.1)
        //     .systemPrompt(buildSystemPrompt())
        //     .build();
        //
        // return provider.chat(session.getMessages(), toolDefs, config);

        return null;
    }
}

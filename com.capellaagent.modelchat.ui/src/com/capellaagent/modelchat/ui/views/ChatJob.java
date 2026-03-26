package com.capellaagent.modelchat.ui.views;

import java.util.List;
import java.util.function.Consumer;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import com.capellaagent.core.config.AgentConfiguration;
import com.capellaagent.core.llm.ILlmProvider;
import com.capellaagent.core.llm.LlmException;
import com.capellaagent.core.llm.LlmMessage;
import com.capellaagent.core.llm.LlmProviderRegistry;
import com.capellaagent.core.llm.LlmRequestConfig;
import com.capellaagent.core.llm.LlmResponse;
import com.capellaagent.core.llm.LlmToolCall;
import com.capellaagent.core.llm.LlmToolResult;
import com.capellaagent.core.session.ConversationSession;
import com.capellaagent.core.tools.IToolDescriptor;
import com.capellaagent.core.tools.ToolRegistry;
import com.capellaagent.core.tools.ToolResult;
import com.capellaagent.modelchat.ui.ModelChatUiActivator;

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
    private static final int MAX_WRITE_TOOLS_PER_TURN = 5;

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
            List<String> toolCategories = List.of(
                    "model_read", "model_write", "diagram",
                    "analysis", "export", "transition");

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
            int writeToolCount = 0;

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

                        // Rate limit: check if this is a write tool
                        boolean isWriteTool = isWriteToolName(toolName);
                        if (isWriteTool) {
                            writeToolCount++;
                            if (writeToolCount > MAX_WRITE_TOOLS_PER_TURN) {
                                onTextResponse.accept(
                                        "[Safety limit reached: more than "
                                        + MAX_WRITE_TOOLS_PER_TURN
                                        + " write operations in a single turn. "
                                        + "Please review the changes made so far "
                                        + "before continuing.]");
                                done = true;
                                break;
                            }
                        }

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
     * Determines whether a tool name corresponds to a write operation.
     * Write tools modify the Capella model and are subject to rate limiting.
     *
     * @param toolName the tool name to check
     * @return true if the tool performs write operations
     */
    private boolean isWriteToolName(String toolName) {
        if (toolName == null) return false;
        return toolName.equals("create_element")
                || toolName.equals("update_element")
                || toolName.equals("delete_element")
                || toolName.equals("allocate_function")
                || toolName.equals("create_capability")
                || toolName.equals("create_exchange")
                || toolName.equals("update_diagram")
                || toolName.equals("create_interface")
                || toolName.equals("create_functional_chain")
                || toolName.equals("create_physical_link")
                || toolName.equals("batch_rename")
                || toolName.equals("create_diagram")
                || toolName.equals("transition_oa_to_sa")
                || toolName.equals("transition_sa_to_la")
                || toolName.equals("transition_la_to_pa");
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
        try {
            // Get the active provider from registry (reads from AgentConfiguration)
            ILlmProvider provider = LlmProviderRegistry.getInstance().getActiveProvider();

            // Get ONLY model-relevant tools (exclude Teamcenter + Simulation to save tokens)
            List<IToolDescriptor> tools = ToolRegistry.getInstance()
                    .getTools("model_read", "model_write", "diagram",
                              "analysis", "export", "transition");

            // Debug: log tool count
            java.util.logging.Logger.getLogger("ChatJob").info(
                    "LLM call with " + tools.size() + " model tools, provider: "
                    + provider.getDisplayName());
            if (tools.isEmpty()) {
                onTextResponse.accept("[Warning: No tools registered. "
                        + "Please close and reopen the AI Model Chat view.]");
            }

            // Build request config — concise system prompt to minimize token usage
            AgentConfiguration config = AgentConfiguration.getInstance();
            String systemPrompt = "You are a Capella MBSE assistant with tool access. "
                    + "ALWAYS use the provided tools to query and modify the model. "
                    + "Never guess model content — call tools to get real data.\n"
                    + "For write operations: describe what you will do and ask user to confirm before calling the tool.";

            LlmRequestConfig requestConfig = new LlmRequestConfig(
                    config.getLlmModelId().isEmpty() ? null : config.getLlmModelId(),
                    config.getLlmTemperature(),
                    config.getLlmMaxTokens(),
                    systemPrompt
            );

            // Call the LLM
            return provider.chat(session.getMessages(), tools, requestConfig);

        } catch (LlmException e) {
            onTextResponse.accept("LLM Error: " + e.getMessage());
            return null;
        } catch (Exception e) {
            onTextResponse.accept("Error connecting to LLM: " + e.getMessage());
            return null;
        }
    }
}

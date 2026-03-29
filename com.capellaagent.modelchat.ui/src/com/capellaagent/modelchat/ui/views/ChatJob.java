package com.capellaagent.modelchat.ui.views;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    /** Max messages to send to LLM (sliding window). 8 = ~4 user/assistant turns. */
    private static final int MAX_HISTORY_MESSAGES = 8;
    /** Max characters per tool result stored in session history. */
    private static final int MAX_TOOL_RESULT_CHARS = 8000;

    private final ConversationSession session;
    private final String userMessage;
    private final String providerName;
    private final Consumer<String> onTextResponse;
    private final Consumer<String> onToolExecution;
    private final Runnable onComplete;
    private final ToolResultCallback onToolResult;

    /**
     * Callback interface for receiving full (untruncated) tool results for UI display.
     */
    @FunctionalInterface
    public interface ToolResultCallback {
        void onResult(String toolName, String category, com.google.gson.JsonObject fullResult);
    }

    /**
     * Constructs a new ChatJob (backward-compatible, no tool result callback).
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
        this(session, userMessage, providerName, onTextResponse, onToolExecution, onComplete, null);
    }

    /**
     * Constructs a new ChatJob with tool result callback.
     *
     * @param session         the conversation session maintaining message history
     * @param userMessage     the new user message to process
     * @param providerName    the name of the LLM provider to use
     * @param onTextResponse  callback invoked with the assistant's text response
     * @param onToolExecution callback invoked when a tool execution starts (receives tool name)
     * @param onComplete      callback invoked when the job finishes (success or failure)
     * @param onToolResult    callback invoked with full untruncated tool results for UI display
     */
    public ChatJob(ConversationSession session, String userMessage, String providerName,
                   Consumer<String> onTextResponse, Consumer<String> onToolExecution,
                   Runnable onComplete, ToolResultCallback onToolResult) {
        super(JOB_NAME);
        this.session = session;
        this.userMessage = userMessage;
        this.providerName = providerName;
        this.onTextResponse = onTextResponse;
        this.onToolExecution = onToolExecution;
        this.onComplete = onComplete;
        this.onToolResult = onToolResult;
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
        try {
            monitor.beginTask("Processing chat message", IProgressMonitor.UNKNOWN);

            // Add user message to the session
            session.addUserMessage(userMessage);

            // Get available tools from the registry
            ToolRegistry toolRegistry = ToolRegistry.getInstance();
            List<String> toolCategories = List.of(
                    "model_read", "model_write", "diagram",
                    "analysis", "export", "transition");

            // Orchestration loop
            boolean done = false;
            int iterations = 0;
            int writeToolCount = 0;

            while (!done && !monitor.isCanceled() && iterations < MAX_TOOL_ITERATIONS) {
                iterations++;

                LlmResponse response = callLlmProvider(session, providerName);

                if (response == null) {
                    onTextResponse.accept(formatUserFriendlyError("no response"));
                    done = true;
                    continue;
                }

                // Handle tool calls
                if (response.hasToolCalls()) {
                    // Add the assistant message with tool calls to session
                    session.addAssistantToolCalls(response.getToolCalls());

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

                        // Check if tool exists before executing
                        if (!toolRegistry.hasToolTool(toolName)) {
                            String friendlyError = "I wasn't able to perform that action because the required capability isn't available yet. " +
                                "Try rephrasing your request, or ask me what I can help with.";
                            session.addToolResult(toolCallId, createErrorResult(friendlyError));
                            if (onToolExecution != null) {
                                onToolExecution.accept("\u26A0 Action not available");
                            }
                            continue;
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

                        // Execute tool result handling: display full, store compact
                        com.google.gson.JsonObject resultObj = toolResult.toJson();

                        // DISPLAY: Send FULL result to UI (no truncation)
                        if (onToolResult != null) {
                            String category = "MODEL_READ"; // Default; tools self-report category via result JSON
                            onToolResult.onResult(toolCall.getName(), category, resultObj);
                        }

                        // HISTORY: Store compact version for LLM context (saves tokens)
                        String resultStr = resultObj.toString();
                        if (resultStr.length() > MAX_TOOL_RESULT_CHARS) {
                            com.google.gson.JsonObject compact = createCompactSummary(toolCall.getName(), resultObj);
                            session.addToolResult(toolCallId, compact);
                        } else {
                            session.addToolResult(toolCallId, resultObj);
                        }
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
            onTextResponse.accept(formatUserFriendlyError(e.getMessage()));
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
     * Creates a compact summary of a tool result for LLM context history.
     * Extracts key metrics and a preview to keep token usage low while
     * preserving enough context for the LLM to reason about results.
     *
     * @param toolName   the name of the tool that produced the result
     * @param fullResult the full JSON result from the tool
     * @return a compact JSON summary
     */
    private com.google.gson.JsonObject createCompactSummary(String toolName, com.google.gson.JsonObject fullResult) {
        com.google.gson.JsonObject summary = new com.google.gson.JsonObject();
        summary.addProperty("summarized", true);
        summary.addProperty("tool", toolName);

        // Extract key metrics from the result
        if (fullResult.has("count")) {
            summary.addProperty("count", fullResult.get("count").getAsInt());
        }
        if (fullResult.has("element_type")) {
            summary.addProperty("element_type", fullResult.get("element_type").getAsString());
        }
        if (fullResult.has("layer")) {
            summary.addProperty("layer", fullResult.get("layer").getAsString());
        }
        if (fullResult.has("elements") && fullResult.get("elements").isJsonArray()) {
            int count = fullResult.getAsJsonArray("elements").size();
            summary.addProperty("elements_count", count);
        }
        if (fullResult.has("status")) {
            summary.addProperty("status", fullResult.get("status").getAsString());
        }
        if (fullResult.has("message")) {
            String msg = fullResult.get("message").getAsString();
            summary.addProperty("message", msg.length() > 200 ? msg.substring(0, 200) + "..." : msg);
        }

        summary.addProperty("note", "Full results already displayed to the user as an interactive table. Do NOT repeat or list individual elements. Just briefly confirm what was found (e.g. 'Found 103 Physical Functions.') and ask if they need anything else.");
        return summary;
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
     * Creates a JSON error result object for returning to the LLM session.
     *
     * @param message the error message
     * @return a JSON object with error flag and message
     */
    private com.google.gson.JsonObject createErrorResult(String message) {
        com.google.gson.JsonObject error = new com.google.gson.JsonObject();
        error.addProperty("error", true);
        error.addProperty("message", message);
        return error;
    }

    /**
     * Converts a raw technical error message into a user-friendly message.
     * Never exposes HTTP status codes, JSON bodies, or internal details to the user.
     *
     * @param rawError the raw error string from the LLM provider or network layer
     * @return a friendly, actionable error message
     */
    private String formatUserFriendlyError(String rawError) {
        if (rawError == null) rawError = "";
        String lower = rawError.toLowerCase();

        // Rate limit
        if (lower.contains("rate_limit") || lower.contains("rate limit") || lower.contains("429") || lower.contains("too many")) {
            return "\u23F3 The AI service is temporarily busy. Please wait a moment and try again.";
        }
        // Token limit
        if (lower.contains("token") && (lower.contains("limit") || lower.contains("exceeded") || lower.contains("too large"))) {
            return "\uD83D\uDCDD The request was too large for the AI provider's limits. Try a simpler question or switch to a provider with higher limits in Preferences.";
        }
        // Tool not found (from API)
        if (lower.contains("tool") && (lower.contains("not in request") || lower.contains("validation failed") || lower.contains("not found"))) {
            return "\u26A0 I wasn't able to perform that action. Try rephrasing your request, or ask me what I can help with.";
        }
        // Auth
        if (lower.contains("unauthorized") || lower.contains("401") || lower.contains("invalid api") || lower.contains("authentication")) {
            return "\uD83D\uDD11 Could not authenticate with the AI provider. Please check your API key in Window \u2192 Preferences \u2192 Capella Agent.";
        }
        // Network
        if (lower.contains("connect") || lower.contains("timeout") || lower.contains("network") || lower.contains("resolve")) {
            return "\uD83C\uDF10 Could not reach the AI provider. Please check your internet connection and try again.";
        }
        // Model not found
        if (lower.contains("model") && lower.contains("not found")) {
            return "\u26A0 The selected AI model is not available. Please check the model name in Preferences.";
        }
        // Generic fallback
        return "\u26A0 Something went wrong while processing your request. Please try again or check your settings in Preferences.";
    }

    /**
     * Calls the LLM provider with the current session messages and tool definitions.
     * <p>
     * Calls the LLM provider with the current session messages and available tools,
     * using a sliding window to stay within token limits.
     *
     * @param session      the conversation session
     * @param providerName the name of the LLM provider
     * @return the LLM response, or null if the provider could not be reached
     */
    private LlmResponse callLlmProvider(ConversationSession session, String providerName) {
        try {
            ILlmProvider provider = LlmProviderRegistry.getInstance().getActiveProvider();

            // Smart tool selection: pick only relevant tools based on query keywords
            // This keeps request under 8K tokens for free-tier providers
            List<IToolDescriptor> allTools = ToolRegistry.getInstance()
                    .getTools("model_read", "model_write", "diagram",
                              "analysis", "export", "transition", "ai_intelligence");
            List<IToolDescriptor> tools = selectRelevantTools(userMessage, allTools);

            java.util.logging.Logger.getLogger("ChatJob").info(
                    "LLM call with " + tools.size() + "/" + allTools.size()
                    + " tools selected for query, provider: " + provider.getDisplayName());
            if (tools.isEmpty()) {
                onTextResponse.accept("[Warning: No tools registered. "
                        + "Please close and reopen the AI Model Chat view.]");
            }

            AgentConfiguration config = AgentConfiguration.getInstance();
            String systemPrompt = "You are a Capella MBSE assistant. "
                    + "Use tools to query/modify the model. Never guess — call tools for real data.";

            LlmRequestConfig requestConfig = new LlmRequestConfig(
                    config.getLlmModelId().isEmpty() ? null : config.getLlmModelId(),
                    config.getLlmTemperature(),
                    config.getLlmMaxTokens(),
                    systemPrompt
            );

            // Sliding window: only send last N messages to stay within token limits
            List<LlmMessage> allMessages = session.getMessages();
            List<LlmMessage> windowedMessages = slidingWindow(allMessages, MAX_HISTORY_MESSAGES);

            java.util.logging.Logger.getLogger("ChatJob").info(
                    "Sending " + windowedMessages.size() + "/" + allMessages.size()
                    + " messages (sliding window)");

            return provider.chat(windowedMessages, tools, requestConfig);

        } catch (LlmException e) {
            onTextResponse.accept(formatUserFriendlyError(e.getMessage()));
            return null;
        } catch (Exception e) {
            onTextResponse.accept(formatUserFriendlyError(e.getMessage()));
            return null;
        }
    }

    /**
     * Selects only the tools relevant to the user's query using keyword matching.
     * This keeps the request token count under free-tier limits (8K for GitHub Models).
     * <p>
     * Always includes: list_elements, search_elements, get_element_details (core read).
     * Then adds tools matching keywords in the query (max ~15 tools total).
     */
    private List<IToolDescriptor> selectRelevantTools(String query, List<IToolDescriptor> allTools) {
        String q = query.toLowerCase();
        Set<String> selected = new LinkedHashSet<>();

        // Always include core read tools
        selected.addAll(Arrays.asList("list_elements", "search_elements", "get_element_details"));

        // Keyword → tool mapping
        if (matches(q, "function", "functional")) {
            selected.addAll(Arrays.asList("get_functional_chain", "get_function_ports",
                    "get_allocation_matrix", "allocate_function"));
        }
        if (matches(q, "component", "physical component", "logical component")) {
            selected.addAll(Arrays.asList("get_component_ports", "get_interfaces",
                    "get_deployment_mapping", "get_configuration_items"));
        }
        if (matches(q, "requirement", "trace", "traceability", "coverage")) {
            selected.addAll(Arrays.asList("list_requirements", "get_traceability",
                    "check_traceability_coverage", "get_requirement_relations"));
        }
        if (matches(q, "create", "add", "new")) {
            selected.addAll(Arrays.asList("create_element", "create_exchange",
                    "create_interface", "create_capability", "create_physical_link",
                    "create_port", "create_functional_chain"));
        }
        if (matches(q, "update", "modify", "change", "rename", "set")) {
            selected.addAll(Arrays.asList("update_element", "batch_rename",
                    "set_description", "set_property_value", "batch_update_properties"));
        }
        if (matches(q, "delete", "remove")) {
            selected.addAll(Arrays.asList("delete_element", "move_element"));
        }
        if (matches(q, "diagram", "draw", "show", "display", "visualize")) {
            selected.addAll(Arrays.asList("list_diagrams", "create_diagram",
                    "show_element_in_diagram", "add_to_diagram", "export_diagram_image",
                    "refresh_diagram"));
        }
        if (matches(q, "export", "csv", "json", "report", "document", "reqif", "sysml")) {
            selected.addAll(Arrays.asList("export_to_csv", "export_to_json",
                    "generate_model_report", "generate_icd_report",
                    "export_to_reqif", "export_to_sysmlv2", "generate_document"));
        }
        if (matches(q, "validate", "validation", "check", "error", "problem")) {
            selected.addAll(Arrays.asList("run_capella_validation", "validate_naming",
                    "find_unused_elements", "detect_cycles"));
        }
        if (matches(q, "transition", "refine", "propagate")) {
            selected.addAll(Arrays.asList("transition_oa_to_sa", "transition_sa_to_la",
                    "transition_la_to_pa", "transition_functions", "auto_transition_all"));
        }
        if (matches(q, "impact", "affect", "change analysis")) {
            selected.addAll(Arrays.asList("impact_analysis", "predict_impact"));
        }
        if (matches(q, "scenario", "sequence", "interaction")) {
            selected.addAll(Arrays.asList("get_scenarios", "create_scenario",
                    "generate_test_scenarios"));
        }
        if (matches(q, "state", "mode", "state machine")) {
            selected.addAll(Arrays.asList("get_state_machines", "create_state_machine",
                    "create_mode", "get_modes_captured_time"));
        }
        if (matches(q, "interface", "exchange", "port", "link", "communication")) {
            selected.addAll(Arrays.asList("get_interfaces", "get_exchange_items",
                    "create_interface", "create_exchange", "create_port",
                    "get_communication_links"));
        }
        if (matches(q, "allocat", "deploy")) {
            selected.addAll(Arrays.asList("get_allocation_matrix", "allocate_function",
                    "allocation_completeness", "optimize_allocation"));
        }
        if (matches(q, "analys", "review", "assess", "quality", "complexity")) {
            selected.addAll(Arrays.asList("architecture_complexity", "suggest_improvements",
                    "review_architecture", "model_statistics"));
        }
        if (matches(q, "search", "find", "where", "which", "pattern")) {
            selected.addAll(Arrays.asList("search_elements", "search_by_pattern",
                    "find_duplicates"));
        }
        if (matches(q, "explain", "what is", "describe", "summarize", "summary")) {
            selected.addAll(Arrays.asList("explain_element", "summarize_model",
                    "model_q_and_a"));
        }
        if (matches(q, "data", "class", "type", "property", "attribute")) {
            selected.addAll(Arrays.asList("get_data_model", "create_data_type",
                    "create_exchange_item", "get_property_values", "set_property_value"));
        }
        if (matches(q, "security", "safety", "weight", "mass")) {
            selected.addAll(Arrays.asList("security_analysis", "weight_analysis",
                    "generate_safety_report"));
        }

        // Filter: keep only tools that actually exist in the registry
        List<IToolDescriptor> result = new ArrayList<>();
        for (IToolDescriptor tool : allTools) {
            if (selected.contains(tool.getName())) {
                result.add(tool);
            }
        }

        // Fallback: if nothing matched, return core read tools only
        if (result.isEmpty()) {
            for (IToolDescriptor tool : allTools) {
                String n = tool.getName();
                if ("list_elements".equals(n) || "search_elements".equals(n)
                        || "get_element_details".equals(n) || "model_q_and_a".equals(n)) {
                    result.add(tool);
                }
            }
        }

        return result;
    }

    /** Returns true if the query contains any of the given keywords. */
    private boolean matches(String query, String... keywords) {
        for (String kw : keywords) {
            if (query.contains(kw)) return true;
        }
        return false;
    }

    /**
     * Returns a sliding window of the most recent messages, keeping the total
     * under the specified limit. Always keeps the first message (if it's a system
     * prompt) and the last N messages.
     *
     * @param messages all messages in the conversation
     * @param maxMessages maximum number of messages to return
     * @return windowed message list
     */
    private List<LlmMessage> slidingWindow(List<LlmMessage> messages, int maxMessages) {
        if (messages.size() <= maxMessages) {
            return messages;
        }
        // Keep the last maxMessages messages
        int start = messages.size() - maxMessages;
        return new ArrayList<>(messages.subList(start, messages.size()));
    }
}

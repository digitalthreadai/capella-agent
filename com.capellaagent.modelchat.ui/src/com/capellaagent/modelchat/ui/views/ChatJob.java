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

import com.capellaagent.core.capella.ActiveProjectContext;
import com.capellaagent.core.config.AgentConfiguration;
import com.capellaagent.core.llm.ILlmProvider;
import com.capellaagent.core.llm.LlmException;
import com.capellaagent.core.llm.LlmMessage;
import com.capellaagent.core.llm.LlmProviderRegistry;
import com.capellaagent.core.llm.LlmRequestConfig;
import com.capellaagent.core.llm.LlmResponse;
import com.capellaagent.core.llm.LlmToolCall;
import com.capellaagent.core.llm.LlmToolResult;
import com.capellaagent.core.session.AgentMode;
import com.capellaagent.core.session.ConversationSession;
import com.capellaagent.core.session.IAgentMode;
import com.capellaagent.core.session.ChatSessionController;
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
    /** Hard ceiling shared with ChatSessionController — cannot be exceeded by any mode. */
    private static final int HARD_ITERATION_CEILING = ChatSessionController.HARD_ITERATION_CEILING;
    private static final int MAX_WRITE_TOOLS_PER_TURN = 5;
    /** Max messages to send to LLM (sliding window). 8 = ~4 user/assistant turns. */
    private static final int MAX_HISTORY_MESSAGES = 8;
    /** Max characters per tool result stored in session history. */
    private static final int MAX_TOOL_RESULT_CHARS = 8000;

    private final ConversationSession session;
    private final String userMessage;
    private final String providerName;
    /**
     * The Capella project name selected in the chat view's project dropdown.
     * Threaded into {@link ActiveProjectContext} for the duration of the
     * orchestration loop so tools resolve the correct Sirius session when
     * multiple projects are open. May be {@code null} for single-project
     * workspaces or when the dropdown has no selection.
     */
    private final String activeProjectName;
    private final IAgentMode agentMode;
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
     * Constructs a new ChatJob (backward-compatible, no tool result callback,
     * no project disambiguation). Defaults to {@link AgentMode#GENERAL}.
     */
    public ChatJob(ConversationSession session, String userMessage, String providerName,
                   Consumer<String> onTextResponse, Consumer<String> onToolExecution,
                   Runnable onComplete) {
        this(session, userMessage, providerName, null, AgentMode.GENERAL,
                onTextResponse, onToolExecution, onComplete, null);
    }

    /**
     * Constructs a new ChatJob with tool result callback (no project
     * disambiguation). Defaults to {@link AgentMode#GENERAL}.
     */
    public ChatJob(ConversationSession session, String userMessage, String providerName,
                   Consumer<String> onTextResponse, Consumer<String> onToolExecution,
                   Runnable onComplete, ToolResultCallback onToolResult) {
        this(session, userMessage, providerName, null, AgentMode.GENERAL,
                onTextResponse, onToolExecution, onComplete, onToolResult);
    }

    /**
     * Constructs a new ChatJob with full context (project disambiguation +
     * agent mode + tool result callback). This is the canonical constructor
     * used by {@code ModelChatView.sendMessage()}.
     *
     * @param session           the conversation session maintaining message history
     * @param userMessage       the new user message to process
     * @param providerName      the name of the LLM provider to use
     * @param activeProjectName the Capella project name, or {@code null}
     * @param agentMode         the active agent mode (determines system prompt and tool categories)
     * @param onTextResponse    callback invoked with the assistant's text response
     * @param onToolExecution   callback invoked when a tool execution starts
     * @param onComplete        callback invoked when the job finishes
     * @param onToolResult      callback for full (untruncated) tool results for UI display
     */
    public ChatJob(ConversationSession session, String userMessage, String providerName,
                   String activeProjectName, IAgentMode agentMode,
                   Consumer<String> onTextResponse, Consumer<String> onToolExecution,
                   Runnable onComplete, ToolResultCallback onToolResult) {
        super(JOB_NAME);
        this.session = session;
        this.userMessage = userMessage;
        this.providerName = providerName;
        this.activeProjectName = activeProjectName;
        this.agentMode = agentMode != null ? agentMode : AgentMode.GENERAL;
        this.onTextResponse = onTextResponse;
        this.onToolExecution = onToolExecution;
        this.onComplete = onComplete;
        this.onToolResult = onToolResult;
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
        // Publish the user-selected project to the orchestration thread so any
        // tool that calls AbstractCapellaTool.getActiveSession() picks the right
        // Sirius session when multiple Capella projects are open. Cleared in
        // the finally below to avoid leaking state into the next job that
        // reuses this Eclipse worker thread.
        ActiveProjectContext.set(activeProjectName);
        try {
            monitor.beginTask("Processing chat message", IProgressMonitor.UNKNOWN);

            // Add user message to the session
            session.addUserMessage(userMessage);

            // Get available tools from the registry
            ToolRegistry toolRegistry = ToolRegistry.getInstance();

            // Orchestration loop — iteration limit is mode-specific, clamped to hard ceiling
            int maxIter = Math.min(agentMode.maxToolIterations(), HARD_ITERATION_CEILING);
            boolean done = false;
            int iterations = 0;
            int writeToolCount = 0;

            while (!done && !monitor.isCanceled() && iterations < maxIter) {
                iterations++;

                LlmResponse response = callLlmProvider(session, providerName);

                if (response == null) {
                    // callLlmProvider() has already posted a specific
                    // user-friendly error message via onTextResponse. Do NOT
                    // post a second generic message — that just adds noise
                    // (e.g. ⏳ rate-limit followed by ⚠ "something went wrong").
                    done = true;
                    continue;
                }

                // Handle tool calls
                if (response.hasToolCalls()) {
                    // Add the assistant message with tool calls to session
                    session.addAssistantToolCalls(response.getToolCalls());

                    List<LlmToolCall> pendingCalls = response.getToolCalls();
                    for (int callIdx = 0; callIdx < pendingCalls.size(); callIdx++) {
                        LlmToolCall toolCall = pendingCalls.get(callIdx);
                        if (monitor.isCanceled()) {
                            break;
                        }
                        // Defensive null guard — providers should never return null elements
                        if (toolCall == null || toolCall.getName() == null) continue;

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
                                // Emit synthetic denied results for all remaining tool calls so
                                // the provider sees every tool_use matched by a tool_result,
                                // preventing "tool_use_id not matched" errors on the next turn.
                                for (int remaining = callIdx; remaining < pendingCalls.size(); remaining++) {
                                    LlmToolCall skipped = pendingCalls.get(remaining);
                                    if (skipped != null && skipped.getId() != null) {
                                        session.addToolResult(skipped.getId(),
                                            createErrorResult("Aborted: write-rate limit per turn exceeded"));
                                    }
                                }
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
                            // SECURITY (A3): do not pass raw exception text back
                            // to the LLM as a tool result — exception messages
                            // routinely echo file paths, prompt fragments, and
                            // provider response bodies. ErrorMessageFilter logs
                            // the full exception at SEVERE.
                            toolResult = ToolResult.error(
                                    com.capellaagent.core.security.ErrorMessageFilter
                                            .safeToolResultMessage(e));
                        }

                        // Execute tool result handling: display full, store compact
                        com.google.gson.JsonObject resultObj = toolResult.toJson();

                        // DISPLAY: Send FULL result to UI (no truncation)
                        if (onToolResult != null) {
                            String category = "";
                            if (resultObj.has("category") && resultObj.get("category").isJsonPrimitive()) {
                                category = resultObj.get("category").getAsString();
                            } else {
                                // Try to get category from tool registry
                                IToolDescriptor toolDesc = toolRegistry.getTools(
                                        "model_read", "model_write", "diagram",
                                        "analysis", "export", "transition",
                                        "requirements", "ai_intelligence")
                                    .stream()
                                    .filter(t -> toolName.equals(t.getName()))
                                    .findFirst()
                                    .orElse(null);
                                if (toolDesc != null) {
                                    category = toolDesc.getCategory() != null
                                            ? toolDesc.getCategory() : "";
                                }
                            }
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

            if (iterations >= maxIter) {
                onTextResponse.accept("[Reached maximum tool iteration limit ("
                        + maxIter + "). Stopping.]");
            }

            return Status.OK_STATUS;

        } catch (Exception e) {
            // SECURITY (A3): route non-LlmException through ErrorMessageFilter
            // so uncategorized exceptions never leak raw getMessage() text.
            String friendly = (e instanceof LlmException)
                ? formatUserFriendlyError(e.getMessage())
                : com.capellaagent.core.security.ErrorMessageFilter.safeUserMessage(e);
            onTextResponse.accept(friendly);
            return new Status(IStatus.ERROR, ModelChatUiActivator.PLUGIN_ID,
                    "Chat job failed", e);
        } finally {
            // Always clear the thread-local active project so it does not leak
            // into the next ChatJob that reuses this worker thread.
            ActiveProjectContext.clear();
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

        // Phase 2: requirements fields
        if (fullResult.has("coverage_pct")) {
            summary.addProperty("coverage_pct", fullResult.get("coverage_pct").getAsDouble());
        }
        if (fullResult.has("requirements_count")) {
            summary.addProperty("requirements_count", fullResult.get("requirements_count").getAsInt());
        }
        if (fullResult.has("trace_count")) {
            summary.addProperty("trace_count", fullResult.get("trace_count").getAsInt());
        }
        if (fullResult.has("total_requirements")) {
            summary.addProperty("total_requirements", fullResult.get("total_requirements").getAsInt());
        }
        if (fullResult.has("traced_requirements")) {
            summary.addProperty("traced_requirements", fullResult.get("traced_requirements").getAsInt());
        }
        // Phase 3: generative fields
        if (fullResult.has("diff_id")) {
            summary.addProperty("diff_id", fullResult.get("diff_id").getAsString());
        }
        if (fullResult.has("proposal_count")) {
            summary.addProperty("proposal_count", fullResult.get("proposal_count").getAsInt());
        }

        summary.addProperty("note", "Full results already displayed to the user as an interactive table. Do NOT repeat or list individual elements. Just briefly confirm what was found (e.g. 'Found 103 Physical Functions.') and ask if they need anything else.");
        return summary;
    }

    /**
     * Determines whether a tool name corresponds to a write operation.
     * Uses the tool registry to check category rather than a hardcoded list,
     * so any future write tools are automatically covered.
     *
     * @param toolName the tool name to check
     * @return true if the tool is registered under a write-capable category
     */
    private boolean isWriteToolName(String toolName) {
        if (toolName == null) return false;
        return ToolRegistry.getInstance()
                .getTools("model_write", "transition", "requirements")
                .stream()
                .anyMatch(t -> toolName.equals(t.getName()));
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
        if (rawError == null || rawError.isBlank()) {
            return "\u26A0 The AI provider returned an empty response. Please try again.";
        }
        String lower = rawError.toLowerCase();

        // ---- Rate limits (most specific patterns first) ----
        if (lower.contains("requests per day") || lower.contains("rpd") || lower.contains("daily limit")) {
            return "\u23F3 LLM daily request limit reached. Your free-tier quota for today is exhausted. "
                 + "Wait until tomorrow, or switch providers in Window \u2192 Preferences \u2192 Capella Agent \u2192 LLM Providers.\n"
                 + "Details: " + truncate(rawError, 200);
        }
        if (lower.contains("requests per minute") || lower.contains("rpm")) {
            return "\u23F3 LLM rate limit: too many requests per minute. Wait ~30 seconds and try again.\n"
                 + "Details: " + truncate(rawError, 200);
        }
        if (lower.contains("tokens per minute") || lower.contains("tpm")) {
            return "\u23F3 LLM rate limit: token throughput exceeded. Wait ~30 seconds, then send a shorter message.\n"
                 + "Details: " + truncate(rawError, 200);
        }
        if (lower.contains("rate_limit") || lower.contains("rate limit") || lower.contains("429") || lower.contains("too many requests")) {
            return "\u23F3 LLM rate limit hit. Wait a moment and try again, or switch providers in Preferences.\n"
                 + "Details: " + truncate(rawError, 200);
        }

        // ---- Token / context limits ----
        if (lower.contains("context_length_exceeded") || lower.contains("context length")
                || (lower.contains("token") && (lower.contains("limit") || lower.contains("exceeded") || lower.contains("too large") || lower.contains("maximum")))) {
            return "\uD83D\uDCDD LLM token / context window exceeded. The conversation is too long for this model. "
                 + "Use the Clear History toolbar button to start fresh, ask a narrower question, "
                 + "or switch to a provider with a larger context window in Preferences.\n"
                 + "Details: " + truncate(rawError, 200);
        }

        // ---- Tool / function call validation ----
        if (lower.contains("tool") && (lower.contains("not in request") || lower.contains("validation failed") || lower.contains("not found"))) {
            return "\u26A0 The AI tried to use a tool that isn't available. This usually means it hallucinated a tool name "
                 + "(for example, asking for 'undo' or 'redo' \u2014 those are not chat tools in beta1; use Capella's "
                 + "Edit \u2192 Undo / Ctrl+Z to roll back model changes). Try rephrasing your request, or type a specific action like "
                 + "\"create a logical component named X\".\n"
                 + "Details: " + truncate(rawError, 200);
        }

        // ---- Auth ----
        if (lower.contains("unauthorized") || lower.contains("401") || lower.contains("invalid api") || lower.contains("invalid_api_key") || lower.contains("authentication")) {
            return "\uD83D\uDD11 LLM authentication failed. Your API key is missing, invalid, or expired. "
                 + "Set or update it in Window \u2192 Preferences \u2192 Capella Agent \u2192 LLM Providers.\n"
                 + "Details: " + truncate(rawError, 200);
        }
        if (lower.contains("403") || lower.contains("forbidden") || lower.contains("permission")) {
            return "\uD83D\uDD12 LLM access forbidden. Your account does not have permission for this model. "
                 + "Check your provider plan or switch models in Preferences.\n"
                 + "Details: " + truncate(rawError, 200);
        }

        // ---- Network ----
        if (lower.contains("unknownhost") || lower.contains("resolve") || lower.contains("dns")) {
            return "\uD83C\uDF10 LLM network error: cannot resolve provider hostname. Check your internet connection or DNS.\n"
                 + "Details: " + truncate(rawError, 200);
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "\u23F1 LLM request timed out. The provider took too long to respond. Try again, or switch providers in Preferences.\n"
                 + "Details: " + truncate(rawError, 200);
        }
        if (lower.contains("connect") || lower.contains("network") || lower.contains("ssl") || lower.contains("handshake")) {
            return "\uD83C\uDF10 LLM connection error. Check your internet connection, proxy, or firewall settings.\n"
                 + "Details: " + truncate(rawError, 200);
        }

        // ---- Model availability ----
        if (lower.contains("model") && (lower.contains("not found") || lower.contains("does not exist") || lower.contains("unavailable") || lower.contains("decommissioned"))) {
            return "\u26A0 The selected LLM model is not available on this provider. "
                 + "Pick a different model in Window \u2192 Preferences \u2192 Capella Agent \u2192 LLM Providers.\n"
                 + "Details: " + truncate(rawError, 200);
        }

        // ---- Server-side ----
        if (lower.contains("500") || lower.contains("502") || lower.contains("503") || lower.contains("504")
                || lower.contains("internal server") || lower.contains("bad gateway") || lower.contains("service unavailable")) {
            return "\u26A0 LLM provider server error. The provider is having issues on their end. "
                 + "Try again in a minute, or switch providers in Preferences.\n"
                 + "Details: " + truncate(rawError, 200);
        }
        if (lower.contains("400") || lower.contains("bad request") || lower.contains("invalid_request")) {
            return "\u26A0 LLM rejected the request as malformed. This may be a bug in the agent's request format. "
                 + "Try a simpler question, or check the Error Log view (Window \u2192 Show View \u2192 Error Log) for details.\n"
                 + "Details: " + truncate(rawError, 200);
        }

        // ---- Generic fallback ----
        return "\u26A0 LLM call failed. Please try again or check your settings in Window \u2192 Preferences \u2192 Capella Agent.\n"
             + "Details: " + truncate(rawError, 250);
    }

    /** Truncates a string to {@code max} characters with an ellipsis. */
    private String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "\u2026";
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

            // Tool selection: use mode-specific categories, bypass keyword filter for focused modes
            List<String> cats = agentMode.preferredToolCategories().isEmpty()
                    ? List.of("model_read", "model_write", "diagram",
                              "analysis", "export", "transition", "ai_intelligence")
                    : agentMode.preferredToolCategories();
            List<IToolDescriptor> allTools = ToolRegistry.getInstance()
                    .getTools(cats.toArray(new String[0]));
            // Bypass keyword filter for non-GENERAL modes — the category list is already focused
            List<IToolDescriptor> tools = agentMode.preferredToolCategories().isEmpty()
                    ? selectRelevantTools(userMessage, allTools)
                    : allTools;

            java.util.logging.Logger.getLogger("ChatJob").info(
                    "LLM call with " + tools.size() + "/" + allTools.size()
                    + " tools selected for query [mode=" + agentMode.displayName()
                    + "], provider: " + provider.getDisplayName());
            if (tools.isEmpty()) {
                onTextResponse.accept("[Warning: No tools registered. "
                        + "Please close and reopen the AI Model Chat view.]");
            }

            AgentConfiguration config = AgentConfiguration.getInstance();
            // System prompt comes from the active agent mode
            String systemPrompt = agentMode.systemPrompt();

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
            // A4-scrubbed in provider layer — safe to categorize for UX.
            onTextResponse.accept(formatUserFriendlyError(e.getMessage()));
            return null;
        } catch (Exception e) {
            // SECURITY (A3): uncategorized exceptions go through the filter.
            onTextResponse.accept(
                com.capellaagent.core.security.ErrorMessageFilter.safeUserMessage(e));
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
     * under the specified limit. Always preserves the first message (the
     * session anchor / first user turn) so the LLM retains original context,
     * then appends the most recent (maxMessages - 1) messages.
     *
     * @param messages all messages in the conversation
     * @param maxMessages maximum number of messages to return
     * @return windowed message list
     */
    private List<LlmMessage> slidingWindow(List<LlmMessage> messages, int maxMessages) {
        if (messages.size() <= maxMessages) {
            return messages;
        }
        // Always keep messages[0] (first-user / session-anchor), then the last (max-1) messages
        List<LlmMessage> result = new ArrayList<>(maxMessages);
        result.add(messages.get(0));
        int tail = messages.size() - (maxMessages - 1);
        result.addAll(messages.subList(tail, messages.size()));
        return result;
    }
}

package com.capellaagent.core.session;

import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.capellaagent.core.llm.ILlmProvider;
import com.capellaagent.core.llm.LlmException;
import com.capellaagent.core.llm.LlmMessage;
import com.capellaagent.core.llm.LlmRequestConfig;
import com.capellaagent.core.llm.LlmResponse;
import com.capellaagent.core.llm.LlmToolCall;
import com.capellaagent.core.security.ConsentManager;
import com.capellaagent.core.security.ToolResultSanitizer;
import com.capellaagent.core.security.WriteToolGate;
import com.capellaagent.core.tools.IToolDescriptor;
import com.capellaagent.core.tools.ToolRegistry;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonObject;

/**
 * Pure-Java controller for the LLM chat orchestration loop.
 * <p>
 * Extracted from {@code ChatJob} (in {@code modelchat.ui}) so the loop logic
 * can be unit tested without spinning up an Eclipse {@code Job} or the SWT
 * UI thread. Architect review (Issue A3) flagged {@code ChatJob} as becoming
 * a god class — this is the first step of that refactor.
 * <p>
 * The controller is intentionally framework-free:
 * <ul>
 *   <li>No dependency on Eclipse runtime, jobs, or SWT</li>
 *   <li>All side-effects flow through the {@link Listener} interface</li>
 *   <li>Cancellation is signalled by a {@link CancellationToken}, not Eclipse's
 *       {@code IProgressMonitor}</li>
 *   <li>Tools are invoked via the existing {@link ToolRegistry} singleton</li>
 * </ul>
 * <p>
 * <strong>Thread safety:</strong> A controller instance is single-use. Spawn a
 * new one per chat turn. The instance is not safe for concurrent {@link #run}
 * calls.
 */
public final class ChatSessionController {

    private static final Logger LOG = Logger.getLogger(ChatSessionController.class.getName());

    /** Default cap on tool-call iterations per turn (prevents infinite loops). */
    public static final int DEFAULT_MAX_ITERATIONS = 20;

    /**
     * Absolute hard ceiling on iterations regardless of caller config. Covers
     * E4 from the security sprint: future modes must not bypass the safety
     * cap by raising {@code maxIterations} beyond this value.
     */
    public static final int HARD_ITERATION_CEILING = 50;

    /** Default cap on write tools per turn (rate-limits destructive operations). */
    public static final int DEFAULT_MAX_WRITE_TOOLS_PER_TURN = 5;

    /** Default char limit before a tool result gets summarized for the LLM history. */
    public static final int DEFAULT_MAX_TOOL_RESULT_CHARS = 8000;

    /**
     * Hard cap on a single tool result's size before it is truncated to a
     * stub error, regardless of the summariser. Prevents a {@code list_all_*}
     * tool blowing the entire session budget in one iteration.
     */
    public static final int DEFAULT_MAX_TOOL_RESULT_BYTES = 200_000;

    /** Default per-session soft token budget (1M tokens ≈ a few dollars). */
    public static final long DEFAULT_SESSION_TOKEN_BUDGET = 1_000_000L;

    private final ConversationSession session;
    private final ILlmProvider provider;
    private final ToolRegistry toolRegistry;
    private final List<IToolDescriptor> tools;
    private final LlmRequestConfig requestConfig;
    private final Listener listener;
    private final CancellationToken cancel;

    private final int maxIterations;
    private final int maxWriteToolsPerTurn;
    private final int maxToolResultChars;
    private final int maxToolResultBytes;
    private final long sessionTokenBudget;
    private final String providerId;
    private long sessionTokensUsed;

    /** Set of tool names that perform model writes (rate-limited). */
    private final java.util.Set<String> writeToolNames;

    /** Gate controlling write-tool approval. If null, no gating (legacy behavior). */
    private final WriteToolGate writeToolGate;

    /** Set of tool names that are destructive (always prompt, no remember). */
    private final java.util.Set<String> destructiveToolNames;

    /** Consent manager for write-tool approval. If null, REQUIRES_CONSENT falls back to error. */
    private final ConsentManager consentManager;

    /** Last LLM text response, used as untrusted reasoning in the consent dialog. */
    private String lastAssistantText = "";

    /** Optional history window for token-budgeted conversation truncation. */
    private final com.capellaagent.core.llm.HistoryWindow historyWindow;

    /** Whether prompt injection defense is active (sanitize tool results). */
    private final boolean injectionDefenseEnabled;

    /** Tracks whether any tool result in this turn contained suspicious content. */
    private boolean suspiciousContextFound = false;

    /**
     * Receives lifecycle events from the controller. All callbacks are invoked
     * on the controller's caller thread; the listener is responsible for
     * marshalling to the UI thread (e.g. {@code Display.asyncExec}) when needed.
     */
    public interface Listener {
        /** A tool execution is starting. Called once per tool call. */
        void onToolExecutionStart(String toolName);

        /**
         * A tool execution finished, with the full untruncated result for UI display.
         * Called once per tool call after {@link #onToolExecutionStart}.
         */
        void onToolExecutionResult(String toolName, JsonObject fullResult);

        /** The assistant produced a text response (final or intermediate). */
        void onAssistantText(String text);

        /** A user-friendly error message intended for chat display. */
        void onError(String userFriendlyMessage);

        /** The session reached the safety rate limit (write-tools or iterations). */
        void onRateLimitTriggered(String reason);

        /** The orchestration loop terminated cleanly (no more tool calls). */
        void onComplete();
    }

    /**
     * Cooperative cancellation token. The controller polls {@link #isCancelled}
     * between iterations and after each tool call.
     */
    public interface CancellationToken {
        boolean isCancelled();

        /** A token that is never cancelled. Useful in tests. */
        CancellationToken NEVER = () -> false;
    }

    private ChatSessionController(Builder b) {
        this.session = b.session;
        this.provider = b.provider;
        this.toolRegistry = b.toolRegistry != null ? b.toolRegistry : ToolRegistry.getInstance();
        this.tools = b.tools;
        this.requestConfig = b.requestConfig;
        this.listener = b.listener;
        this.cancel = b.cancel != null ? b.cancel : CancellationToken.NEVER;
        // E4: hard ceiling clamps caller-provided maxIterations.
        this.maxIterations = Math.min(b.maxIterations, HARD_ITERATION_CEILING);
        this.maxWriteToolsPerTurn = b.maxWriteToolsPerTurn;
        this.maxToolResultChars = b.maxToolResultChars;
        this.maxToolResultBytes = b.maxToolResultBytes;
        this.sessionTokenBudget = b.sessionTokenBudget;
        this.providerId = b.providerId != null ? b.providerId : "default";
        this.writeToolNames = b.writeToolNames != null ? b.writeToolNames : java.util.Set.of();
        this.writeToolGate = b.writeToolGate;
        this.destructiveToolNames = b.destructiveToolNames != null
            ? b.destructiveToolNames : java.util.Set.of();
        this.consentManager = b.consentManager;
        this.historyWindow = b.historyWindow;
        this.injectionDefenseEnabled = b.injectionDefenseEnabled;
    }

    /** Returns a new builder. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Runs one full chat turn: append the user message, loop over LLM + tool
     * executions until the assistant produces a final text response, then
     * notify the listener of completion.
     * <p>
     * Always calls {@link Listener#onComplete()} exactly once at the end,
     * including after errors and cancellation.
     *
     * @param userMessage the user's input text
     */
    public void run(String userMessage) {
        try {
            session.addUserMessage(userMessage);

            int iterations = 0;
            int writeToolCount = 0;
            boolean done = false;

            while (!done && !cancel.isCancelled() && iterations < maxIterations) {
                iterations++;

                // Apply history window if configured — selects messages within token budget
                java.util.List<LlmMessage> contextMessages = (historyWindow != null)
                    ? historyWindow.select(session.getMessages())
                    : session.getMessages();

                // E1: per-session token budget — abort before calling LLM if
                // we're already over. Uses the most recent response usage
                // tallied into sessionTokensUsed below.
                if (sessionTokenBudget > 0 && sessionTokensUsed >= sessionTokenBudget) {
                    listener.onError(
                        "\u23F3 Session token budget exhausted ("
                        + sessionTokenBudget + " tokens). "
                        + "Start a new session to continue.");
                    return;
                }

                // E2: per-day hard cap across all providers.
                if (!com.capellaagent.core.metering.DailyTokenLimiter
                        .getInstance().canConsume(1)) {
                    listener.onError(
                        "\u23F3 Daily token cap reached. "
                        + "Resets at 00:00 UTC. Adjust in Preferences if needed.");
                    return;
                }

                // E3: circuit breaker — fast-fail if the provider has been
                // returning errors.
                try {
                    com.capellaagent.core.llm.ProviderCircuitBreaker
                        .getInstance().checkOrThrow(providerId);
                } catch (com.capellaagent.core.llm.ProviderCircuitBreaker
                        .CircuitOpenException coe) {
                    listener.onError(coe.getMessage());
                    return;
                }

                // Snapshot tracker before the call so we can compute the delta
                // attributable to this iteration (providers record into the
                // global tracker from inside their chat() call).
                long tokensBefore = com.capellaagent.core.metering
                    .TokenUsageTracker.getInstance().snapshot().totalTokens();

                LlmResponse response;
                try {
                    response = provider.chat(contextMessages, tools, requestConfig);
                    com.capellaagent.core.llm.ProviderCircuitBreaker
                        .getInstance().recordSuccess(providerId);
                } catch (LlmException e) {
                    LOG.fine("LlmException: " + e.getMessage());
                    if (isRetriable(e)) {
                        com.capellaagent.core.llm.ProviderCircuitBreaker
                            .getInstance().recordFailure(providerId);
                    }
                    listener.onError(formatError(e.getMessage()));
                    return;
                } catch (Exception e) {
                    LOG.fine("Unexpected error in chat(): " + e.getMessage());
                    com.capellaagent.core.llm.ProviderCircuitBreaker
                        .getInstance().recordFailure(providerId);
                    listener.onError(formatError(e.getMessage()));
                    return;
                }

                // Delta of the tracker's global total is this iteration's
                // cost (providers record inside chat()). Feed into session
                // budget (E1) and daily limiter (E2).
                long tokensAfter = com.capellaagent.core.metering
                    .TokenUsageTracker.getInstance().snapshot().totalTokens();
                long delta = Math.max(0, tokensAfter - tokensBefore);
                if (delta > 0) {
                    sessionTokensUsed += delta;
                    com.capellaagent.core.metering.DailyTokenLimiter
                        .getInstance().record(delta);
                }

                if (response == null) {
                    listener.onError(formatError("no response"));
                    return;
                }

                if (response.hasToolCalls()) {
                    // Persist the assistant tool-call message so the LLM does
                    // not repeat the same calls (regression: tool loop bug).
                    session.addAssistantToolCalls(response.getToolCalls());

                    for (LlmToolCall toolCall : response.getToolCalls()) {
                        if (cancel.isCancelled()) {
                            break;
                        }

                        String toolName = toolCall.getName();
                        String toolArgs = toolCall.getArguments();
                        String toolCallId = toolCall.getId();

                        // Rate limit: write tools per turn
                        if (writeToolNames.contains(toolName)) {
                            writeToolCount++;
                            if (writeToolCount > maxWriteToolsPerTurn) {
                                listener.onRateLimitTriggered(
                                    "more than " + maxWriteToolsPerTurn
                                    + " write operations in a single turn");
                                return;
                            }
                        }

                        // Tool not found in registry → friendly error to LLM,
                        // never expose hallucinated tool name to user.
                        if (!toolRegistry.hasToolTool(toolName)) {
                            session.addToolResult(toolCallId, errorJson(
                                "I wasn't able to perform that action because the "
                                + "required capability isn't available yet. "
                                + "Try rephrasing your request, or ask me what I can help with."));
                            listener.onToolExecutionStart("\u26A0 Action not available");
                            continue;
                        }

                        // Write-tool gate: check policy before executing
                        if (writeToolGate != null) {
                            WriteToolGate.Decision decision = writeToolGate.decide(
                                toolName, suspiciousContextFound);
                            if (decision == WriteToolGate.Decision.BLOCKED_READ_ONLY) {
                                session.addToolResult(toolCallId, errorJson(
                                    "This action is blocked: the workspace is in "
                                    + "read-only mode. Change to read-write in "
                                    + "Preferences if you want to modify the model."));
                                listener.onToolExecutionStart("\uD83D\uDD12 Blocked (read-only)");
                                continue;
                            }
                            if (decision == WriteToolGate.Decision.BLOCKED_ADMIN) {
                                session.addToolResult(toolCallId, errorJson(
                                    "This action is administratively disabled for "
                                    + "this site. Contact your administrator."));
                                listener.onToolExecutionStart("\uD83D\uDD12 Blocked");
                                continue;
                            }
                            if (decision == WriteToolGate.Decision.REQUIRES_CONSENT) {
                                // F1/F2: ask the user via the native consent
                                // dialog. If no manager is registered, fall
                                // back to the old "error fed to LLM" path so
                                // headless tests still work.
                                if (consentManager == null) {
                                    session.addToolResult(toolCallId, errorJson(
                                        "This action requires user confirmation before proceeding. "
                                        + "The AI proposed: " + toolName + ". "
                                        + "To allow write operations in this context, rephrase your request "
                                        + "and confirm explicitly (e.g. 'Yes, please create the element')."));
                                    listener.onToolExecutionStart("\u26A0 " + toolName
                                        + " requires confirmation \u2014 blocked");
                                    continue;
                                }
                                boolean destructive = destructiveToolNames.contains(toolName);
                                listener.onToolExecutionStart("\u23F8 Awaiting user approval: " + toolName);
                                ConsentManager.Decision cd = consentManager.requestConsent(
                                    toolName,
                                    destructive ? "DESTRUCTIVE" : "MODEL_WRITE",
                                    toolArgs != null ? toolArgs : "{}",
                                    lastAssistantText,
                                    destructive);
                                if (cd == ConsentManager.Decision.DENIED) {
                                    session.addToolResult(toolCallId, errorJson(
                                        "User denied the action. Do not retry this tool. "
                                        + "Explain why you were going to run it and wait "
                                        + "for further instructions."));
                                    listener.onToolExecutionStart("\u274C " + toolName
                                        + " denied by user");
                                    continue;
                                }
                                // APPROVED / APPROVED_REMEMBER: fall through
                                // to actual tool execution below.
                            }
                        }

                        listener.onToolExecutionStart(toolName);

                        ToolResult toolResult;
                        try {
                            toolResult = toolRegistry.executeTool(toolName, toolArgs);
                        } catch (Exception e) {
                            toolResult = ToolResult.error(
                                "Tool execution failed: " + e.getMessage());
                        }

                        JsonObject resultJson = toolResult.toJson();

                        // Check the RAW result for injection patterns BEFORE sanitizing
                        if (injectionDefenseEnabled
                                && ToolResultSanitizer.containsSuspiciousContent(resultJson)) {
                            suspiciousContextFound = true;
                            LOG.warning("Tool result from " + toolName
                                + " contains suspicious content; "
                                + "subsequent write tools will require consent");
                        }

                        // Full (unsanitized) result → UI so the user sees exactly
                        // what was returned. UI renderer escapes HTML separately.
                        listener.onToolExecutionResult(toolName, resultJson);

                        // Sanitized result → LLM history: wraps every string in
                        // <untrusted>...</untrusted> so injection in model
                        // descriptions cannot be interpreted as instructions.
                        JsonObject forLlm = injectionDefenseEnabled
                            ? ToolResultSanitizer.sanitize(resultJson)
                            : resultJson;

                        // Compact summary → LLM history (saves tokens) when large
                        String resultStr = forLlm.toString();
                        // E1 hardening: hard byte cap. Huge tool results (e.g.
                        // list_all_requirements on 5000 reqs) would blow the
                        // session budget in one call. Replace with a stub.
                        if (resultStr.length() > maxToolResultBytes) {
                            JsonObject stub = new JsonObject();
                            stub.addProperty("error", true);
                            stub.addProperty("truncated", true);
                            stub.addProperty("original_bytes", resultStr.length());
                            stub.addProperty("message",
                                "Tool result exceeded " + maxToolResultBytes
                                + " bytes and was truncated. Narrow the query "
                                + "(filter by layer, add a limit, or request a summary).");
                            session.addToolResult(toolCallId, stub);
                            continue;
                        }
                        if (resultStr.length() > maxToolResultChars) {
                            session.addToolResult(toolCallId,
                                createCompactSummary(toolName, forLlm));
                        } else {
                            session.addToolResult(toolCallId, forLlm);
                        }
                    }
                }

                if (response.hasTextContent()) {
                    String text = response.getTextContent();
                    lastAssistantText = text;
                    listener.onAssistantText(text);
                    session.addAssistantMessage(text);
                }

                if (!response.hasToolCalls()) {
                    done = true;
                }
            }

            if (cancel.isCancelled()) {
                listener.onAssistantText("[Chat cancelled by user]");
            } else if (iterations >= maxIterations) {
                listener.onAssistantText("[Reached maximum tool iteration limit ("
                    + maxIterations + "). Stopping.]");
            }
        } finally {
            listener.onComplete();
        }
    }

    /**
     * Builds a compact JSON summary of a large tool result so the LLM history
     * stays under token limits while the user still sees the full result via
     * the UI listener callback.
     * <p>
     * Public for testing — exposes the truncation contract so it can be
     * verified without spinning up the full controller loop.
     */
    public JsonObject createCompactSummary(String toolName, JsonObject fullResult) {
        JsonObject summary = new JsonObject();
        summary.addProperty("summarized", true);
        summary.addProperty("tool", toolName);

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
            summary.addProperty("elements_count",
                fullResult.getAsJsonArray("elements").size());
        }
        if (fullResult.has("status")) {
            summary.addProperty("status", fullResult.get("status").getAsString());
        }
        if (fullResult.has("message")) {
            String msg = fullResult.get("message").getAsString();
            summary.addProperty("message",
                msg.length() > 200 ? msg.substring(0, 200) + "..." : msg);
        }

        summary.addProperty("note",
            "Full results already displayed to the user as an interactive table. "
            + "Do NOT repeat or list individual elements. Just briefly confirm "
            + "what was found (e.g. 'Found 103 Physical Functions.') and ask if "
            + "they need anything else.");
        return summary;
    }

    /**
     * Classifies an LlmException as retriable for circuit-breaker purposes.
     * Auth failures and invalid requests do NOT count toward the breaker —
     * those are caller mistakes, not provider outages.
     */
    private static boolean isRetriable(LlmException e) {
        String code = e.getErrorCode();
        if (code == null) return true;
        return LlmException.ERR_RATE_LIMITED.equals(code)
            || LlmException.ERR_CONNECTION.equals(code)
            || LlmException.ERR_UNKNOWN.equals(code);
    }

    private JsonObject errorJson(String message) {
        JsonObject e = new JsonObject();
        e.addProperty("error", true);
        e.addProperty("message", message);
        return e;
    }

    /**
     * Maps a raw error string from the LLM provider or network layer to a
     * user-friendly message. Never exposes HTTP status codes, JSON bodies, or
     * internal details.
     * <p>
     * Public for testing — the no-leak invariant is part of the controller's
     * external contract and is asserted by ChatSessionControllerTest.
     */
    public static String formatError(String rawError) {
        if (rawError == null) rawError = "";
        String lower = rawError.toLowerCase();

        if (lower.contains("rate_limit") || lower.contains("rate limit")
                || lower.contains("429") || lower.contains("too many")) {
            return "\u23F3 The AI service is temporarily busy. Please wait a moment and try again.";
        }
        if (lower.contains("token") && (lower.contains("limit")
                || lower.contains("exceeded") || lower.contains("too large"))) {
            return "\uD83D\uDCDD The request was too large for the AI provider's limits. "
                + "Try a simpler question or switch to a provider with higher limits in Preferences.";
        }
        if (lower.contains("tool") && (lower.contains("not in request")
                || lower.contains("validation failed") || lower.contains("not found"))) {
            return "\u26A0 I wasn't able to perform that action. "
                + "Try rephrasing your request, or ask me what I can help with.";
        }
        if (lower.contains("unauthorized") || lower.contains("401")
                || lower.contains("invalid api") || lower.contains("authentication")) {
            return "\uD83D\uDD11 Could not authenticate with the AI provider. "
                + "Please check your API key in Window \u2192 Preferences \u2192 Capella Agent.";
        }
        if (lower.contains("connect") || lower.contains("timeout")
                || lower.contains("network") || lower.contains("resolve")) {
            return "\uD83C\uDF10 Could not reach the AI provider. "
                + "Please check your internet connection and try again.";
        }
        if (lower.contains("model") && lower.contains("not found")) {
            return "\u26A0 The selected AI model is not available. "
                + "Please check the model name in Preferences.";
        }
        return "\u26A0 Something went wrong while processing your request. "
            + "Please try again or check your settings in Preferences.";
    }

    /** Builder for {@link ChatSessionController}. */
    public static final class Builder {
        private ConversationSession session;
        private ILlmProvider provider;
        private ToolRegistry toolRegistry;
        private List<IToolDescriptor> tools;
        private LlmRequestConfig requestConfig;
        private Listener listener;
        private CancellationToken cancel;
        private int maxIterations = DEFAULT_MAX_ITERATIONS;
        private int maxWriteToolsPerTurn = DEFAULT_MAX_WRITE_TOOLS_PER_TURN;
        private int maxToolResultChars = DEFAULT_MAX_TOOL_RESULT_CHARS;
        private int maxToolResultBytes = DEFAULT_MAX_TOOL_RESULT_BYTES;
        private long sessionTokenBudget = DEFAULT_SESSION_TOKEN_BUDGET;
        private String providerId;
        private java.util.Set<String> writeToolNames;
        private WriteToolGate writeToolGate;
        private java.util.Set<String> destructiveToolNames;
        private ConsentManager consentManager;
        private com.capellaagent.core.llm.HistoryWindow historyWindow = null;
        private boolean injectionDefenseEnabled = true;

        public Builder session(ConversationSession session) {
            this.session = session;
            return this;
        }

        public Builder provider(ILlmProvider provider) {
            this.provider = provider;
            return this;
        }

        public Builder toolRegistry(ToolRegistry toolRegistry) {
            this.toolRegistry = toolRegistry;
            return this;
        }

        public Builder tools(List<IToolDescriptor> tools) {
            this.tools = tools;
            return this;
        }

        public Builder requestConfig(LlmRequestConfig requestConfig) {
            this.requestConfig = requestConfig;
            return this;
        }

        public Builder listener(Listener listener) {
            this.listener = listener;
            return this;
        }

        public Builder cancellationToken(CancellationToken cancel) {
            this.cancel = cancel;
            return this;
        }

        public Builder maxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
            return this;
        }

        public Builder maxWriteToolsPerTurn(int n) {
            this.maxWriteToolsPerTurn = n;
            return this;
        }

        public Builder maxToolResultChars(int n) {
            this.maxToolResultChars = n;
            return this;
        }

        /** Hard byte cap on a single tool result (security sprint E1). */
        public Builder maxToolResultBytes(int n) {
            this.maxToolResultBytes = n;
            return this;
        }

        /** Per-session soft token budget (security sprint E1). */
        public Builder sessionTokenBudget(long tokens) {
            this.sessionTokenBudget = tokens;
            return this;
        }

        /** Provider id for circuit-breaker scoping (security sprint E3). */
        public Builder providerId(String id) {
            this.providerId = id;
            return this;
        }

        public Builder writeToolNames(java.util.Set<String> names) {
            this.writeToolNames = names;
            return this;
        }

        /**
         * Installs a {@link WriteToolGate} to enforce access-control decisions
         * (read-only, destructive consent, admin block). If null (default),
         * all write tools are allowed.
         */
        public Builder writeToolGate(WriteToolGate gate) {
            this.writeToolGate = gate;
            return this;
        }

        /** Names of destructive tools (security sprint F3). */
        public Builder destructiveToolNames(java.util.Set<String> names) {
            this.destructiveToolNames = names;
            return this;
        }

        /** Consent manager invoked for REQUIRES_CONSENT writes (security sprint F2). */
        public Builder consentManager(ConsentManager cm) {
            this.consentManager = cm;
            return this;
        }

        public Builder historyWindow(com.capellaagent.core.llm.HistoryWindow window) {
            this.historyWindow = window;
            return this;
        }

        /**
         * Enables or disables prompt-injection defense. Default: true.
         * When enabled, tool results are sanitized with
         * {@link ToolResultSanitizer} before being added to the LLM history
         * and the {@code suspiciousContextFound} flag is tracked for the
         * write-tool gate.
         */
        public Builder injectionDefenseEnabled(boolean enabled) {
            this.injectionDefenseEnabled = enabled;
            return this;
        }

        public ChatSessionController build() {
            java.util.Objects.requireNonNull(session, "session");
            java.util.Objects.requireNonNull(provider, "provider");
            java.util.Objects.requireNonNull(tools, "tools");
            java.util.Objects.requireNonNull(requestConfig, "requestConfig");
            java.util.Objects.requireNonNull(listener, "listener");
            return new ChatSessionController(this);
        }
    }
}

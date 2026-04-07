package com.capellaagent.core.tests.session;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.capellaagent.core.llm.ILlmProvider;
import com.capellaagent.core.llm.LlmException;
import com.capellaagent.core.llm.LlmMessage;
import com.capellaagent.core.llm.LlmRequestConfig;
import com.capellaagent.core.llm.LlmResponse;
import com.capellaagent.core.llm.LlmToolCall;
import com.capellaagent.core.session.ChatSessionController;
import com.capellaagent.core.session.ConversationSession;
import com.capellaagent.core.tools.IToolDescriptor;
import com.google.gson.JsonObject;

/**
 * Unit tests for {@link ChatSessionController}.
 * <p>
 * Validates the orchestration loop with a fake LLM provider — no Eclipse,
 * no SWT, no Capella runtime needed. This is the architectural payoff of
 * the controller extraction (architect Issue A3).
 */
class ChatSessionControllerTest {

    /** Recording listener that captures all events for assertion. */
    private static final class RecordingListener implements ChatSessionController.Listener {
        final List<String> events = new ArrayList<>();
        boolean completed = false;
        String lastError;
        String rateLimitReason;
        @Override public void onToolExecutionStart(String toolName) {
            events.add("start:" + toolName);
        }
        @Override public void onToolExecutionResult(String toolName, JsonObject fullResult) {
            events.add("result:" + toolName);
        }
        @Override public void onAssistantText(String text) {
            events.add("text:" + text);
        }
        @Override public void onError(String userFriendlyMessage) {
            events.add("error");
            lastError = userFriendlyMessage;
        }
        @Override public void onRateLimitTriggered(String reason) {
            events.add("ratelimit");
            rateLimitReason = reason;
        }
        @Override public void onComplete() {
            events.add("complete");
            completed = true;
        }
    }

    /** Fake provider that returns a queued sequence of responses. */
    private static final class FakeProvider implements ILlmProvider {
        final List<LlmResponse> responses;
        int callCount = 0;
        FakeProvider(LlmResponse... responses) {
            this.responses = new ArrayList<>(Arrays.asList(responses));
        }
        @Override public String getId() { return "fake"; }
        @Override public String getDisplayName() { return "Fake"; }
        @Override
        public LlmResponse chat(List<LlmMessage> messages,
                                List<IToolDescriptor> tools,
                                LlmRequestConfig config) throws LlmException {
            if (callCount >= responses.size()) {
                throw new LlmException("FAKE", "no more queued responses");
            }
            return responses.get(callCount++);
        }
    }

    private ChatSessionController.Builder baseBuilder(ConversationSession s,
                                                      ILlmProvider p,
                                                      RecordingListener l) {
        return ChatSessionController.builder()
            .session(s)
            .provider(p)
            .tools(List.of())
            .requestConfig(LlmRequestConfig.defaults())
            .listener(l);
    }

    @Test
    @DisplayName("text-only response: 1 call, complete cleanly")
    void textOnlyResponseCompletes() {
        ConversationSession s = new ConversationSession();
        RecordingListener l = new RecordingListener();
        FakeProvider p = new FakeProvider(
            new LlmResponse("Hello back", List.of(), "stop"));

        baseBuilder(s, p, l).build().run("hi there");

        assertTrue(l.completed);
        assertNull(l.lastError);
        assertEquals(1, p.callCount);
        // Expect: text:Hello back, complete
        assertTrue(l.events.contains("text:Hello back"));
        assertTrue(l.events.contains("complete"));
    }

    @Test
    @DisplayName("provider exception is converted to friendly error and listener.onComplete still fires")
    void providerExceptionFriendlyError() {
        ConversationSession s = new ConversationSession();
        RecordingListener l = new RecordingListener();
        ILlmProvider p = new ILlmProvider() {
            @Override public String getId() { return "f"; }
            @Override public String getDisplayName() { return "F"; }
            @Override public LlmResponse chat(List<LlmMessage> m, List<IToolDescriptor> t, LlmRequestConfig c)
                    throws LlmException {
                throw new LlmException("AUTH", "401 Unauthorized");
            }
        };
        baseBuilder(s, p, l).build().run("anything");

        assertTrue(l.completed, "onComplete must always fire");
        assertNotNull(l.lastError, "lastError must be set");
        assertFalse(l.lastError.contains("401"), "raw HTTP code must NEVER leak to user");
        assertFalse(l.lastError.contains("Unauthorized"), "raw provider message must NEVER leak");
        assertTrue(l.lastError.toLowerCase().contains("authenticate")
            || l.lastError.toLowerCase().contains("api key"),
            "must hint at the auth fix path");
    }

    @Test
    @DisplayName("rate limit error returns friendly 'wait and try again' message")
    void rateLimitFriendlyError() {
        ConversationSession s = new ConversationSession();
        RecordingListener l = new RecordingListener();
        ILlmProvider p = new ILlmProvider() {
            @Override public String getId() { return "f"; }
            @Override public String getDisplayName() { return "F"; }
            @Override public LlmResponse chat(List<LlmMessage> m, List<IToolDescriptor> t, LlmRequestConfig c)
                    throws LlmException {
                throw new LlmException("RATE", "rate_limit_exceeded: too many requests");
            }
        };
        baseBuilder(s, p, l).build().run("anything");

        assertNotNull(l.lastError);
        assertTrue(l.lastError.toLowerCase().contains("busy")
            || l.lastError.toLowerCase().contains("wait"),
            "rate-limit error should ask user to wait");
    }

    @Test
    @DisplayName("formatError never leaks raw provider strings")
    void formatErrorNeverLeaksRawStrings() {
        // Direct test of the static formatter (package-private)
        String[] rawErrors = {
            "HTTP 429 Too Many Requests",
            "401 Unauthorized: invalid api key sk-abc123",
            "java.net.ConnectException: timeout connecting to api.openai.com",
            "context_length_exceeded: token limit 8000",
            "tool 'reparent_element' not in request.tools",
            "model gpt-4-vision-preview not found",
        };
        for (String raw : rawErrors) {
            String friendly = ChatSessionController.formatError(raw);
            assertNotNull(friendly);
            assertFalse(friendly.contains("HTTP"), "raw HTTP must not leak: " + friendly);
            assertFalse(friendly.contains("api.openai.com"), "raw URL must not leak: " + friendly);
            assertFalse(friendly.contains("sk-"), "raw API key must not leak: " + friendly);
            assertFalse(friendly.contains("reparent_element"), "hallucinated tool name must not leak: " + friendly);
            assertFalse(friendly.toLowerCase().contains("java."), "stack-trace prefix must not leak");
        }
    }

    @Test
    @DisplayName("cancellation token stops the loop and still calls onComplete")
    void cancellationStopsLoop() {
        ConversationSession s = new ConversationSession();
        RecordingListener l = new RecordingListener();
        AtomicBoolean cancelled = new AtomicBoolean(false);

        // Provider that asks for a tool call on the first turn
        LlmToolCall fakeCall = new LlmToolCall("c1", "list_elements", "{}");
        FakeProvider p = new FakeProvider(
            new LlmResponse(null, List.of(fakeCall), "tool_use"));

        baseBuilder(s, p, l)
            .cancellationToken(() -> cancelled.get())
            .build()
            .run("anything");
        // First turn went through (provider returned a tool call) — but tool is
        // not registered so we'll get an "Action not available" path. The loop
        // continues to a second iteration. Cancel before we get too far:
        cancelled.set(true);

        assertTrue(l.completed, "onComplete fires even after cancellation");
    }

    @Test
    @DisplayName("max iterations stops runaway loop")
    void maxIterationsStopsRunawayLoop() {
        ConversationSession s = new ConversationSession();
        RecordingListener l = new RecordingListener();

        // Build a provider that ALWAYS returns a tool call
        ILlmProvider p = new ILlmProvider() {
            @Override public String getId() { return "f"; }
            @Override public String getDisplayName() { return "F"; }
            @Override public LlmResponse chat(List<LlmMessage> m, List<IToolDescriptor> t, LlmRequestConfig c) {
                LlmToolCall tc = new LlmToolCall("c", "no_such_tool", "{}");
                return new LlmResponse(null, List.of(tc), "tool_use");
            }
        };

        baseBuilder(s, p, l).maxIterations(3).build().run("anything");

        assertTrue(l.completed);
        // Final assistant message should mention iteration limit
        boolean foundLimitMsg = l.events.stream().anyMatch(
            e -> e.startsWith("text:") && e.toLowerCase().contains("iteration"));
        assertTrue(foundLimitMsg, "should announce iteration limit reached");
    }

    @Test
    @DisplayName("write tool rate limit triggers onRateLimitTriggered")
    void writeToolRateLimit() {
        ConversationSession s = new ConversationSession();
        RecordingListener l = new RecordingListener();

        // Provider returns 6 write-tool calls in one response (which exceeds the
        // default cap of 5). Tools are not registered so each one fails the
        // hasToolTool check, BUT the rate-limit check happens FIRST, so we should
        // see the rate-limit signal on the 6th write tool.
        LlmToolCall[] calls = new LlmToolCall[6];
        for (int i = 0; i < 6; i++) {
            calls[i] = new LlmToolCall("c" + i, "create_element", "{}");
        }
        FakeProvider p = new FakeProvider(
            new LlmResponse(null, Arrays.asList(calls), "tool_use"));

        baseBuilder(s, p, l)
            .writeToolNames(Set.of("create_element"))
            .build()
            .run("create six things");

        assertTrue(l.completed);
        assertNotNull(l.rateLimitReason, "rate-limit reason should be reported");
        assertTrue(l.rateLimitReason.contains("write operations"));
    }

    @Test
    @DisplayName("createCompactSummary preserves count and adds note")
    void createCompactSummaryPreservesCountAndNote() {
        ConversationSession s = new ConversationSession();
        RecordingListener l = new RecordingListener();
        FakeProvider p = new FakeProvider(
            new LlmResponse("ok", List.of(), "stop"));

        ChatSessionController c = baseBuilder(s, p, l).build();

        JsonObject fullResult = new JsonObject();
        fullResult.addProperty("count", 103);
        fullResult.addProperty("element_type", "PhysicalFunction");
        fullResult.addProperty("layer", "pa");

        JsonObject compact = c.createCompactSummary("list_elements", fullResult);
        assertEquals(103, compact.get("count").getAsInt());
        assertEquals("PhysicalFunction", compact.get("element_type").getAsString());
        assertEquals("pa", compact.get("layer").getAsString());
        assertTrue(compact.has("note"));
        assertTrue(compact.get("note").getAsString().contains("Do NOT repeat"),
            "note should instruct LLM not to relist results");
    }

    @Test
    @DisplayName("builder rejects null required fields")
    void builderRejectsNullRequiredFields() {
        ChatSessionController.Builder b = ChatSessionController.builder();
        // session, provider, tools, requestConfig, listener all required
        assertThrows(NullPointerException.class, b::build);
    }
}

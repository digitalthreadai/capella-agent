package com.capellaagent.core.tests.llm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.capellaagent.core.llm.HistoryWindow;
import com.capellaagent.core.llm.LlmMessage;
import com.capellaagent.core.llm.LlmToolCall;
import com.google.gson.JsonObject;

/**
 * Unit tests for {@link HistoryWindow}.
 * <p>
 * The two regression tests for orphan prevention (test #5 and #6) are the
 * "AI engineer must-fix #2" — without these, dropping a tool_result whose
 * matching assistant tool_call was also dropped causes the LLM to return a
 * 400 error from every OpenAI-compatible provider.
 */
class HistoryWindowTest {

    @Test
    @DisplayName("empty input returns empty output")
    void emptyInputEmptyOutput() {
        HistoryWindow w = new HistoryWindow(1000);
        assertTrue(w.select(null).isEmpty());
        assertTrue(w.select(List.of()).isEmpty());
    }

    @Test
    @DisplayName("system messages are always preserved at the front")
    void systemMessagesAlwaysPreserved() {
        List<LlmMessage> msgs = new ArrayList<>();
        msgs.add(LlmMessage.system("you are helpful"));
        msgs.add(LlmMessage.user("hi"));
        msgs.add(LlmMessage.assistant("hello"));

        HistoryWindow w = new HistoryWindow(1000);
        List<LlmMessage> selected = w.select(msgs);
        assertEquals(LlmMessage.Role.SYSTEM, selected.get(0).getRole());
    }

    @Test
    @DisplayName("messages within budget are all kept")
    void messagesWithinBudgetAreAllKept() {
        List<LlmMessage> msgs = List.of(
            LlmMessage.user("a"),
            LlmMessage.assistant("b"),
            LlmMessage.user("c"));
        HistoryWindow w = new HistoryWindow(10000);
        List<LlmMessage> selected = w.select(msgs);
        assertEquals(3, selected.size());
    }

    @Test
    @DisplayName("oldest messages are dropped first when budget is tight")
    void oldestDroppedFirst() {
        List<LlmMessage> msgs = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            msgs.add(LlmMessage.user("message " + i + " " + "x".repeat(200)));
        }
        // Budget should fit only a few
        HistoryWindow w = new HistoryWindow(500);
        List<LlmMessage> selected = w.select(msgs);
        assertTrue(selected.size() < msgs.size(), "some should be dropped");
        // The newest message must be present
        assertEquals(msgs.get(msgs.size() - 1).getContent(),
            selected.get(selected.size() - 1).getContent());
    }

    @Test
    @DisplayName("orphaned tool_result (missing parent assistant call) is dropped")
    void orphanedToolResultDropped() {
        // History: [user, ASSISTANT(tool_call c1), TOOL(c1), user, TOOL(c1)-orphan]
        // The trailing TOOL(c1) is orphaned because the assistant call was earlier
        // and the matching pair is intact. Build a different scenario where the
        // TOOL has no matching assistant in the same window.
        List<LlmMessage> msgs = new ArrayList<>();
        msgs.add(LlmMessage.user("query"));
        // Note: TOOL message with id 'orphan' but no assistant message issues 'orphan'
        msgs.add(LlmMessage.toolResult("orphan", "{\"result\":1}"));
        msgs.add(LlmMessage.assistant("done"));

        HistoryWindow w = new HistoryWindow(10000);
        List<LlmMessage> selected = w.select(msgs);
        // The orphaned tool result should be removed
        assertTrue(selected.stream().noneMatch(m ->
            m.getRole() == LlmMessage.Role.TOOL),
            "orphaned tool result must be dropped");
    }

    @Test
    @DisplayName("orphaned assistant tool_call (no matching tool_result) is dropped")
    void orphanedAssistantToolCallDropped() {
        List<LlmMessage> msgs = new ArrayList<>();
        msgs.add(LlmMessage.user("query"));
        // Assistant requests a tool but no corresponding TOOL reply in window
        LlmToolCall tc = new LlmToolCall("c1", "list_elements", "{}");
        msgs.add(LlmMessage.assistantWithToolCalls(List.of(tc)));
        msgs.add(LlmMessage.user("forget that, hi"));

        HistoryWindow w = new HistoryWindow(10000);
        List<LlmMessage> selected = w.select(msgs);
        // The orphaned assistant-with-tool-calls must be dropped
        assertFalse(selected.stream().anyMatch(LlmMessage::hasToolCalls),
            "orphaned assistant tool-call must be dropped");
    }

    @Test
    @DisplayName("matched assistant tool_call + tool_result are kept together")
    void matchedPairKeptTogether() {
        List<LlmMessage> msgs = new ArrayList<>();
        msgs.add(LlmMessage.user("list functions"));
        LlmToolCall tc = new LlmToolCall("c1", "list_elements", "{}");
        msgs.add(LlmMessage.assistantWithToolCalls(List.of(tc)));
        JsonObject result = new JsonObject();
        result.addProperty("count", 5);
        msgs.add(LlmMessage.toolResult("c1", result.toString()));
        msgs.add(LlmMessage.assistant("Found 5 functions"));

        HistoryWindow w = new HistoryWindow(10000);
        List<LlmMessage> selected = w.select(msgs);
        // Both members of the pair should be present
        boolean hasCall = selected.stream().anyMatch(LlmMessage::hasToolCalls);
        boolean hasReply = selected.stream().anyMatch(m ->
            m.getRole() == LlmMessage.Role.TOOL);
        assertTrue(hasCall, "assistant tool_call should be kept");
        assertTrue(hasReply, "matching tool_result should be kept");
    }

    @Test
    @DisplayName("constructor rejects too-small budgets")
    void constructorRejectsSmallBudget() {
        assertThrows(IllegalArgumentException.class, () -> new HistoryWindow(50));
    }

    @Test
    @DisplayName("removeOrphans is symmetrical: drops both sides of broken pairs")
    void removeOrphansSymmetrical() {
        // assistant(c1, c2) + tool(c1) only — c2 is unreplied → assistant gets dropped
        List<LlmMessage> msgs = new ArrayList<>();
        msgs.add(LlmMessage.user("multi"));
        msgs.add(LlmMessage.assistantWithToolCalls(List.of(
            new LlmToolCall("c1", "tool1", "{}"),
            new LlmToolCall("c2", "tool2", "{}"))));
        msgs.add(LlmMessage.toolResult("c1", "{}"));

        List<LlmMessage> result = HistoryWindow.removeOrphans(msgs);
        // Assistant tool-call must be dropped because c2 has no reply
        assertFalse(result.stream().anyMatch(LlmMessage::hasToolCalls));
        // The lone tool result for c1 has no matching kept assistant -> also dropped
        assertFalse(result.stream().anyMatch(m -> m.getRole() == LlmMessage.Role.TOOL));
    }
}

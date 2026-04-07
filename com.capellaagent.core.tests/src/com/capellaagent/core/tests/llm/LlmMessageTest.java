package com.capellaagent.core.tests.llm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.capellaagent.core.llm.LlmMessage;
import com.capellaagent.core.llm.LlmToolCall;

/**
 * Unit tests for {@link LlmMessage}.
 * <p>
 * These tests validate the factory methods and the tool-call handling that
 * was added to fix the tool execution loop bug (commit 0fb2a67).
 */
class LlmMessageTest {

    @Test
    @DisplayName("system() factory creates SYSTEM role message")
    void systemFactoryCreatesSystemRole() {
        LlmMessage msg = LlmMessage.system("you are helpful");
        assertEquals(LlmMessage.Role.SYSTEM, msg.getRole());
        assertEquals("you are helpful", msg.getContent());
        assertFalse(msg.hasToolCalls());
        assertTrue(msg.getToolCalls().isEmpty());
    }

    @Test
    @DisplayName("user() factory creates USER role message")
    void userFactoryCreatesUserRole() {
        LlmMessage msg = LlmMessage.user("list functions");
        assertEquals(LlmMessage.Role.USER, msg.getRole());
        assertEquals("list functions", msg.getContent());
    }

    @Test
    @DisplayName("assistant() factory creates ASSISTANT role message")
    void assistantFactoryCreatesAssistantRole() {
        LlmMessage msg = LlmMessage.assistant("here you go");
        assertEquals(LlmMessage.Role.ASSISTANT, msg.getRole());
        assertEquals("here you go", msg.getContent());
        assertFalse(msg.hasToolCalls());
    }

    @Test
    @DisplayName("toolResult() carries toolCallId")
    void toolResultCarriesToolCallId() {
        LlmMessage msg = LlmMessage.toolResult("call_1", "{\"ok\":true}");
        assertEquals(LlmMessage.Role.TOOL, msg.getRole());
        assertEquals("{\"ok\":true}", msg.getContent());
        assertTrue(msg.getToolCallId().isPresent());
        assertEquals("call_1", msg.getToolCallId().get());
    }

    @Test
    @DisplayName("assistantWithToolCalls() persists tool calls (regression: tool loop fix)")
    void assistantWithToolCallsPersistsToolCalls() {
        LlmToolCall tc = new LlmToolCall("call_42", "list_elements", "{\"layer\":\"pa\"}");
        LlmMessage msg = LlmMessage.assistantWithToolCalls(List.of(tc));

        assertEquals(LlmMessage.Role.ASSISTANT, msg.getRole());
        assertTrue(msg.hasToolCalls(), "must report hasToolCalls()=true");
        assertEquals(1, msg.getToolCalls().size());
        assertEquals("call_42", msg.getToolCalls().get(0).getId());
        assertEquals("list_elements", msg.getToolCalls().get(0).getName());
    }

    @Test
    @DisplayName("assistantWithToolCalls() returns immutable list")
    void assistantWithToolCallsReturnsImmutableList() {
        LlmToolCall tc1 = new LlmToolCall("c1", "n", "{}");
        LlmMessage msg = LlmMessage.assistantWithToolCalls(List.of(tc1));
        // Attempting to mutate the returned list must throw
        assertThrows(UnsupportedOperationException.class,
            () -> msg.getToolCalls().add(new LlmToolCall("c2", "n2", "{}")));
    }

    @Test
    @DisplayName("hasToolCalls() returns false for empty list")
    void hasToolCallsFalseForEmptyList() {
        LlmMessage msg = LlmMessage.assistantWithToolCalls(List.of());
        assertFalse(msg.hasToolCalls());
    }

    @Test
    @DisplayName("equals/hashCode treat tool calls as part of identity")
    void equalsHashCodeTreatsToolCalls() {
        LlmToolCall tc = new LlmToolCall("c", "n", "{}");
        LlmMessage a = LlmMessage.assistantWithToolCalls(List.of(tc));
        LlmMessage b = LlmMessage.assistantWithToolCalls(List.of(tc));
        LlmMessage plain = LlmMessage.assistant("");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, plain, "tool-call message must not equal plain assistant message");
    }

    @Test
    @DisplayName("constructor rejects null role")
    void constructorRejectsNullRole() {
        assertThrows(NullPointerException.class,
            () -> new LlmMessage(null, "x", null, null));
    }

    @Test
    @DisplayName("constructor rejects null content")
    void constructorRejectsNullContent() {
        assertThrows(NullPointerException.class,
            () -> new LlmMessage(LlmMessage.Role.USER, null, null, null));
    }

    @Test
    @DisplayName("toString truncates long content for log readability")
    void toStringTruncatesLongContent() {
        String longContent = "a".repeat(200);
        LlmMessage msg = LlmMessage.user(longContent);
        String s = msg.toString();
        assertTrue(s.contains("..."), "long content should be truncated in toString()");
        assertTrue(s.length() < longContent.length() + 100);
    }
}

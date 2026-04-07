package com.capellaagent.core.tests.session;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.capellaagent.core.llm.LlmMessage;
import com.capellaagent.core.llm.LlmToolCall;
import com.capellaagent.core.session.ConversationSession;
import com.google.gson.JsonObject;

/**
 * Unit tests for {@link ConversationSession}.
 * <p>
 * Covers the message-history lifecycle, the assistant-tool-call persistence
 * fix (regression test for the tool loop bug), and the schema versioning
 * field added in Day 1.
 */
class ConversationSessionTest {

    @Test
    @DisplayName("new session has unique ID and current schema version")
    void newSessionHasIdAndSchemaVersion() {
        ConversationSession s1 = new ConversationSession();
        ConversationSession s2 = new ConversationSession();
        assertNotNull(s1.getSessionId());
        assertNotEquals(s1.getSessionId(), s2.getSessionId());
        assertEquals(ConversationSession.SCHEMA_VERSION, s1.getSchemaVersion());
        assertEquals(1, s1.getSchemaVersion(), "v1.x must report schema version 1");
    }

    @Test
    @DisplayName("addUserMessage / addAssistantMessage append to history in order")
    void addMessagesAppendsInOrder() {
        ConversationSession s = new ConversationSession();
        s.addUserMessage("hello");
        s.addAssistantMessage("hi back");
        List<LlmMessage> msgs = s.getMessages();
        assertEquals(2, msgs.size());
        assertEquals(LlmMessage.Role.USER, msgs.get(0).getRole());
        assertEquals("hello", msgs.get(0).getContent());
        assertEquals(LlmMessage.Role.ASSISTANT, msgs.get(1).getRole());
    }

    @Test
    @DisplayName("getMessages() returns immutable snapshot")
    void getMessagesReturnsImmutableSnapshot() {
        ConversationSession s = new ConversationSession();
        s.addUserMessage("x");
        List<LlmMessage> snapshot = s.getMessages();
        assertThrows(UnsupportedOperationException.class,
            () -> snapshot.add(LlmMessage.user("y")));
    }

    @Test
    @DisplayName("addAssistantToolCalls creates assistant message with tool_calls (tool loop fix)")
    void addAssistantToolCallsCreatesProperMessage() {
        ConversationSession s = new ConversationSession();
        s.addUserMessage("list pa functions");

        LlmToolCall call = new LlmToolCall("call_1", "list_elements", "{\"layer\":\"pa\"}");
        s.addAssistantToolCalls(List.of(call));

        List<LlmMessage> msgs = s.getMessages();
        assertEquals(2, msgs.size());
        LlmMessage assistantMsg = msgs.get(1);
        assertEquals(LlmMessage.Role.ASSISTANT, assistantMsg.getRole());
        assertTrue(assistantMsg.hasToolCalls(),
            "regression: assistant tool-call message MUST persist tool_calls or the LLM will repeat them");
        assertEquals("call_1", assistantMsg.getToolCalls().get(0).getId());
    }

    @Test
    @DisplayName("addToolResult writes a TOOL role message linked to the call")
    void addToolResultLinksToCallId() {
        ConversationSession s = new ConversationSession();
        JsonObject result = new JsonObject();
        result.addProperty("count", 42);
        s.addToolResult("call_1", result);
        LlmMessage msg = s.getMessages().get(0);
        assertEquals(LlmMessage.Role.TOOL, msg.getRole());
        assertTrue(msg.getToolCallId().isPresent());
        assertEquals("call_1", msg.getToolCallId().get());
        assertTrue(msg.getContent().contains("42"));
    }

    @Test
    @DisplayName("clear() removes all messages but preserves session ID")
    void clearRemovesMessagesPreservesId() {
        ConversationSession s = new ConversationSession();
        String id = s.getSessionId();
        s.addUserMessage("a");
        s.addUserMessage("b");
        assertEquals(2, s.getMessageCount());
        s.clear();
        assertEquals(0, s.getMessageCount());
        assertEquals(id, s.getSessionId(), "session ID must persist across clear()");
    }

    @Test
    @DisplayName("addSystemMessage does NOT count toward truncation limit")
    void systemMessagesNotCountedTowardLimit() {
        ConversationSession s = new ConversationSession(10);
        s.addSystemMessage("you are helpful");
        s.addSystemMessage("respond in english");
        // Fill above limit
        for (int i = 0; i < 15; i++) {
            s.addUserMessage("u" + i);
            s.addAssistantMessage("a" + i);
        }
        // Truncated, but system messages survive at the front
        List<LlmMessage> msgs = s.getMessages();
        assertEquals(LlmMessage.Role.SYSTEM, msgs.get(0).getRole(),
            "system messages must be preserved at the front after truncation");
        assertEquals(LlmMessage.Role.SYSTEM, msgs.get(1).getRole());
    }

    @Test
    @DisplayName("setMaxMessages rejects values below the floor")
    void setMaxMessagesRejectsTooSmall() {
        ConversationSession s = new ConversationSession();
        assertThrows(IllegalArgumentException.class, () -> s.setMaxMessages(5));
    }

    @Test
    @DisplayName("constructor with custom max stores the value")
    void constructorWithCustomMax() {
        ConversationSession s = new ConversationSession(25);
        assertEquals(25, s.getMaxMessages());
    }

    @Test
    @DisplayName("toString includes id and message count")
    void toStringIncludesIdAndCount() {
        ConversationSession s = new ConversationSession();
        s.addUserMessage("x");
        String str = s.toString();
        assertTrue(str.contains(s.getSessionId()));
        assertTrue(str.contains("messages=1"));
    }
}

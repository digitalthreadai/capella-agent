package com.capellaagent.core.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import com.capellaagent.core.llm.LlmMessage;
import com.capellaagent.core.llm.LlmToolCall;
import com.google.gson.JsonObject;

/**
 * Manages conversation history for a single agent session.
 * <p>
 * Maintains an ordered list of {@link LlmMessage} objects representing the
 * full conversation between the user, assistant, and tools. Supports auto-
 * truncation when the message count exceeds a configurable limit, preserving
 * system messages and the most recent exchanges.
 */
public class ConversationSession {

    private static final Logger LOG = Logger.getLogger(ConversationSession.class.getName());

    /** Default maximum number of messages before truncation. */
    public static final int DEFAULT_MAX_MESSAGES = 50;

    private final String sessionId;
    private final List<LlmMessage> messages;
    private int maxMessages;

    /**
     * Creates a new conversation session with default settings.
     */
    public ConversationSession() {
        this(DEFAULT_MAX_MESSAGES);
    }

    /**
     * Creates a new conversation session with a custom message limit.
     *
     * @param maxMessages the maximum number of messages before truncation
     */
    public ConversationSession(int maxMessages) {
        this.sessionId = UUID.randomUUID().toString();
        this.messages = new ArrayList<>();
        this.maxMessages = maxMessages;
    }

    /**
     * Returns the unique session identifier.
     *
     * @return the session ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Adds a user message to the conversation.
     *
     * @param content the user's input text
     */
    public synchronized void addUserMessage(String content) {
        messages.add(LlmMessage.user(content));
        truncateIfNeeded();
    }

    /**
     * Adds an assistant message to the conversation.
     *
     * @param content the assistant's response text
     */
    public synchronized void addAssistantMessage(String content) {
        messages.add(LlmMessage.assistant(content));
        truncateIfNeeded();
    }

    /**
     * Adds a system message to the conversation.
     *
     * @param content the system prompt text
     */
    public synchronized void addSystemMessage(String content) {
        messages.add(LlmMessage.system(content));
        // System messages are not counted toward truncation limit
    }

    /**
     * Records that the assistant requested one or more tool calls.
     * <p>
     * This persists the assistant's tool-call turn in the conversation history
     * so the LLM does not repeat the same calls on the next iteration.
     *
     * @param toolCalls the tool calls requested by the assistant
     */
    public synchronized void addAssistantToolCalls(List<LlmToolCall> toolCalls) {
        messages.add(LlmMessage.assistantWithToolCalls(toolCalls));
        truncateIfNeeded();
    }

    /**
     * Records that the assistant requested a tool call.
     * <p>
     * This adds an assistant message containing the tool call information
     * so the conversation history accurately reflects the interaction.
     *
     * @param toolCall the tool call requested by the LLM
     */
    public synchronized void addToolCall(LlmToolCall toolCall) {
        String content = "[Tool call: " + toolCall.getName() + "(" + toolCall.getArguments() + ")]";
        messages.add(LlmMessage.assistant(content));
        truncateIfNeeded();
    }

    /**
     * Adds a tool result message to the conversation.
     *
     * @param toolCallId the identifier of the tool call this result belongs to
     * @param result     the JSON result from the tool execution
     */
    public synchronized void addToolResult(String toolCallId, JsonObject result) {
        messages.add(LlmMessage.toolResult(toolCallId, result.toString()));
        truncateIfNeeded();
    }

    /**
     * Returns an unmodifiable snapshot of the current message list.
     *
     * @return the conversation messages
     */
    public synchronized List<LlmMessage> getMessages() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }

    /**
     * Clears all messages from the conversation.
     */
    public synchronized void clear() {
        messages.clear();
        LOG.fine("Conversation session " + sessionId + " cleared");
    }

    /**
     * Returns the current number of messages.
     *
     * @return the message count
     */
    public synchronized int getMessageCount() {
        return messages.size();
    }

    /**
     * Returns the maximum message limit.
     *
     * @return the max messages value
     */
    public int getMaxMessages() {
        return maxMessages;
    }

    /**
     * Sets the maximum message limit.
     *
     * @param maxMessages the new limit; must be at least 10
     */
    public void setMaxMessages(int maxMessages) {
        if (maxMessages < 10) {
            throw new IllegalArgumentException("maxMessages must be at least 10, got " + maxMessages);
        }
        this.maxMessages = maxMessages;
    }

    /**
     * Truncates the message list if it exceeds the maximum limit.
     * <p>
     * Preserves system messages at the beginning and the most recent
     * non-system messages up to the limit.
     */
    private void truncateIfNeeded() {
        if (messages.size() <= maxMessages) {
            return;
        }

        // Separate system messages from the rest
        List<LlmMessage> systemMessages = new ArrayList<>();
        List<LlmMessage> nonSystemMessages = new ArrayList<>();

        for (LlmMessage msg : messages) {
            if (msg.getRole() == LlmMessage.Role.SYSTEM) {
                systemMessages.add(msg);
            } else {
                nonSystemMessages.add(msg);
            }
        }

        // Keep system messages + last N non-system messages
        int keepCount = maxMessages - systemMessages.size();
        if (keepCount < 1) {
            keepCount = 1;
        }

        int startIndex = Math.max(0, nonSystemMessages.size() - keepCount);
        List<LlmMessage> retained = nonSystemMessages.subList(startIndex, nonSystemMessages.size());

        messages.clear();
        messages.addAll(systemMessages);
        messages.addAll(retained);

        LOG.fine("Truncated conversation to " + messages.size() + " messages " +
                "(system=" + systemMessages.size() + ", recent=" + retained.size() + ")");
    }

    @Override
    public String toString() {
        return "ConversationSession{id='" + sessionId +
                "', messages=" + messages.size() +
                ", maxMessages=" + maxMessages + "}";
    }
}

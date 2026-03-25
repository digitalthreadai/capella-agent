package com.capellaagent.core.llm;

import java.util.Objects;

/**
 * Data transfer object representing the result of a tool execution,
 * to be sent back to the LLM for continued processing.
 */
public final class LlmToolResult {

    private final String toolCallId;
    private final String content;

    /**
     * Constructs a new tool result.
     *
     * @param toolCallId the identifier of the tool call this result corresponds to
     * @param content    the JSON string result from tool execution
     */
    public LlmToolResult(String toolCallId, String content) {
        this.toolCallId = Objects.requireNonNull(toolCallId, "toolCallId must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
    }

    /**
     * Returns the tool call identifier this result belongs to.
     *
     * @return the tool call ID
     */
    public String getToolCallId() {
        return toolCallId;
    }

    /**
     * Returns the JSON string content of the tool result.
     *
     * @return the result content
     */
    public String getContent() {
        return content;
    }

    @Override
    public String toString() {
        return "LlmToolResult{toolCallId='" + toolCallId + "', content='" +
                (content.length() > 100 ? content.substring(0, 100) + "..." : content) + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LlmToolResult that = (LlmToolResult) o;
        return toolCallId.equals(that.toolCallId) && content.equals(that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolCallId, content);
    }
}

package com.capellaagent.core.llm;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Data transfer object representing the complete response from an LLM provider.
 * <p>
 * A response may contain text content, tool calls, or both, depending on the
 * LLM's decision. The {@code stopReason} indicates why the LLM stopped generating.
 */
public final class LlmResponse {

    private final String textContent;
    private final List<LlmToolCall> toolCalls;
    private final String stopReason;

    /**
     * Constructs a new LLM response.
     *
     * @param textContent the text portion of the response; may be null or empty
     * @param toolCalls   the list of tool calls requested by the LLM; may be null or empty
     * @param stopReason  the reason the LLM stopped generating (e.g., "end_turn", "tool_use", "stop")
     */
    public LlmResponse(String textContent, List<LlmToolCall> toolCalls, String stopReason) {
        this.textContent = textContent;
        this.toolCalls = toolCalls != null ? List.copyOf(toolCalls) : Collections.emptyList();
        this.stopReason = stopReason;
    }

    /**
     * Returns the text content of the response, or null if none.
     *
     * @return the text content, or null
     */
    public String getTextContent() {
        return textContent;
    }

    /**
     * Returns the immutable list of tool calls requested by the LLM.
     *
     * @return the tool calls list; never null, may be empty
     */
    public List<LlmToolCall> getToolCalls() {
        return toolCalls;
    }

    /**
     * Returns the stop reason string from the LLM provider.
     *
     * @return the stop reason
     */
    public String getStopReason() {
        return stopReason;
    }

    /**
     * Checks whether this response contains any tool calls.
     *
     * @return true if the LLM requested one or more tool invocations
     */
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    /**
     * Checks whether this response contains non-empty text content.
     *
     * @return true if textContent is non-null and non-blank
     */
    public boolean hasTextContent() {
        return textContent != null && !textContent.isBlank();
    }

    @Override
    public String toString() {
        return "LlmResponse{textContent='" +
                (textContent != null && textContent.length() > 80
                        ? textContent.substring(0, 80) + "..." : textContent) +
                "', toolCalls=" + toolCalls.size() +
                ", stopReason='" + stopReason + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LlmResponse that = (LlmResponse) o;
        return Objects.equals(textContent, that.textContent) &&
                toolCalls.equals(that.toolCalls) &&
                Objects.equals(stopReason, that.stopReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(textContent, toolCalls, stopReason);
    }
}

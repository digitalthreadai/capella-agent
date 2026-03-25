package com.capellaagent.core.llm;

import java.util.Objects;

/**
 * Data transfer object representing a tool invocation requested by the LLM.
 * <p>
 * When the LLM determines it needs to call a tool, it returns one or more
 * {@code LlmToolCall} instances describing which function to invoke and
 * with what arguments.
 */
public final class LlmToolCall {

    private final String id;
    private final String name;
    private final String arguments;

    /**
     * Constructs a new tool call.
     *
     * @param id        unique identifier for this tool call, used to correlate results
     * @param name      the function name of the tool to invoke
     * @param arguments the raw JSON string of arguments to pass to the tool
     */
    public LlmToolCall(String id, String name, String arguments) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.arguments = Objects.requireNonNull(arguments, "arguments must not be null");
    }

    /**
     * Returns the unique identifier for this tool call.
     *
     * @return the tool call ID
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the function name of the tool to invoke.
     *
     * @return the tool function name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the raw JSON string of arguments for the tool.
     *
     * @return the arguments as a JSON string
     */
    public String getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return "LlmToolCall{id='" + id + "', name='" + name + "', arguments='" +
                (arguments.length() > 100 ? arguments.substring(0, 100) + "..." : arguments) + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LlmToolCall that = (LlmToolCall) o;
        return id.equals(that.id) && name.equals(that.name) && arguments.equals(that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, arguments);
    }
}

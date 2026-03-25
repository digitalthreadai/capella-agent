package com.capellaagent.core.tools;

import java.util.Objects;

/**
 * Pairs a tool descriptor with its executor implementation.
 * <p>
 * This class is the unit of registration in the {@link ToolRegistry},
 * binding the metadata (what the tool is) with the implementation
 * (how the tool executes).
 */
public final class ToolRegistration {

    private final IToolDescriptor descriptor;
    private final IToolExecutor executor;

    /**
     * Creates a new tool registration.
     *
     * @param descriptor the tool's metadata descriptor; must not be null
     * @param executor   the tool's execution logic; must not be null
     */
    public ToolRegistration(IToolDescriptor descriptor, IToolExecutor executor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * Returns the tool descriptor.
     *
     * @return the descriptor
     */
    public IToolDescriptor getDescriptor() {
        return descriptor;
    }

    /**
     * Returns the tool executor.
     *
     * @return the executor
     */
    public IToolExecutor getExecutor() {
        return executor;
    }

    @Override
    public String toString() {
        return "ToolRegistration{name='" + descriptor.getName() +
                "', category='" + descriptor.getCategory() + "'}";
    }
}

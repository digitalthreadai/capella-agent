package com.capellaagent.modelchat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolRegistry;

/**
 * Registers all Model Chat tools with the core {@link ToolRegistry}.
 * <p>
 * Tools are organized into categories:
 * <ul>
 *   <li><b>model_read</b> - Read-only queries against the Capella model</li>
 *   <li><b>model_write</b> - Mutations that create, update, or delete model elements</li>
 *   <li><b>diagram</b> - Diagram manipulation tools</li>
 *   <li><b>analysis</b> - Model analysis (validation, cycles, impact, statistics)</li>
 *   <li><b>export</b> - Model export (CSV, JSON, reports, traceability matrices)</li>
 *   <li><b>transition</b> - Layer transition tools (OA->SA, SA->LA, LA->PA)</li>
 * </ul>
 * <p>
 * This class is instantiated by {@link ModelChatActivator} during bundle startup and
 * can also be contributed via the {@code com.capellaagent.core.toolProvider} extension point.
 */
public class ModelChatToolRegistrar {

    private final List<AbstractCapellaTool> registeredTools = new ArrayList<>();

    /**
     * Registers all model chat tools with the core {@link ToolRegistry}.
     * <p>
     * Each tool is instantiated and added to the registry under its declared name.
     * Tools that fail to register are logged but do not prevent other tools from registering.
     */
    public void registerAll() {
        ToolRegistry registry = ToolRegistry.getInstance();
        ReadToolRegistrar.registerAll(registry, this);
        WriteToolRegistrar.registerAll(registry, this);
        DiagramToolRegistrar.registerAll(registry, this);
        AnalysisToolRegistrar.registerAll(registry, this);
        ExportToolRegistrar.registerAll(registry, this);
        TransitionToolRegistrar.registerAll(registry, this);
        AiToolRegistrar.registerAll(registry, this);
    }

    /**
     * Unregisters all tools that were previously registered by this registrar.
     */
    public void unregisterAll() {
        ToolRegistry registry = ToolRegistry.getInstance();
        for (AbstractCapellaTool tool : registeredTools) {
            try {
                registry.unregister(tool.getName());
            } catch (Exception e) {
                ModelChatActivator.getDefault().getLog().warn(
                        "Failed to unregister tool: " + tool.getName(), e);
            }
        }
        registeredTools.clear();
    }

    /**
     * Returns an unmodifiable view of all tools registered by this registrar.
     *
     * @return the list of registered tools
     */
    public List<AbstractCapellaTool> getRegisteredTools() {
        return Collections.unmodifiableList(registeredTools);
    }

    void reg(ToolRegistry registry, AbstractCapellaTool tool) {
        try {
            registry.register(tool);
            registeredTools.add(tool);
        } catch (Exception e) {
            if (ModelChatActivator.getDefault() != null) {
                ModelChatActivator.getDefault().getLog().warn(
                        "Failed to register tool: " + tool.getName(), e);
            }
        }
    }
}

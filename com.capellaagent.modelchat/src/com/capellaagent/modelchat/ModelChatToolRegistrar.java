package com.capellaagent.modelchat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolRegistry;
import com.capellaagent.modelchat.tools.diagram.RefreshDiagramTool;
import com.capellaagent.modelchat.tools.diagram.UpdateDiagramTool;
import com.capellaagent.modelchat.tools.read.GetElementDetailsTool;
import com.capellaagent.modelchat.tools.read.GetHierarchyTool;
import com.capellaagent.modelchat.tools.read.GetTraceabilityTool;
import com.capellaagent.modelchat.tools.read.ListDiagramsTool;
import com.capellaagent.modelchat.tools.read.ListElementsTool;
import com.capellaagent.modelchat.tools.read.ListRequirementsTool;
import com.capellaagent.modelchat.tools.read.SearchElementsTool;
import com.capellaagent.modelchat.tools.read.ValidateModelTool;
import com.capellaagent.modelchat.tools.write.AllocateFunctionTool;
import com.capellaagent.modelchat.tools.write.CreateCapabilityTool;
import com.capellaagent.modelchat.tools.write.CreateElementTool;
import com.capellaagent.modelchat.tools.write.CreateExchangeTool;
import com.capellaagent.modelchat.tools.write.DeleteElementTool;
import com.capellaagent.modelchat.tools.write.UpdateElementTool;

/**
 * Registers all Model Chat tools with the core {@link ToolRegistry}.
 * <p>
 * Tools are organized into three categories:
 * <ul>
 *   <li><b>model_read</b> - Read-only queries against the Capella model (list, search, hierarchy, etc.)</li>
 *   <li><b>model_write</b> - Mutations that create, update, or delete model elements</li>
 *   <li><b>diagram</b> - Diagram manipulation tools (add/remove elements, refresh)</li>
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

        // -- Read tools (category: model_read) --
        registerTool(registry, new ListElementsTool());
        registerTool(registry, new GetElementDetailsTool());
        registerTool(registry, new SearchElementsTool());
        registerTool(registry, new GetHierarchyTool());
        registerTool(registry, new ListDiagramsTool());
        registerTool(registry, new ListRequirementsTool());
        registerTool(registry, new GetTraceabilityTool());
        registerTool(registry, new ValidateModelTool());

        // -- Write tools (category: model_write) --
        registerTool(registry, new CreateElementTool());
        registerTool(registry, new CreateExchangeTool());
        registerTool(registry, new AllocateFunctionTool());
        registerTool(registry, new CreateCapabilityTool());
        registerTool(registry, new UpdateElementTool());
        registerTool(registry, new DeleteElementTool());

        // -- Diagram tools (category: diagram) --
        registerTool(registry, new UpdateDiagramTool());
        registerTool(registry, new RefreshDiagramTool());
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

    private void registerTool(ToolRegistry registry, AbstractCapellaTool tool) {
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

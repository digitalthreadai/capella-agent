package com.capellaagent.modelchat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolRegistry;
// -- AI intelligence tools --
import com.capellaagent.modelchat.tools.ai.AutoAllocateTool;
import com.capellaagent.modelchat.tools.ai.GenerateTestScenariosTool;
import com.capellaagent.modelchat.tools.ai.ModelQAndATool;
import com.capellaagent.modelchat.tools.ai.ReviewArchitectureTool;
import com.capellaagent.modelchat.tools.ai.SuggestInterfacesTool;
// -- Analysis tools --
import com.capellaagent.modelchat.tools.analysis.AllocationCompletenessTool;
import com.capellaagent.modelchat.tools.analysis.ArchitectureComplexityTool;
import com.capellaagent.modelchat.tools.analysis.DetectCyclesTool;
import com.capellaagent.modelchat.tools.analysis.FindUnusedElementsTool;
import com.capellaagent.modelchat.tools.analysis.GenerateSafetyReportTool;
import com.capellaagent.modelchat.tools.analysis.IdentifySingletonsTool;
import com.capellaagent.modelchat.tools.analysis.ImpactAnalysisTool;
import com.capellaagent.modelchat.tools.analysis.InterfaceConsistencyTool;
import com.capellaagent.modelchat.tools.analysis.ModelStatisticsTool;
import com.capellaagent.modelchat.tools.analysis.ReachabilityAnalysisTool;
import com.capellaagent.modelchat.tools.analysis.RunCapellaValidationTool;
import com.capellaagent.modelchat.tools.analysis.SuggestImprovementsTool;
// -- Diagram tools --
import com.capellaagent.modelchat.tools.diagram.AutoLayoutDiagramTool;
import com.capellaagent.modelchat.tools.diagram.CloneDiagramTool;
import com.capellaagent.modelchat.tools.diagram.CreateDiagramTool;
import com.capellaagent.modelchat.tools.diagram.DeleteDiagramTool;
import com.capellaagent.modelchat.tools.diagram.ExportDiagramImageTool;
import com.capellaagent.modelchat.tools.diagram.ListDiagramElementsTool;
import com.capellaagent.modelchat.tools.diagram.RefreshDiagramTool;
import com.capellaagent.modelchat.tools.diagram.ShowElementInDiagramTool;
import com.capellaagent.modelchat.tools.diagram.UpdateDiagramTool;
// -- Export tools --
import com.capellaagent.modelchat.tools.export_.ExportAllocationMatrixCsvTool;
import com.capellaagent.modelchat.tools.export_.ExportDiagramCatalogTool;
import com.capellaagent.modelchat.tools.export_.ExportToCsvTool;
import com.capellaagent.modelchat.tools.export_.ExportToJsonTool;
import com.capellaagent.modelchat.tools.export_.ExportTraceabilityMatrixTool;
import com.capellaagent.modelchat.tools.export_.GenerateDiffReportTool;
import com.capellaagent.modelchat.tools.export_.GenerateIcdReportTool;
import com.capellaagent.modelchat.tools.export_.GenerateModelReportTool;
// -- Read tools --
import com.capellaagent.modelchat.tools.read.CheckTraceabilityCoverageTool;
import com.capellaagent.modelchat.tools.read.ExplainElementTool;
import com.capellaagent.modelchat.tools.read.GetAllocationMatrixTool;
import com.capellaagent.modelchat.tools.read.GetConfigurationItemsTool;
import com.capellaagent.modelchat.tools.read.GetConstraintsTool;
import com.capellaagent.modelchat.tools.read.GetDataModelTool;
import com.capellaagent.modelchat.tools.read.GetDeploymentMappingTool;
import com.capellaagent.modelchat.tools.read.GetElementDetailsTool;
import com.capellaagent.modelchat.tools.read.GetExchangeItemsTool;
import com.capellaagent.modelchat.tools.read.GetFunctionalChainTool;
import com.capellaagent.modelchat.tools.read.GetHierarchyTool;
import com.capellaagent.modelchat.tools.read.GetInterfacesTool;
import com.capellaagent.modelchat.tools.read.GetPhysicalPathsTool;
import com.capellaagent.modelchat.tools.read.GetPropertyValuesTool;
import com.capellaagent.modelchat.tools.read.GetRefinementStatusTool;
import com.capellaagent.modelchat.tools.read.GetScenariosTool;
import com.capellaagent.modelchat.tools.read.GetStateMachinesTool;
import com.capellaagent.modelchat.tools.read.GetTraceabilityTool;
import com.capellaagent.modelchat.tools.read.ListDiagramsTool;
import com.capellaagent.modelchat.tools.read.ListElementsTool;
import com.capellaagent.modelchat.tools.read.ListRequirementsTool;
import com.capellaagent.modelchat.tools.read.SearchElementsTool;
import com.capellaagent.modelchat.tools.read.ValidateModelTool;
// -- Transition tools --
import com.capellaagent.modelchat.tools.transition.ReconcileLayersTool;
import com.capellaagent.modelchat.tools.transition.TransitionLaToPaTool;
import com.capellaagent.modelchat.tools.transition.TransitionOaToSaTool;
import com.capellaagent.modelchat.tools.transition.TransitionPaToEpbsTool;
import com.capellaagent.modelchat.tools.transition.TransitionSaToLaTool;
// -- Write tools --
import com.capellaagent.modelchat.tools.write.AllocateFunctionTool;
import com.capellaagent.modelchat.tools.write.BatchRenameTool;
import com.capellaagent.modelchat.tools.write.BatchUpdatePropertiesTool;
import com.capellaagent.modelchat.tools.write.CloneElementTool;
import com.capellaagent.modelchat.tools.write.CreateCapabilityTool;
import com.capellaagent.modelchat.tools.write.CreateDataTypeTool;
import com.capellaagent.modelchat.tools.write.CreateElementTool;
import com.capellaagent.modelchat.tools.write.CreateExchangeItemTool;
import com.capellaagent.modelchat.tools.write.CreateExchangeTool;
import com.capellaagent.modelchat.tools.write.CreateFunctionalChainTool;
import com.capellaagent.modelchat.tools.write.CreateGeneralizationTool;
import com.capellaagent.modelchat.tools.write.CreateInterfaceTool;
import com.capellaagent.modelchat.tools.write.CreateInvolvementTool;
import com.capellaagent.modelchat.tools.write.CreatePhysicalLinkTool;
import com.capellaagent.modelchat.tools.write.CreatePortTool;
import com.capellaagent.modelchat.tools.write.CreateScenarioTool;
import com.capellaagent.modelchat.tools.write.CreateStateMachineTool;
import com.capellaagent.modelchat.tools.write.DeleteElementTool;
import com.capellaagent.modelchat.tools.write.MoveElementTool;
import com.capellaagent.modelchat.tools.write.SetPropertyValueTool;
import com.capellaagent.modelchat.tools.write.UpdateElementTool;

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

        // -- Read tools (category: model_read) --
        registerTool(registry, new ListElementsTool());
        registerTool(registry, new GetElementDetailsTool());
        registerTool(registry, new SearchElementsTool());
        registerTool(registry, new GetHierarchyTool());
        registerTool(registry, new ListDiagramsTool());
        registerTool(registry, new ListRequirementsTool());
        registerTool(registry, new GetTraceabilityTool());
        registerTool(registry, new ValidateModelTool());
        registerTool(registry, new GetAllocationMatrixTool());
        registerTool(registry, new GetFunctionalChainTool());
        registerTool(registry, new GetInterfacesTool());
        registerTool(registry, new GetScenariosTool());
        registerTool(registry, new ExplainElementTool());
        // P2 read tools
        registerTool(registry, new GetStateMachinesTool());
        registerTool(registry, new GetDataModelTool());
        registerTool(registry, new GetPhysicalPathsTool());
        registerTool(registry, new GetExchangeItemsTool());
        registerTool(registry, new GetPropertyValuesTool());
        registerTool(registry, new GetConstraintsTool());
        // P3 read tools
        registerTool(registry, new GetConfigurationItemsTool());
        registerTool(registry, new GetDeploymentMappingTool());

        // -- Write tools (category: model_write) --
        registerTool(registry, new CreateElementTool());
        registerTool(registry, new CreateExchangeTool());
        registerTool(registry, new AllocateFunctionTool());
        registerTool(registry, new CreateCapabilityTool());
        registerTool(registry, new UpdateElementTool());
        registerTool(registry, new DeleteElementTool());
        registerTool(registry, new CreateInterfaceTool());
        registerTool(registry, new CreateFunctionalChainTool());
        registerTool(registry, new CreatePhysicalLinkTool());
        registerTool(registry, new BatchRenameTool());
        // P2 write tools
        registerTool(registry, new CreateScenarioTool());
        registerTool(registry, new CreateStateMachineTool());
        registerTool(registry, new CreateDataTypeTool());
        registerTool(registry, new MoveElementTool());
        registerTool(registry, new CloneElementTool());
        registerTool(registry, new SetPropertyValueTool());
        registerTool(registry, new CreateExchangeItemTool());
        registerTool(registry, new BatchUpdatePropertiesTool());
        registerTool(registry, new CreatePortTool());
        registerTool(registry, new CreateInvolvementTool());
        registerTool(registry, new CreateGeneralizationTool());

        // -- Diagram tools (category: diagram) --
        registerTool(registry, new UpdateDiagramTool());
        registerTool(registry, new RefreshDiagramTool());
        registerTool(registry, new CreateDiagramTool());
        registerTool(registry, new ExportDiagramImageTool());
        registerTool(registry, new ShowElementInDiagramTool());
        // P2 diagram tools
        registerTool(registry, new ListDiagramElementsTool());
        registerTool(registry, new CloneDiagramTool());
        // P3 diagram tools
        registerTool(registry, new AutoLayoutDiagramTool());
        registerTool(registry, new DeleteDiagramTool());

        // -- Analysis tools (category: analysis) --
        registerTool(registry, new CheckTraceabilityCoverageTool());
        registerTool(registry, new RunCapellaValidationTool());
        registerTool(registry, new DetectCyclesTool());
        registerTool(registry, new ImpactAnalysisTool());
        registerTool(registry, new FindUnusedElementsTool());
        registerTool(registry, new ModelStatisticsTool());
        registerTool(registry, new AllocationCompletenessTool());
        // P2 analysis tools
        registerTool(registry, new GetRefinementStatusTool());
        registerTool(registry, new InterfaceConsistencyTool());
        registerTool(registry, new ReachabilityAnalysisTool());
        registerTool(registry, new ArchitectureComplexityTool());
        registerTool(registry, new IdentifySingletonsTool());
        registerTool(registry, new SuggestImprovementsTool());
        // P3 analysis tools
        registerTool(registry, new GenerateSafetyReportTool());

        // -- Export tools (category: export) --
        registerTool(registry, new ExportToCsvTool());
        registerTool(registry, new ExportToJsonTool());
        registerTool(registry, new GenerateIcdReportTool());
        registerTool(registry, new GenerateModelReportTool());
        registerTool(registry, new ExportTraceabilityMatrixTool());
        // P2 export tools
        registerTool(registry, new ExportAllocationMatrixCsvTool());
        // P3 export tools
        registerTool(registry, new ExportDiagramCatalogTool());
        registerTool(registry, new GenerateDiffReportTool());

        // -- Transition tools (category: transition) --
        registerTool(registry, new TransitionOaToSaTool());
        registerTool(registry, new TransitionSaToLaTool());
        registerTool(registry, new TransitionLaToPaTool());
        registerTool(registry, new ReconcileLayersTool());
        // P3 transition tools
        registerTool(registry, new TransitionPaToEpbsTool());

        // -- AI Intelligence tools (category: ai_intelligence) --
        registerTool(registry, new ReviewArchitectureTool());
        registerTool(registry, new SuggestInterfacesTool());
        registerTool(registry, new AutoAllocateTool());
        registerTool(registry, new GenerateTestScenariosTool());
        registerTool(registry, new ModelQAndATool());
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

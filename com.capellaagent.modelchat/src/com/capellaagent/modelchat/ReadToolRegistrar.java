package com.capellaagent.modelchat;

import com.capellaagent.core.tools.ToolRegistry;
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
import com.capellaagent.modelchat.tools.read.GetScenariosTool;
import com.capellaagent.modelchat.tools.read.GetStateMachinesTool;
import com.capellaagent.modelchat.tools.read.GetTraceabilityTool;
import com.capellaagent.modelchat.tools.read.ListDiagramsTool;
import com.capellaagent.modelchat.tools.read.ListElementsTool;
import com.capellaagent.modelchat.tools.read.ListRequirementsTool;
import com.capellaagent.modelchat.tools.read.SearchByPatternTool;
import com.capellaagent.modelchat.tools.read.SearchElementsTool;
import com.capellaagent.modelchat.tools.read.GetVersionHistoryTool;
import com.capellaagent.modelchat.tools.read.ValidateModelTool;
import com.capellaagent.modelchat.tools.read.GetModesCapturedTimeTool;
import com.capellaagent.modelchat.tools.read.GetFunctionPortsTool;
import com.capellaagent.modelchat.tools.read.GetComponentPortsTool;
import com.capellaagent.modelchat.tools.read.GetCommunicationLinksTool;
import com.capellaagent.modelchat.tools.read.GetRequirementRelationsTool;

/**
 * Registers all read (model_read) tools with the core {@link ToolRegistry}.
 * Delegated from {@link ModelChatToolRegistrar#registerAll()}.
 */
final class ReadToolRegistrar {

    private ReadToolRegistrar() {}

    static void registerAll(ToolRegistry registry, ModelChatToolRegistrar parent) {
        parent.reg(registry, new ListElementsTool());
        parent.reg(registry, new GetElementDetailsTool());
        parent.reg(registry, new SearchElementsTool());
        parent.reg(registry, new GetHierarchyTool());
        parent.reg(registry, new ListDiagramsTool());
        parent.reg(registry, new ListRequirementsTool());
        parent.reg(registry, new GetTraceabilityTool());
        parent.reg(registry, new ValidateModelTool());
        parent.reg(registry, new GetAllocationMatrixTool());
        parent.reg(registry, new GetFunctionalChainTool());
        parent.reg(registry, new GetInterfacesTool());
        parent.reg(registry, new GetScenariosTool());
        parent.reg(registry, new ExplainElementTool());
        // P2 read tools
        parent.reg(registry, new GetStateMachinesTool());
        parent.reg(registry, new GetDataModelTool());
        parent.reg(registry, new GetPhysicalPathsTool());
        parent.reg(registry, new GetExchangeItemsTool());
        parent.reg(registry, new GetPropertyValuesTool());
        parent.reg(registry, new GetConstraintsTool());
        parent.reg(registry, new GetModesCapturedTimeTool());
        parent.reg(registry, new GetFunctionPortsTool());
        parent.reg(registry, new GetComponentPortsTool());
        parent.reg(registry, new GetCommunicationLinksTool());
        parent.reg(registry, new GetRequirementRelationsTool());
        // P3 read tools
        parent.reg(registry, new GetConfigurationItemsTool());
        parent.reg(registry, new GetDeploymentMappingTool());
        parent.reg(registry, new GetVersionHistoryTool());
        parent.reg(registry, new SearchByPatternTool());
    }
}

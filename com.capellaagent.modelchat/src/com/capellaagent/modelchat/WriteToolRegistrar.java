package com.capellaagent.modelchat;

import com.capellaagent.core.tools.ToolRegistry;
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
import com.capellaagent.modelchat.tools.write.ApplyPatternTool;
import com.capellaagent.modelchat.tools.write.MergeElementsTool;
import com.capellaagent.modelchat.tools.write.UpdateElementTool;
import com.capellaagent.modelchat.tools.write.CreateModeTool;
import com.capellaagent.modelchat.tools.write.CreateConstraintTool;
import com.capellaagent.modelchat.tools.write.SetDescriptionTool;
import com.capellaagent.modelchat.tools.write.ReorderElementsTool;

/**
 * Registers all write (model_write) tools with the core {@link ToolRegistry}.
 * Delegated from {@link ModelChatToolRegistrar#registerAll()}.
 */
final class WriteToolRegistrar {

    private WriteToolRegistrar() {}

    static void registerAll(ToolRegistry registry, ModelChatToolRegistrar parent) {
        parent.reg(registry, new CreateElementTool());
        parent.reg(registry, new CreateExchangeTool());
        parent.reg(registry, new AllocateFunctionTool());
        parent.reg(registry, new CreateCapabilityTool());
        parent.reg(registry, new UpdateElementTool());
        parent.reg(registry, new DeleteElementTool());
        parent.reg(registry, new CreateInterfaceTool());
        parent.reg(registry, new CreateFunctionalChainTool());
        parent.reg(registry, new CreatePhysicalLinkTool());
        parent.reg(registry, new BatchRenameTool());
        // P2 write tools
        parent.reg(registry, new CreateScenarioTool());
        parent.reg(registry, new CreateStateMachineTool());
        parent.reg(registry, new CreateDataTypeTool());
        parent.reg(registry, new MoveElementTool());
        parent.reg(registry, new CloneElementTool());
        parent.reg(registry, new SetPropertyValueTool());
        parent.reg(registry, new CreateExchangeItemTool());
        parent.reg(registry, new BatchUpdatePropertiesTool());
        parent.reg(registry, new CreatePortTool());
        parent.reg(registry, new CreateInvolvementTool());
        parent.reg(registry, new CreateGeneralizationTool());
        parent.reg(registry, new CreateModeTool());
        parent.reg(registry, new CreateConstraintTool());
        parent.reg(registry, new SetDescriptionTool());
        parent.reg(registry, new ReorderElementsTool());
        // P3 write tools
        parent.reg(registry, new MergeElementsTool());
        parent.reg(registry, new ApplyPatternTool());
    }
}

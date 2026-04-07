package com.capellaagent.modelchat;

import com.capellaagent.core.tools.ToolRegistry;
import com.capellaagent.modelchat.tools.ai.AutoAllocateTool;
import com.capellaagent.modelchat.tools.ai.GenerateTestCasesTool;
import com.capellaagent.modelchat.tools.ai.GenerateTestScenariosTool;
import com.capellaagent.modelchat.tools.ai.ModelQAndATool;
import com.capellaagent.modelchat.tools.ai.OptimizeAllocationTool;
import com.capellaagent.modelchat.tools.ai.PredictImpactTool;
import com.capellaagent.modelchat.tools.ai.ReviewArchitectureTool;
import com.capellaagent.modelchat.tools.ai.SuggestInterfacesTool;
import com.capellaagent.modelchat.tools.ai.SummarizeModelTool;
import com.capellaagent.modelchat.tools.ai.ValidateNamingTool;

/**
 * Registers all AI intelligence tools with the core {@link ToolRegistry}.
 * Delegated from {@link ModelChatToolRegistrar#registerAll()}.
 */
final class AiToolRegistrar {

    private AiToolRegistrar() {}

    static void registerAll(ToolRegistry registry, ModelChatToolRegistrar parent) {
        parent.reg(registry, new ReviewArchitectureTool());
        parent.reg(registry, new SuggestInterfacesTool());
        parent.reg(registry, new AutoAllocateTool());
        parent.reg(registry, new GenerateTestScenariosTool());
        parent.reg(registry, new ModelQAndATool());
        parent.reg(registry, new SummarizeModelTool());
        parent.reg(registry, new ValidateNamingTool());
        // P3 AI tools
        parent.reg(registry, new PredictImpactTool());
        parent.reg(registry, new GenerateTestCasesTool());
        parent.reg(registry, new OptimizeAllocationTool());
    }
}

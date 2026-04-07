package com.capellaagent.modelchat;

import com.capellaagent.core.tools.ToolRegistry;
import com.capellaagent.modelchat.tools.read.CheckTraceabilityCoverageTool;
import com.capellaagent.modelchat.tools.read.GetRefinementStatusTool;
import com.capellaagent.modelchat.tools.analysis.AllocationCompletenessTool;
import com.capellaagent.modelchat.tools.analysis.ArchitectureComplexityTool;
import com.capellaagent.modelchat.tools.analysis.DetectCyclesTool;
import com.capellaagent.modelchat.tools.analysis.FindUnusedElementsTool;
import com.capellaagent.modelchat.tools.analysis.GenerateSafetyReportTool;
import com.capellaagent.modelchat.tools.analysis.IdentifySingletonsTool;
import com.capellaagent.modelchat.tools.analysis.SecurityAnalysisTool;
import com.capellaagent.modelchat.tools.analysis.WeightAnalysisTool;
import com.capellaagent.modelchat.tools.analysis.ImpactAnalysisTool;
import com.capellaagent.modelchat.tools.analysis.InterfaceConsistencyTool;
import com.capellaagent.modelchat.tools.analysis.ModelStatisticsTool;
import com.capellaagent.modelchat.tools.analysis.ReachabilityAnalysisTool;
import com.capellaagent.modelchat.tools.analysis.RunCapellaValidationTool;
import com.capellaagent.modelchat.tools.analysis.SuggestImprovementsTool;
import com.capellaagent.modelchat.tools.analysis.CompareModelsTool;
import com.capellaagent.modelchat.tools.analysis.FindDuplicatesTool;
import com.capellaagent.modelchat.tools.analysis.CoverageReportTool;

/**
 * Registers all analysis tools with the core {@link ToolRegistry}.
 * Delegated from {@link ModelChatToolRegistrar#registerAll()}.
 */
final class AnalysisToolRegistrar {

    private AnalysisToolRegistrar() {}

    static void registerAll(ToolRegistry registry, ModelChatToolRegistrar parent) {
        parent.reg(registry, new CheckTraceabilityCoverageTool());
        parent.reg(registry, new RunCapellaValidationTool());
        parent.reg(registry, new DetectCyclesTool());
        parent.reg(registry, new ImpactAnalysisTool());
        parent.reg(registry, new FindUnusedElementsTool());
        parent.reg(registry, new ModelStatisticsTool());
        parent.reg(registry, new AllocationCompletenessTool());
        // P2 analysis tools
        parent.reg(registry, new GetRefinementStatusTool());
        parent.reg(registry, new InterfaceConsistencyTool());
        parent.reg(registry, new ReachabilityAnalysisTool());
        parent.reg(registry, new ArchitectureComplexityTool());
        parent.reg(registry, new IdentifySingletonsTool());
        parent.reg(registry, new SuggestImprovementsTool());
        parent.reg(registry, new CompareModelsTool());
        parent.reg(registry, new FindDuplicatesTool());
        parent.reg(registry, new CoverageReportTool());
        // P3 analysis tools
        parent.reg(registry, new GenerateSafetyReportTool());
        parent.reg(registry, new SecurityAnalysisTool());
        parent.reg(registry, new WeightAnalysisTool());
    }
}

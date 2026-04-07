package com.capellaagent.modelchat;

import com.capellaagent.core.tools.ToolRegistry;
import com.capellaagent.modelchat.tools.export_.ExportAllocationMatrixCsvTool;
import com.capellaagent.modelchat.tools.export_.ExportDiagramCatalogTool;
import com.capellaagent.modelchat.tools.export_.ExportToCsvTool;
import com.capellaagent.modelchat.tools.export_.ExportToJsonTool;
import com.capellaagent.modelchat.tools.export_.ExportTraceabilityMatrixTool;
import com.capellaagent.modelchat.tools.export_.GenerateDiffReportTool;
import com.capellaagent.modelchat.tools.export_.GenerateIcdReportTool;
import com.capellaagent.modelchat.tools.export_.GenerateDocumentTool;
import com.capellaagent.modelchat.tools.export_.GenerateModelReportTool;
import com.capellaagent.modelchat.tools.export_.ExportToReqIfTool;
import com.capellaagent.modelchat.tools.export_.ExportToSysMLv2Tool;
import com.capellaagent.modelchat.tools.export_.ExportDiagramSvgTool;

/**
 * Registers all export tools with the core {@link ToolRegistry}.
 * Delegated from {@link ModelChatToolRegistrar#registerAll()}.
 */
final class ExportToolRegistrar {

    private ExportToolRegistrar() {}

    static void registerAll(ToolRegistry registry, ModelChatToolRegistrar parent) {
        parent.reg(registry, new ExportToCsvTool());
        parent.reg(registry, new ExportToJsonTool());
        parent.reg(registry, new GenerateIcdReportTool());
        parent.reg(registry, new GenerateModelReportTool());
        parent.reg(registry, new ExportTraceabilityMatrixTool());
        // P2 export tools
        parent.reg(registry, new ExportAllocationMatrixCsvTool());
        parent.reg(registry, new ExportToReqIfTool());
        parent.reg(registry, new ExportDiagramSvgTool());
        // P3 export tools
        parent.reg(registry, new ExportDiagramCatalogTool());
        parent.reg(registry, new GenerateDiffReportTool());
        parent.reg(registry, new ExportToSysMLv2Tool());
        parent.reg(registry, new GenerateDocumentTool());
    }
}

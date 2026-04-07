package com.capellaagent.modelchat;

import com.capellaagent.core.tools.ToolRegistry;
import com.capellaagent.modelchat.tools.diagram.AutoLayoutDiagramTool;
import com.capellaagent.modelchat.tools.diagram.CloneDiagramTool;
import com.capellaagent.modelchat.tools.diagram.CreateDiagramTool;
import com.capellaagent.modelchat.tools.diagram.DeleteDiagramTool;
import com.capellaagent.modelchat.tools.diagram.ExportDiagramImageTool;
import com.capellaagent.modelchat.tools.diagram.GenerateDiagramTool;
import com.capellaagent.modelchat.tools.diagram.ListDiagramElementsTool;
import com.capellaagent.modelchat.tools.diagram.RefreshDiagramTool;
import com.capellaagent.modelchat.tools.diagram.ShowElementInDiagramTool;
import com.capellaagent.modelchat.tools.diagram.UpdateDiagramTool;
import com.capellaagent.modelchat.tools.diagram.AddToDiagramTool;
import com.capellaagent.modelchat.tools.diagram.RemoveFromDiagramTool;
import com.capellaagent.modelchat.tools.diagram.HighlightElementTool;

/**
 * Registers all diagram tools with the core {@link ToolRegistry}.
 * Delegated from {@link ModelChatToolRegistrar#registerAll()}.
 */
final class DiagramToolRegistrar {

    private DiagramToolRegistrar() {}

    static void registerAll(ToolRegistry registry, ModelChatToolRegistrar parent) {
        parent.reg(registry, new UpdateDiagramTool());
        parent.reg(registry, new RefreshDiagramTool());
        parent.reg(registry, new CreateDiagramTool());
        parent.reg(registry, new ExportDiagramImageTool());
        parent.reg(registry, new ShowElementInDiagramTool());
        // P2 diagram tools
        parent.reg(registry, new ListDiagramElementsTool());
        parent.reg(registry, new CloneDiagramTool());
        parent.reg(registry, new AddToDiagramTool());
        parent.reg(registry, new RemoveFromDiagramTool());
        parent.reg(registry, new HighlightElementTool());
        // P3 diagram tools
        parent.reg(registry, new AutoLayoutDiagramTool());
        parent.reg(registry, new DeleteDiagramTool());
        parent.reg(registry, new GenerateDiagramTool());
    }
}

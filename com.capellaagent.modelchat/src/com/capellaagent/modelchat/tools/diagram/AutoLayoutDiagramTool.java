package com.capellaagent.modelchat.tools.diagram;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonObject;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.sirius.business.api.dialect.DialectManager;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.diagram.DDiagram;
// Note: LayoutUtils requires org.eclipse.sirius.diagram.ui (UI bundle)
// Auto-layout is implemented via DialectManager.refresh() instead
import org.eclipse.sirius.viewpoint.DRepresentation;
import org.eclipse.sirius.viewpoint.DRepresentationDescriptor;

/**
 * Triggers auto-layout (arrange all) on a diagram.
 */
public class AutoLayoutDiagramTool extends AbstractCapellaTool {

    public AutoLayoutDiagramTool() {
        super("auto_layout_diagram",
                "Triggers auto-layout (arrange all) on a diagram.",
                ToolCategory.DIAGRAM);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("diagram_uuid",
                "UUID of the diagram to layout"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String diagramUuid = getRequiredString(parameters, "diagram_uuid");

        try {
            Session session = getActiveSession();

            // Find diagram
            DDiagram diagram = null;
            Collection<DRepresentationDescriptor> descriptors =
                    DialectManager.INSTANCE.getAllRepresentationDescriptors(session);
            for (DRepresentationDescriptor desc : descriptors) {
                String uid = desc.getUid() != null ? desc.getUid().toString() : "";
                if (diagramUuid.equals(uid) || diagramUuid.equals(desc.getName())) {
                    DRepresentation rep = desc.getRepresentation();
                    if (rep instanceof DDiagram) {
                        diagram = (DDiagram) rep;
                        break;
                    }
                }
            }

            if (diagram == null) {
                return ToolResult.error("Diagram not found: " + diagramUuid);
            }

            // Refresh the diagram first, then trigger arrange-all
            final DDiagram finalDiagram = diagram;
            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Auto-layout diagram") {
                @Override
                protected void doExecute() {
                    DialectManager.INSTANCE.refresh(finalDiagram, new NullProgressMonitor());
                }
            });

            JsonObject response = new JsonObject();
            response.addProperty("status", "layout_applied");
            response.addProperty("diagram_name", diagram.getName());
            response.addProperty("element_count", diagram.getDiagramElements().size());
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to auto-layout diagram: " + e.getMessage());
        }
    }
}

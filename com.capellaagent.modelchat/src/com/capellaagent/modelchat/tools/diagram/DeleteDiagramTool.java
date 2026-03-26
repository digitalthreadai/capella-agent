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
import org.eclipse.sirius.viewpoint.DRepresentation;
import org.eclipse.sirius.viewpoint.DRepresentationDescriptor;

/**
 * Deletes a diagram from the model.
 */
public class DeleteDiagramTool extends AbstractCapellaTool {

    public DeleteDiagramTool() {
        super("delete_diagram",
                "Deletes a diagram from the model.",
                ToolCategory.DIAGRAM);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("diagram_uuid",
                "UUID of the diagram to delete"));
        params.add(ToolParameter.optionalBoolean("confirm",
                "Must be true to confirm deletion (safety check)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String diagramUuid = getRequiredString(parameters, "diagram_uuid");
        boolean confirm = getOptionalBoolean(parameters, "confirm", false);

        if (!confirm) {
            return ToolResult.error("Set confirm=true to delete the diagram. This cannot be undone easily.");
        }

        try {
            Session session = getActiveSession();

            DRepresentationDescriptor targetDesc = null;
            Collection<DRepresentationDescriptor> descriptors =
                    DialectManager.INSTANCE.getAllRepresentationDescriptors(session);
            for (DRepresentationDescriptor desc : descriptors) {
                String uid = desc.getUid() != null ? desc.getUid().toString() : "";
                if (diagramUuid.equals(uid) || diagramUuid.equals(desc.getName())) {
                    targetDesc = desc;
                    break;
                }
            }

            if (targetDesc == null) {
                return ToolResult.error("Diagram not found: " + diagramUuid);
            }

            String diagramName = targetDesc.getName();
            final DRepresentationDescriptor finalDesc = targetDesc;

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Delete diagram '" + diagramName + "'") {
                @Override
                protected void doExecute() {
                    DialectManager.INSTANCE.deleteRepresentation(
                            finalDesc, session);
                }
            });

            JsonObject response = new JsonObject();
            response.addProperty("status", "deleted");
            response.addProperty("diagram_name", diagramName);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to delete diagram: " + e.getMessage());
        }
    }
}

package com.capellaagent.modelchat.tools.diagram;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.capella.CapellaModelService;
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
import org.eclipse.sirius.viewpoint.DRepresentation;
import org.eclipse.sirius.viewpoint.DRepresentationDescriptor;

/**
 * Clones (copies) an existing diagram with a new name.
 */
public class CloneDiagramTool extends AbstractCapellaTool {

    public CloneDiagramTool() {
        super("clone_diagram",
                "Creates a copy of an existing diagram.",
                ToolCategory.DIAGRAM);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("diagram_uuid",
                "UUID of the diagram to clone"));
        params.add(ToolParameter.optionalString("new_name",
                "Name for the cloned diagram (default: original + ' (Copy)')"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String diagramUuid = getRequiredString(parameters, "diagram_uuid");
        String newName = getOptionalString(parameters, "new_name", null);

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            // Find the source diagram
            DRepresentationDescriptor sourceDesc = null;
            Collection<DRepresentationDescriptor> descriptors =
                    DialectManager.INSTANCE.getAllRepresentationDescriptors(session);
            for (DRepresentationDescriptor desc : descriptors) {
                String uid = desc.getUid() != null ? desc.getUid().toString() : "";
                if (diagramUuid.equals(uid) || diagramUuid.equals(desc.getName())) {
                    sourceDesc = desc;
                    break;
                }
            }

            if (sourceDesc == null) {
                return ToolResult.error("Diagram not found: " + diagramUuid);
            }

            DRepresentation sourceRep = sourceDesc.getRepresentation();
            if (sourceRep == null) {
                return ToolResult.error("Cannot access diagram representation");
            }

            String cloneName = (newName != null && !newName.isBlank())
                    ? newName : sourceDesc.getName() + " (Copy)";

            // Use Sirius API to copy the representation
            final DRepresentation[] clonedRep = new DRepresentation[1];
            final String finalName = cloneName;
            final DRepresentation finalSource = sourceRep;

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Clone diagram '" + sourceDesc.getName() + "'") {
                @Override
                protected void doExecute() {
                    clonedRep[0] = DialectManager.INSTANCE.copyRepresentation(
                            finalSource, finalName, session, new NullProgressMonitor());
                }
            });

            if (clonedRep[0] == null) {
                return ToolResult.error("Diagram cloning failed");
            }

            // Find the new descriptor
            String cloneUuid = "";
            Collection<DRepresentationDescriptor> allDescs =
                    DialectManager.INSTANCE.getAllRepresentationDescriptors(session);
            for (DRepresentationDescriptor desc : allDescs) {
                if (desc.getRepresentation() == clonedRep[0]) {
                    cloneUuid = desc.getUid() != null ? desc.getUid().toString() : "";
                    break;
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "cloned");
            response.addProperty("source_name", sourceDesc.getName());
            response.addProperty("clone_name", cloneName);
            response.addProperty("clone_uuid", cloneUuid);
            if (clonedRep[0] instanceof DDiagram) {
                response.addProperty("element_count",
                        ((DDiagram) clonedRep[0]).getDiagramElements().size());
            }
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to clone diagram: " + e.getMessage());
        }
    }
}

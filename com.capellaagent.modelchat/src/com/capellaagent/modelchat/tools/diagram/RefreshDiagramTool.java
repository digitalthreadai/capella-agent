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
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.sirius.business.api.dialect.DialectManager;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.diagram.DDiagram;
import org.eclipse.sirius.viewpoint.DRepresentation;
import org.eclipse.sirius.viewpoint.DRepresentationDescriptor;

/**
 * Forces a refresh/synchronization of a Sirius diagram with the underlying model.
 * <p>
 * After model changes (creating, updating, or deleting elements), diagrams may not
 * immediately reflect the new state. This tool triggers a full refresh of the specified
 * diagram, ensuring all diagram elements are synchronized with the semantic model.
 *
 * <h3>Tool Specification</h3>
 * <ul>
 *   <li><b>Name:</b> refresh_diagram</li>
 *   <li><b>Category:</b> diagram</li>
 *   <li><b>Parameters:</b>
 *     <ul>
 *       <li>{@code diagram_uuid} (string, required) - UUID of the diagram to refresh</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class RefreshDiagramTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "refresh_diagram";
    private static final String DESCRIPTION =
            "Forces a refresh of a Sirius diagram to synchronize it with the underlying model. "
            + "Use after model changes to ensure the diagram is up to date.";

    public RefreshDiagramTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.DIAGRAM);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("diagram_uuid",
                "UUID of the diagram to refresh"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String diagramUuid = getRequiredString(parameters, "diagram_uuid");

        if (diagramUuid.isBlank()) {
            return ToolResult.error("Parameter 'diagram_uuid' must not be empty");
        }

        try {
            Session session = getActiveSession();

            // Find the representation descriptor by UUID
            DRepresentationDescriptor descriptor = null;
            Collection<DRepresentationDescriptor> allDescriptors =
                    DialectManager.INSTANCE.getAllRepresentationDescriptors(session);

            for (DRepresentationDescriptor desc : allDescriptors) {
                String uid = desc.getUid() != null ? desc.getUid().toString() : "";
                if (diagramUuid.equals(uid)) {
                    descriptor = desc;
                    break;
                }
            }

            if (descriptor == null) {
                return ToolResult.error("Diagram not found with UUID: " + diagramUuid);
            }

            DRepresentation representation = descriptor.getRepresentation();
            if (representation == null) {
                return ToolResult.error("Could not load the diagram representation for UUID: " + diagramUuid);
            }

            if (!(representation instanceof DDiagram)) {
                return ToolResult.error("Representation is not a diagram (type: "
                        + representation.eClass().getName() + ")");
            }

            final String diagramName = descriptor.getName();
            final String diagramType = descriptor.getDescription() != null
                    ? descriptor.getDescription().getName() : "unknown";
            final DDiagram diagram = (DDiagram) representation;
            final int elementCountBefore = diagram.getDiagramElements().size();

            // Perform the refresh within a transaction
            // Use Display.syncExec if we are not on the UI thread
            final int[] elementCountAfter = new int[1];
            final boolean[] success = {false};

            TransactionalEditingDomain domain = getEditingDomain(session);

            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Refresh diagram '" + diagramName + "'") {
                @Override
                protected void doExecute() {
                    DialectManager.INSTANCE.refresh(representation, new NullProgressMonitor());
                    elementCountAfter[0] = diagram.getDiagramElements().size();
                    success[0] = true;
                }
            });

            if (!success[0]) {
                return ToolResult.error("Diagram refresh failed for UUID: " + diagramUuid);
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "refreshed");
            response.addProperty("diagram_uuid", diagramUuid);
            response.addProperty("diagram_name", diagramName);
            response.addProperty("diagram_type", diagramType);
            response.addProperty("element_count_before", elementCountBefore);
            response.addProperty("element_count_after", elementCountAfter[0]);
            response.addProperty("message", "Diagram refreshed and synchronized with model");

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to refresh diagram: " + e.getMessage());
        }
    }
}

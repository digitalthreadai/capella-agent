package com.capellaagent.modelchat.tools.diagram;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.sirius.business.api.dialect.DialectManager;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.diagram.DDiagram;
import org.eclipse.sirius.diagram.DDiagramElement;
import org.eclipse.sirius.viewpoint.DRepresentation;
import org.eclipse.sirius.viewpoint.DRepresentationDescriptor;
import org.eclipse.sirius.viewpoint.DSemanticDecorator;

/**
 * Removes an element from a diagram without deleting it from the model.
 * <p>
 * This hides the element's graphical representation in the specified diagram.
 * The model element itself remains intact. This is the inverse of
 * {@link AddToDiagramTool}.
 */
public class RemoveFromDiagramTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "remove_from_diagram";
    private static final String DESCRIPTION =
            "Removes an element from a diagram (hides it) without deleting it from the model.";

    public RemoveFromDiagramTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.DIAGRAM);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("diagram_uuid",
                "UUID of the diagram to remove the element from"));
        params.add(ToolParameter.requiredString("element_uuids",
                "Comma-separated UUIDs of model elements to remove from the diagram"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String diagramUuid = getRequiredString(parameters, "diagram_uuid");
        String elementUuidsStr = getRequiredString(parameters, "element_uuids");

        try {
            Session session = getActiveSession();

            // Find the diagram
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
                return ToolResult.error("Diagram not found: " + diagramUuid);
            }

            DRepresentation representation = descriptor.getRepresentation();
            if (!(representation instanceof DDiagram)) {
                return ToolResult.error("Representation is not a diagram");
            }

            DDiagram diagram = (DDiagram) representation;

            // Parse element UUIDs
            List<String> uuids = new ArrayList<>();
            for (String uid : elementUuidsStr.split(",")) {
                String trimmed = uid.trim();
                if (!trimmed.isEmpty()) {
                    uuids.add(trimmed);
                }
            }

            if (uuids.isEmpty()) {
                return ToolResult.error("No valid element UUIDs provided");
            }

            // Find diagram elements matching the specified model elements
            List<DDiagramElement> elementsToRemove = new ArrayList<>();
            JsonArray removedElements = new JsonArray();
            JsonArray notFound = new JsonArray();

            for (String uuid : uuids) {
                EObject modelElement = resolveElementByUuid(uuid);
                if (modelElement == null) {
                    notFound.add(uuid);
                    continue;
                }

                boolean found = false;
                for (DDiagramElement dElement : diagram.getDiagramElements()) {
                    if (dElement instanceof DSemanticDecorator) {
                        EObject target = ((DSemanticDecorator) dElement).getTarget();
                        if (target == modelElement) {
                            elementsToRemove.add(dElement);
                            found = true;

                            JsonObject entry = new JsonObject();
                            entry.addProperty("name", getElementName(modelElement));
                            entry.addProperty("id", uuid);
                            entry.addProperty("type", modelElement.eClass().getName());
                            removedElements.add(entry);
                            break;
                        }
                    }
                }

                if (!found) {
                    notFound.add(uuid);
                }
            }

            if (elementsToRemove.isEmpty()) {
                return ToolResult.error("None of the specified elements were found in the diagram");
            }

            int elementsBefore = diagram.getDiagramElements().size();

            // Remove diagram elements (not model elements)
            TransactionalEditingDomain domain = getEditingDomain(session);
            final List<DDiagramElement> toRemove = elementsToRemove;

            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Remove elements from diagram '" + descriptor.getName() + "'") {
                @Override
                protected void doExecute() {
                    for (DDiagramElement dElement : toRemove) {
                        // Remove only the diagram representation, not the semantic element
                        EcoreUtil.remove(dElement);
                    }
                }
            });

            int elementsAfter = diagram.getDiagramElements().size();

            JsonObject response = new JsonObject();
            response.addProperty("status", "removed");
            response.addProperty("diagram_name", descriptor.getName());
            response.addProperty("diagram_uuid", diagramUuid);
            response.addProperty("elements_before", elementsBefore);
            response.addProperty("elements_after", elementsAfter);
            response.addProperty("removed_count", removedElements.size());
            response.add("removed", removedElements);
            response.addProperty("note",
                    "Elements were removed from the diagram only. "
                    + "The model elements are still intact.");

            if (notFound.size() > 0) {
                response.add("not_found_in_diagram", notFound);
            }

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to remove from diagram: " + e.getMessage());
        }
    }
}

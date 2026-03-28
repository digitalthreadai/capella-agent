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

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.sirius.business.api.dialect.DialectManager;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.diagram.DDiagram;
import org.eclipse.sirius.diagram.DDiagramElement;
import org.eclipse.sirius.diagram.description.DiagramElementMapping;
import org.eclipse.sirius.viewpoint.DRepresentation;
import org.eclipse.sirius.viewpoint.DRepresentationDescriptor;
import org.eclipse.sirius.viewpoint.DSemanticDecorator;

/**
 * Adds an existing model element to a diagram representation.
 * <p>
 * This does not create a new model element; it creates a diagram node/edge
 * for an element that already exists in the model but is not yet visible
 * in the specified diagram. Uses Sirius DialectManager to refresh and
 * ensure the element appears.
 */
public class AddToDiagramTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "add_to_diagram";
    private static final String DESCRIPTION =
            "Adds an existing model element to a diagram (makes it visible).";

    public AddToDiagramTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.DIAGRAM);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("diagram_uuid",
                "UUID of the diagram to add the element to"));
        params.add(ToolParameter.requiredString("element_uuids",
                "Comma-separated UUIDs of model elements to add to the diagram"));
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
            List<EObject> elementsToAdd = new ArrayList<>();
            for (String uid : elementUuidsStr.split(",")) {
                String trimmed = uid.trim();
                if (trimmed.isEmpty()) continue;
                EObject element = resolveElementByUuid(trimmed);
                if (element == null) {
                    return ToolResult.error("Element not found: " + trimmed);
                }
                elementsToAdd.add(element);
            }

            if (elementsToAdd.isEmpty()) {
                return ToolResult.error("No valid elements specified");
            }

            // Track which elements were already present vs newly added
            JsonArray addedElements = new JsonArray();
            JsonArray alreadyPresent = new JsonArray();

            // Check which elements are already displayed
            for (EObject element : elementsToAdd) {
                boolean found = false;
                for (DDiagramElement dElement : diagram.getDiagramElements()) {
                    if (dElement instanceof DSemanticDecorator) {
                        EObject target = ((DSemanticDecorator) dElement).getTarget();
                        if (target == element) {
                            found = true;
                            break;
                        }
                    }
                }
                if (found) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("name", getElementName(element));
                    entry.addProperty("id", getElementId(element));
                    alreadyPresent.add(entry);
                }
            }

            int elementsBefore = diagram.getDiagramElements().size();

            // Refresh the diagram to ensure new elements appear
            // Sirius will automatically create diagram elements for model elements
            // that are in scope of the diagram's mapping definitions
            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Add elements to diagram '" + descriptor.getName() + "'") {
                @Override
                protected void doExecute() {
                    DialectManager.INSTANCE.refresh(diagram, new NullProgressMonitor());
                }
            });

            int elementsAfter = diagram.getDiagramElements().size();
            int newElementCount = elementsAfter - elementsBefore;

            // Report which elements now appear in the diagram
            for (EObject element : elementsToAdd) {
                for (DDiagramElement dElement : diagram.getDiagramElements()) {
                    if (dElement instanceof DSemanticDecorator) {
                        EObject target = ((DSemanticDecorator) dElement).getTarget();
                        if (target == element) {
                            boolean wasPresent = false;
                            for (int i = 0; i < alreadyPresent.size(); i++) {
                                if (alreadyPresent.get(i).getAsJsonObject()
                                        .get("id").getAsString().equals(getElementId(element))) {
                                    wasPresent = true;
                                    break;
                                }
                            }
                            if (!wasPresent) {
                                JsonObject entry = new JsonObject();
                                entry.addProperty("name", getElementName(element));
                                entry.addProperty("id", getElementId(element));
                                entry.addProperty("type", element.eClass().getName());
                                addedElements.add(entry);
                            }
                            break;
                        }
                    }
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "refreshed");
            response.addProperty("diagram_name", descriptor.getName());
            response.addProperty("diagram_uuid", diagramUuid);
            response.addProperty("elements_before", elementsBefore);
            response.addProperty("elements_after", elementsAfter);
            response.addProperty("new_elements_added", newElementCount);
            response.add("added", addedElements);
            response.add("already_present", alreadyPresent);

            if (addedElements.size() == 0 && newElementCount == 0) {
                response.addProperty("note",
                        "No new diagram elements were created. The elements may be outside "
                        + "the diagram's mapping scope or already visible.");
            }

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to add elements to diagram: " + e.getMessage());
        }
    }
}

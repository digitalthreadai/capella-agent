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
import org.eclipse.sirius.business.api.dialect.DialectManager;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.diagram.DDiagram;
import org.eclipse.sirius.diagram.DDiagramElement;
import org.eclipse.sirius.viewpoint.DRepresentation;
import org.eclipse.sirius.viewpoint.DRepresentationDescriptor;
import org.eclipse.sirius.viewpoint.DSemanticDecorator;

/**
 * Finds all diagrams that contain a specific model element.
 * <p>
 * Searches all diagram representations in the session and checks whether
 * the given element appears as a semantic target of any diagram element.
 */
public class ShowElementInDiagramTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "show_element_in_diagram";
    private static final String DESCRIPTION =
            "Finds all diagrams containing a given model element.";

    public ShowElementInDiagramTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.DIAGRAM);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("element_uuid",
                "UUID of the model element to find in diagrams"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String elementUuid = getRequiredString(parameters, "element_uuid");

        if (elementUuid.isBlank()) {
            return ToolResult.error("Parameter 'element_uuid' must not be empty");
        }

        try {
            Session session = getActiveSession();

            // Resolve the target element
            EObject element = resolveElementByUuid(elementUuid);
            if (element == null) {
                return ToolResult.error("Element not found with UUID: " + elementUuid);
            }

            JsonArray diagrams = new JsonArray();

            // Search all diagram representations
            Collection<DRepresentationDescriptor> allDescriptors =
                    DialectManager.INSTANCE.getAllRepresentationDescriptors(session);

            for (DRepresentationDescriptor desc : allDescriptors) {
                DRepresentation representation;
                try {
                    representation = desc.getRepresentation();
                } catch (Exception e) {
                    continue; // Some representations may not be loadable
                }

                if (representation == null || !(representation instanceof DDiagram)) {
                    continue;
                }

                DDiagram diagram = (DDiagram) representation;

                // Check if any diagram element has this element as its semantic target
                boolean found = false;
                int occurrenceCount = 0;

                try {
                    for (DDiagramElement diagramElement : diagram.getDiagramElements()) {
                        EObject target = null;
                        if (diagramElement instanceof DSemanticDecorator) {
                            target = ((DSemanticDecorator) diagramElement).getTarget();
                        }
                        if (target == element) {
                            found = true;
                            occurrenceCount++;
                        }
                    }
                } catch (Exception e) {
                    // Diagram element iteration may fail for unloaded diagrams
                    continue;
                }

                if (found) {
                    JsonObject diagramObj = new JsonObject();
                    diagramObj.addProperty("diagram_name", desc.getName());
                    diagramObj.addProperty("diagram_uuid",
                            desc.getUid() != null ? desc.getUid().toString() : "");
                    diagramObj.addProperty("diagram_type", desc.getDescription() != null
                            ? desc.getDescription().getName() : "unknown");
                    diagramObj.addProperty("occurrence_count", occurrenceCount);
                    diagramObj.addProperty("total_elements", diagram.getDiagramElements().size());
                    diagrams.add(diagramObj);
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("element_name", getElementName(element));
            response.addProperty("element_uuid", elementUuid);
            response.addProperty("element_type", element.eClass().getName());
            response.addProperty("diagram_count", diagrams.size());
            response.add("diagrams", diagrams);

            if (diagrams.size() == 0) {
                response.addProperty("message",
                        "Element does not appear in any diagram. "
                        + "Use create_diagram or update_diagram to add it to a diagram.");
            }

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to find element in diagrams: " + e.getMessage());
        }
    }
}

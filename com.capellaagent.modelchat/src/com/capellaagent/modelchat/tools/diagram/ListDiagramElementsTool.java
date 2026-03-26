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

/**
 * Lists the semantic elements shown in a specific diagram.
 */
public class ListDiagramElementsTool extends AbstractCapellaTool {

    public ListDiagramElementsTool() {
        super("list_diagram_elements",
                "Lists semantic elements displayed in a diagram.",
                ToolCategory.DIAGRAM);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("diagram_uuid",
                "UUID of the diagram"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String diagramUuid = getRequiredString(parameters, "diagram_uuid");

        try {
            Session session = getActiveSession();

            // Find the diagram by UUID
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

            JsonArray results = new JsonArray();
            for (DDiagramElement dElement : diagram.getDiagramElements()) {
                EObject target = dElement.getTarget();
                if (target == null) continue;

                JsonObject item = new JsonObject();
                item.addProperty("element_name", getElementName(target));
                item.addProperty("element_id", getElementId(target));
                item.addProperty("element_type", target.eClass().getName());
                item.addProperty("diagram_element_type", dElement.eClass().getName());
                item.addProperty("visible", dElement.isVisible());
                results.add(item);
            }

            JsonObject response = new JsonObject();
            response.addProperty("diagram_name", diagram.getName());
            response.addProperty("count", results.size());
            response.add("elements", results);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to list diagram elements: " + e.getMessage());
        }
    }
}

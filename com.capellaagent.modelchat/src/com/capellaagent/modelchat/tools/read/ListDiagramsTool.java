package com.capellaagent.modelchat.tools.read;

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

// PLACEHOLDER imports for Sirius and Capella diagram API
// import org.eclipse.sirius.business.api.dialect.DialectManager;
// import org.eclipse.sirius.business.api.session.Session;
// import org.eclipse.sirius.business.api.session.SessionManager;
// import org.eclipse.sirius.viewpoint.DRepresentationDescriptor;
// import org.eclipse.sirius.diagram.DDiagram;
// import org.eclipse.sirius.diagram.DDiagramElement;

/**
 * Lists all diagrams (Sirius representations) in the active Capella project.
 * <p>
 * Results can be filtered by ARCADIA layer and diagram type. Each entry includes
 * the diagram name, UUID, type, and the count of visible elements.
 *
 * <h3>Tool Specification</h3>
 * <ul>
 *   <li><b>Name:</b> list_diagrams</li>
 *   <li><b>Category:</b> model_read</li>
 *   <li><b>Parameters:</b>
 *     <ul>
 *       <li>{@code layer} (string, optional) - Filter by layer: oa, sa, la, pa</li>
 *       <li>{@code diagram_type} (string, optional) - Filter by diagram type
 *           (e.g., "[SDFB]", "[SAB]", "[LAB]", "[PAB]", "[OCB]", "[OAB]", etc.)</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class ListDiagramsTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "list_diagrams";
    private static final String DESCRIPTION =
            "Lists all diagrams (Sirius representations) in the project. "
            + "Optionally filters by architecture layer and diagram type. "
            + "Returns diagram name, UUID, type, and element count.";

    public ListDiagramsTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalString("layer",
                "Filter by architecture layer: oa, sa, la, pa"));
        params.add(ToolParameter.optionalString("diagram_type",
                "Filter by diagram type abbreviation (e.g., SDFB, SAB, LAB, PAB, OCB, OAB)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getOptionalString(parameters, "layer", null);
        String diagramType = getOptionalString(parameters, "diagram_type", null);

        try {
            // PLACEHOLDER: Sirius DialectManager API to retrieve representations
            //
            // Session session = getActiveSession();
            // Collection<DRepresentationDescriptor> allDescriptors =
            //     DialectManager.INSTANCE.getAllRepresentationDescriptors(session);
            //
            // For each descriptor:
            //   String name = descriptor.getName();
            //   String uid = descriptor.getUid().toString();
            //   String descriptionName = descriptor.getDescription().getName();
            //   // The description name typically contains the diagram type like
            //   // "[SAB] System Architecture Blank" or "[SDFB] System Data Flow Blank"
            //
            //   // To get element count, load the full representation:
            //   DRepresentation rep = descriptor.getRepresentation();
            //   if (rep instanceof DDiagram diagram) {
            //       int elementCount = diagram.getDiagramElements().size();
            //   }
            //
            //   // To filter by layer, check the target semantic element's architecture:
            //   EObject target = descriptor.getTarget();
            //   String elementLayer = detectLayer(target);

            JsonArray diagramsArray = new JsonArray();
            int totalCount = 0;

            // PLACEHOLDER: Iterate through Sirius representations
            // Collection<DRepresentationDescriptor> descriptors = getAllDescriptors();
            // for (DRepresentationDescriptor desc : descriptors) {
            //     String descLayer = detectLayer(desc.getTarget());
            //     String descType = extractDiagramType(desc.getDescription().getName());
            //
            //     // Apply filters
            //     if (layer != null && !layer.equalsIgnoreCase(descLayer)) continue;
            //     if (diagramType != null && !diagramType.equalsIgnoreCase(descType)) continue;
            //
            //     JsonObject diagramObj = new JsonObject();
            //     diagramObj.addProperty("name", desc.getName());
            //     diagramObj.addProperty("uuid", desc.getUid().toString());
            //     diagramObj.addProperty("type", desc.getDescription().getName());
            //     diagramObj.addProperty("layer", descLayer);
            //
            //     // Element count requires loading the representation
            //     DRepresentation rep = desc.getRepresentation();
            //     if (rep instanceof DDiagram dd) {
            //         diagramObj.addProperty("element_count", dd.getDiagramElements().size());
            //     }
            //
            //     diagramObj.addProperty("target_name", getElementName(desc.getTarget()));
            //     diagramObj.addProperty("target_uuid", getElementId(desc.getTarget()));
            //     diagramsArray.add(diagramObj);
            //     totalCount++;
            // }

            JsonObject response = new JsonObject();
            response.addProperty("count", totalCount);
            if (layer != null) {
                response.addProperty("layer_filter", layer);
            }
            if (diagramType != null) {
                response.addProperty("type_filter", diagramType);
            }
            response.add("diagrams", diagramsArray);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to list diagrams: " + e.getMessage());
        }
    }

    /**
     * Extracts the diagram type abbreviation from a Sirius description name.
     * <p>
     * For example, "[SAB] System Architecture Blank" returns "SAB".
     *
     * @param descriptionName the full description name
     * @return the type abbreviation, or the full name if no brackets found
     */
    @SuppressWarnings("unused")
    private String extractDiagramType(String descriptionName) {
        if (descriptionName != null && descriptionName.startsWith("[")) {
            int closeIdx = descriptionName.indexOf(']');
            if (closeIdx > 1) {
                return descriptionName.substring(1, closeIdx);
            }
        }
        return descriptionName;
    }
}

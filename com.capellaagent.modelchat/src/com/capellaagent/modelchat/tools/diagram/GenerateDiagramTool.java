package com.capellaagent.modelchat.tools.diagram;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.capella.CapellaModelService;
import com.capellaagent.core.security.InputValidator;
import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.sirius.business.api.dialect.DialectManager;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.diagram.DDiagram;
import org.eclipse.sirius.diagram.DDiagramElement;
import org.eclipse.sirius.viewpoint.DRepresentation;
import org.eclipse.sirius.viewpoint.DRepresentationDescriptor;
import org.eclipse.sirius.viewpoint.description.RepresentationDescription;
import org.eclipse.sirius.viewpoint.description.Viewpoint;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.fa.AbstractFunction;

/**
 * Auto-generates a diagram for a given scope by determining the appropriate
 * diagram type from the target element and populating it with all visible
 * elements in the scope.
 * <p>
 * Scopes supported:
 * <ul>
 *   <li><b>architecture</b> - Full architecture blank diagram (OAB/SAB/LAB/PAB)</li>
 *   <li><b>dataflow</b> - Data flow blank diagram (SDFB/LDFB/PDFB)</li>
 *   <li><b>component</b> - Diagram scoped to a specific component subtree</li>
 *   <li><b>function_tree</b> - Functional decomposition tree</li>
 * </ul>
 */
public class GenerateDiagramTool extends AbstractCapellaTool {

    private static final List<String> VALID_SCOPES = List.of(
            "architecture", "dataflow", "component", "function_tree");
    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public GenerateDiagramTool() {
        super("generate_diagram",
                "Auto-generates a diagram for a scope: architecture blank, dataflow, "
                + "component subtree, or function tree.",
                ToolCategory.DIAGRAM);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("scope",
                "Diagram scope: architecture (blank), dataflow, component, function_tree",
                VALID_SCOPES));
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS));
        params.add(ToolParameter.optionalString("target_uuid",
                "UUID of target element for component/function_tree scopes"));
        params.add(ToolParameter.optionalString("name",
                "Custom diagram name (auto-generated if omitted)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String scope = getRequiredString(parameters, "scope").toLowerCase();
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        String targetUuid = getOptionalString(parameters, "target_uuid", null);
        String customName = getOptionalString(parameters, "name", null);

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();
            BlockArchitecture arch = modelService.getArchitecture(session, layer);

            // Determine diagram type based on scope and layer
            String diagramType = determineDiagramType(scope, layer);
            if (diagramType == null) {
                return ToolResult.error("Cannot determine diagram type for scope '"
                        + scope + "' in layer '" + layer + "'");
            }

            // Determine target element
            EObject targetElement;
            if (targetUuid != null && !targetUuid.isBlank()) {
                try {
                    targetUuid = InputValidator.validateUuid(targetUuid);
                } catch (IllegalArgumentException e) {
                    return ToolResult.error("Invalid UUID: " + e.getMessage());
                }
                targetElement = resolveElementByUuid(targetUuid);
                if (targetElement == null) {
                    return ToolResult.error("Target element not found: " + targetUuid);
                }
            } else {
                targetElement = arch;
            }

            // Find matching representation description
            RepresentationDescription matchingDesc = findRepresentationDescription(
                    session, diagramType, targetElement);
            if (matchingDesc == null) {
                return ToolResult.error("No diagram description found for type '"
                        + diagramType + "' in layer '" + layer + "'");
            }

            // Check applicability
            boolean canCreate = DialectManager.INSTANCE.canCreate(targetElement, matchingDesc);
            if (!canCreate) {
                return ToolResult.error("Cannot create '" + diagramType
                        + "' on '" + getElementName(targetElement) + "'");
            }

            // Generate diagram name
            String diagramName = customName != null ? customName
                    : "[" + diagramType + "] " + getElementName(targetElement) + " (Generated)";

            final DRepresentation[] createdRep = new DRepresentation[1];
            final RepresentationDescription finalDesc = matchingDesc;

            TransactionalEditingDomain domain = getEditingDomain(session);

            // Create the diagram
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Generate diagram '" + diagramName + "'") {
                @Override
                protected void doExecute() {
                    createdRep[0] = DialectManager.INSTANCE.createRepresentation(
                            diagramName, targetElement, finalDesc, session,
                            new NullProgressMonitor());
                }
            });

            if (createdRep[0] == null) {
                return ToolResult.error("Diagram generation failed");
            }

            // Refresh to populate with all visible elements
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Refresh generated diagram") {
                @Override
                protected void doExecute() {
                    DialectManager.INSTANCE.refresh(createdRep[0],
                            new NullProgressMonitor());

                    // DialectManager.refresh above handles full synchronization
                }
            });

            // Build response
            JsonObject response = new JsonObject();
            response.addProperty("status", "generated");
            response.addProperty("diagram_name", diagramName);
            response.addProperty("diagram_type", diagramType);
            response.addProperty("scope", scope);
            response.addProperty("layer", layer);
            response.addProperty("target_element", getElementName(targetElement));

            if (createdRep[0] instanceof DDiagram) {
                DDiagram diagram = (DDiagram) createdRep[0];
                response.addProperty("element_count", diagram.getDiagramElements().size());

                // Summarize element types in diagram
                int componentCount = 0, functionCount = 0, exchangeCount = 0, otherCount = 0;
                for (DDiagramElement de : diagram.getDiagramElements()) {
                    EObject semantic = de.getTarget();
                    if (semantic instanceof Component) componentCount++;
                    else if (semantic instanceof AbstractFunction) functionCount++;
                    else if (semantic instanceof org.polarsys.capella.core.data.fa.FunctionalExchange
                            || semantic instanceof org.polarsys.capella.core.data.fa.ComponentExchange)
                        exchangeCount++;
                    else otherCount++;
                }

                JsonObject elementSummary = new JsonObject();
                elementSummary.addProperty("components", componentCount);
                elementSummary.addProperty("functions", functionCount);
                elementSummary.addProperty("exchanges", exchangeCount);
                elementSummary.addProperty("other", otherCount);
                response.add("element_summary", elementSummary);
            }

            // Get diagram UUID from descriptor
            Collection<DRepresentationDescriptor> allDescs =
                    DialectManager.INSTANCE.getAllRepresentationDescriptors(session);
            for (DRepresentationDescriptor desc : allDescs) {
                if (desc.getRepresentation() == createdRep[0]) {
                    response.addProperty("diagram_uuid",
                            desc.getUid() != null ? desc.getUid().toString() : "");
                    break;
                }
            }

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to generate diagram: " + e.getMessage());
        }
    }

    /**
     * Maps scope + layer to the appropriate Capella diagram type abbreviation.
     */
    private String determineDiagramType(String scope, String layer) {
        switch (scope) {
            case "architecture":
                switch (layer) {
                    case "oa": return "OAB";
                    case "sa": return "SAB";
                    case "la": return "LAB";
                    case "pa": return "PAB";
                }
                break;
            case "dataflow":
                switch (layer) {
                    case "oa": return "OAIB";
                    case "sa": return "SDFB";
                    case "la": return "LDFB";
                    case "pa": return "PDFB";
                }
                break;
            case "component":
                switch (layer) {
                    case "oa": return "OAB";
                    case "sa": return "SAB";
                    case "la": return "LAB";
                    case "pa": return "PAB";
                }
                break;
            case "function_tree":
                switch (layer) {
                    case "oa": return "OAIB";
                    case "sa": return "SDFB";
                    case "la": return "LDFB";
                    case "pa": return "PDFB";
                }
                break;
        }
        return null;
    }

    /**
     * Finds a RepresentationDescription matching the given diagram type abbreviation.
     */
    private RepresentationDescription findRepresentationDescription(
            Session session, String diagramType, EObject target) {

        // Search selected viewpoints
        Collection<Viewpoint> viewpoints = session.getSelectedViewpoints(false);
        for (Viewpoint vp : viewpoints) {
            for (RepresentationDescription desc : vp.getOwnedRepresentations()) {
                String descName = desc.getName();
                if (descName != null && descName.contains("[" + diagramType + "]")) {
                    return desc;
                }
            }
        }

        // Fallback: search available descriptions for target
        Collection<RepresentationDescription> available =
                DialectManager.INSTANCE.getAvailableRepresentationDescriptions(
                        viewpoints, target);
        for (RepresentationDescription desc : available) {
            String descName = desc.getName();
            if (descName != null && (descName.contains(diagramType)
                    || descName.contains("[" + diagramType + "]"))) {
                return desc;
            }
        }

        return null;
    }
}

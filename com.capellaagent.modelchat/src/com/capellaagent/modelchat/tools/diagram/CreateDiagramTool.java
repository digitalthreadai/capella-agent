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
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.sirius.business.api.dialect.DialectManager;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.diagram.DDiagram;
import org.eclipse.sirius.viewpoint.DRepresentation;
import org.eclipse.sirius.viewpoint.DRepresentationDescriptor;
import org.eclipse.sirius.viewpoint.description.RepresentationDescription;
import org.eclipse.sirius.viewpoint.description.Viewpoint;
import org.polarsys.capella.core.data.cs.BlockArchitecture;

/**
 * Creates a new Sirius diagram in the specified architecture layer.
 * <p>
 * Searches for the matching viewpoint description name and creates a new
 * representation. Supports standard Capella diagram types: PAB, LAB, SAB, OAB,
 * OAIB, SDFB, LDFB, PDFB, etc.
 */
public class CreateDiagramTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "create_diagram";
    private static final String DESCRIPTION =
            "Creates a new Sirius diagram (PAB, LAB, SAB, OAB, etc.) in a layer.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");
    private static final List<String> VALID_DIAGRAM_TYPES = List.of(
            "OAB", "OAIB", "SAB", "SDFB", "LAB", "LDFB", "PAB", "PDFB");

    public CreateDiagramTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.DIAGRAM);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS));
        params.add(ToolParameter.requiredEnum("diagram_type",
                "Diagram type: OAB, OAIB, SAB, SDFB, LAB, LDFB, PAB, PDFB",
                VALID_DIAGRAM_TYPES));
        params.add(ToolParameter.optionalString("target_uuid",
                "UUID of the element to scope the diagram to (default: root architecture)"));
        params.add(ToolParameter.optionalString("name",
                "Custom name for the diagram (auto-generated if omitted)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        String diagramType = getRequiredString(parameters, "diagram_type").toUpperCase();
        String targetUuid = getOptionalString(parameters, "target_uuid", null);
        String customName = getOptionalString(parameters, "name", null);

        if (!VALID_LAYERS.contains(layer)) {
            return ToolResult.error("Invalid layer: " + layer);
        }

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();
            BlockArchitecture architecture = modelService.getArchitecture(session, layer);

            // Determine the target semantic element
            EObject targetElement;
            if (targetUuid != null && !targetUuid.isBlank()) {
                targetElement = resolveElementByUuid(targetUuid);
                if (targetElement == null) {
                    return ToolResult.error("Target element not found: " + targetUuid);
                }
            } else {
                targetElement = architecture;
            }

            // Find the matching representation description
            // Capella diagram description names follow patterns like:
            // "[PAB] Physical Architecture Blank", "[LAB] Logical Architecture Blank", etc.
            RepresentationDescription matchingDesc = null;
            Collection<Viewpoint> viewpoints = session.getSelectedViewpoints(false);
            for (Viewpoint vp : viewpoints) {
                for (RepresentationDescription desc : vp.getOwnedRepresentations()) {
                    String descName = desc.getName();
                    if (descName != null && descName.contains("[" + diagramType + "]")) {
                        matchingDesc = desc;
                        break;
                    }
                }
                if (matchingDesc != null) break;
            }

            if (matchingDesc == null) {
                // Fallback: search all representation descriptors for partial match
                Collection<RepresentationDescription> allDescs =
                        DialectManager.INSTANCE.getAvailableRepresentationDescriptions(
                                session.getSelectedViewpoints(false), targetElement);
                for (RepresentationDescription desc : allDescs) {
                    String descName = desc.getName();
                    if (descName != null && (descName.contains(diagramType)
                            || descName.contains("[" + diagramType + "]"))) {
                        matchingDesc = desc;
                        break;
                    }
                }
            }

            if (matchingDesc == null) {
                return ToolResult.error("No diagram description found for type '" + diagramType
                        + "' in layer '" + layer + "'. Available viewpoints may not include this diagram type.");
            }

            // Check if the description can be applied to the target element
            boolean canCreate = DialectManager.INSTANCE.canCreate(targetElement, matchingDesc);
            if (!canCreate) {
                return ToolResult.error("Cannot create diagram type '" + diagramType
                        + "' on element '" + getElementName(targetElement)
                        + "' (type: " + targetElement.eClass().getName() + ")");
            }

            // Generate diagram name
            String diagramName = customName != null ? customName
                    : "[" + diagramType + "] " + getElementName(targetElement);

            final RepresentationDescription finalDesc = matchingDesc;
            final String finalName = diagramName;
            final DRepresentation[] createdRep = new DRepresentation[1];

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Create diagram '" + diagramName + "'") {
                @Override
                protected void doExecute() {
                    createdRep[0] = DialectManager.INSTANCE.createRepresentation(
                            finalName, targetElement, finalDesc, session, new NullProgressMonitor());
                }
            });

            if (createdRep[0] == null) {
                return ToolResult.error("Diagram creation failed - no representation produced");
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "created");
            response.addProperty("diagram_name", diagramName);
            response.addProperty("diagram_type", diagramType);
            response.addProperty("layer", layer);
            response.addProperty("target_element", getElementName(targetElement));

            // Try to get the diagram UUID
            if (createdRep[0] instanceof DDiagram) {
                DDiagram diagram = (DDiagram) createdRep[0];
                response.addProperty("element_count", diagram.getDiagramElements().size());
            }

            // Find the descriptor to get the UUID
            Collection<DRepresentationDescriptor> allDescriptors =
                    DialectManager.INSTANCE.getAllRepresentationDescriptors(session);
            for (DRepresentationDescriptor desc : allDescriptors) {
                if (desc.getRepresentation() == createdRep[0]) {
                    response.addProperty("diagram_uuid",
                            desc.getUid() != null ? desc.getUid().toString() : "");
                    break;
                }
            }

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to create diagram: " + e.getMessage());
        }
    }
}

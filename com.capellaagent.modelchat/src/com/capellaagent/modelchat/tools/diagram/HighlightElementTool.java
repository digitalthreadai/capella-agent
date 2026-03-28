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
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.sirius.business.api.dialect.DialectManager;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.diagram.DDiagram;
import org.eclipse.sirius.diagram.DDiagramElement;
import org.eclipse.sirius.diagram.DNode;
import org.eclipse.sirius.diagram.DNodeContainer;
import org.eclipse.sirius.diagram.DEdge;
import org.eclipse.sirius.diagram.Square;
import org.eclipse.sirius.diagram.FlatContainerStyle;
import org.eclipse.sirius.diagram.EdgeStyle;
import org.eclipse.sirius.viewpoint.DRepresentation;
import org.eclipse.sirius.viewpoint.DRepresentationDescriptor;
import org.eclipse.sirius.viewpoint.DSemanticDecorator;
import org.eclipse.sirius.viewpoint.RGBValues;

/**
 * Applies color/style highlighting to elements in a diagram.
 * <p>
 * Changes the visual appearance of diagram elements by modifying their
 * Sirius style properties (background color, border color, font color).
 * This is useful for visually marking elements of interest during reviews.
 */
public class HighlightElementTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "highlight_element";
    private static final String DESCRIPTION =
            "Applies color highlighting to elements in a diagram for visual emphasis.";

    private static final List<String> VALID_COLORS = List.of(
            "red", "green", "blue", "yellow", "orange", "purple", "cyan", "pink");

    public HighlightElementTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.DIAGRAM);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("diagram_uuid",
                "UUID of the diagram containing the elements"));
        params.add(ToolParameter.requiredString("element_uuids",
                "Comma-separated UUIDs of model elements to highlight"));
        params.add(ToolParameter.optionalEnum("color",
                "Highlight color: red, green, blue, yellow, orange, purple, cyan, pink (default: yellow)",
                VALID_COLORS, "yellow"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String diagramUuid = getRequiredString(parameters, "diagram_uuid");
        String elementUuidsStr = getRequiredString(parameters, "element_uuids");
        String color = getOptionalString(parameters, "color", "yellow").toLowerCase();

        if (!VALID_COLORS.contains(color)) {
            return ToolResult.error("Invalid color '" + color
                    + "'. Must be one of: " + String.join(", ", VALID_COLORS));
        }

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

            // Parse UUIDs
            List<String> uuids = new ArrayList<>();
            for (String uid : elementUuidsStr.split(",")) {
                String trimmed = uid.trim();
                if (!trimmed.isEmpty()) uuids.add(trimmed);
            }

            // Resolve RGB values for the color
            RGBValues rgbColor = resolveColor(color);

            // Find matching diagram elements and apply highlighting
            JsonArray highlighted = new JsonArray();
            JsonArray notFound = new JsonArray();

            TransactionalEditingDomain domain = getEditingDomain(session);

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
                            final DDiagramElement diagramElement = dElement;
                            domain.getCommandStack().execute(new RecordingCommand(domain,
                                    "Highlight '" + getElementName(modelElement) + "'") {
                                @Override
                                protected void doExecute() {
                                    applyHighlight(diagramElement, rgbColor);
                                }
                            });

                            JsonObject entry = new JsonObject();
                            entry.addProperty("name", getElementName(modelElement));
                            entry.addProperty("id", uuid);
                            entry.addProperty("type", modelElement.eClass().getName());
                            entry.addProperty("color", color);
                            highlighted.add(entry);
                            found = true;
                            break;
                        }
                    }
                }

                if (!found) {
                    notFound.add(uuid);
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "highlighted");
            response.addProperty("diagram_name", descriptor.getName());
            response.addProperty("diagram_uuid", diagramUuid);
            response.addProperty("color", color);
            response.addProperty("highlighted_count", highlighted.size());
            response.add("highlighted", highlighted);

            if (notFound.size() > 0) {
                response.add("not_found_in_diagram", notFound);
            }

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to highlight elements: " + e.getMessage());
        }
    }

    /**
     * Applies color highlighting to a diagram element based on its style type.
     */
    private void applyHighlight(DDiagramElement element, RGBValues color) {
        if (element instanceof DNode) {
            DNode node = (DNode) element;
            if (node.getOwnedStyle() instanceof Square) {
                Square square = (Square) node.getOwnedStyle();
                square.setColor(color);
            } else if (node.getOwnedStyle() != null) {
                // Generic node style - set border color
                node.getOwnedStyle().setBorderColor(color);
            }
        } else if (element instanceof DNodeContainer) {
            DNodeContainer container = (DNodeContainer) element;
            if (container.getOwnedStyle() instanceof FlatContainerStyle) {
                FlatContainerStyle style = (FlatContainerStyle) container.getOwnedStyle();
                style.setBackgroundColor(color);
            }
        } else if (element instanceof DEdge) {
            DEdge edge = (DEdge) element;
            if (edge.getOwnedStyle() instanceof EdgeStyle) {
                EdgeStyle edgeStyle = (EdgeStyle) edge.getOwnedStyle();
                edgeStyle.setStrokeColor(color);
            }
        }
    }

    /**
     * Maps a color name to Sirius RGBValues.
     */
    private RGBValues resolveColor(String colorName) {
        switch (colorName) {
            case "red":     return RGBValues.create(255, 0, 0);
            case "green":   return RGBValues.create(0, 200, 0);
            case "blue":    return RGBValues.create(0, 0, 255);
            case "yellow":  return RGBValues.create(255, 255, 0);
            case "orange":  return RGBValues.create(255, 165, 0);
            case "purple":  return RGBValues.create(128, 0, 128);
            case "cyan":    return RGBValues.create(0, 255, 255);
            case "pink":    return RGBValues.create(255, 182, 193);
            default:        return RGBValues.create(255, 255, 0); // default yellow
        }
    }
}

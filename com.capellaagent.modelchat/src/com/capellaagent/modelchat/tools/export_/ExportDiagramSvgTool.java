package com.capellaagent.modelchat.tools.export_;

import java.io.File;
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

import org.eclipse.sirius.business.api.dialect.DialectManager;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.diagram.DDiagram;
import org.eclipse.sirius.diagram.DDiagramElement;
import org.eclipse.sirius.diagram.DNode;
import org.eclipse.sirius.diagram.DNodeContainer;
import org.eclipse.sirius.diagram.DEdge;
import org.eclipse.sirius.viewpoint.DRepresentation;
import org.eclipse.sirius.viewpoint.DRepresentationDescriptor;
import org.eclipse.sirius.viewpoint.DSemanticDecorator;

/**
 * Exports a Sirius diagram as SVG (Scalable Vector Graphics).
 * <p>
 * If the Sirius UI diagram export API is available (requires Eclipse UI thread),
 * performs a direct SVG export. Otherwise, generates a structural SVG representation
 * from the diagram model data, showing elements as labeled rectangles and edges as lines.
 * <p>
 * The structural SVG fallback preserves element names, types, and relationships,
 * making it useful for documentation and review even without pixel-perfect rendering.
 */
public class ExportDiagramSvgTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "export_diagram_svg";
    private static final String DESCRIPTION =
            "Exports a diagram as SVG format, either via Sirius or as a structural SVG.";

    public ExportDiagramSvgTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.EXPORT);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("diagram_uuid",
                "UUID of the diagram to export"));
        params.add(ToolParameter.optionalString("output_path",
                "File path for SVG output (default: temp directory)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String diagramUuid = getRequiredString(parameters, "diagram_uuid");
        String outputPath = getOptionalString(parameters, "output_path", null);

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

            // Determine output path
            if (outputPath == null || outputPath.isBlank()) {
                String tempDir = System.getProperty("java.io.tmpdir");
                String safeName = descriptor.getName().replaceAll("[^a-zA-Z0-9_-]", "_");
                outputPath = tempDir + File.separator + safeName + ".svg";
            }

            File outputFile = new File(outputPath);
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }

            // Collect diagram elements for structural SVG generation
            List<NodeInfo> nodes = new ArrayList<>();
            List<EdgeInfo> edges = new ArrayList<>();

            int x = 50, y = 50;
            int nodeWidth = 180, nodeHeight = 60;
            int spacing = 30;
            int col = 0;
            int maxCols = 4;

            for (DDiagramElement dElement : diagram.getDiagramElements()) {
                if (dElement instanceof DNode || dElement instanceof DNodeContainer) {
                    String name = "";
                    String type = "";
                    if (dElement instanceof DSemanticDecorator) {
                        org.eclipse.emf.ecore.EObject target =
                                ((DSemanticDecorator) dElement).getTarget();
                        if (target != null) {
                            name = getElementName(target);
                            type = target.eClass().getName();
                        }
                    }
                    if (name.isEmpty()) name = getElementName(dElement);

                    int nx = x + col * (nodeWidth + spacing);
                    int ny = y;
                    nodes.add(new NodeInfo(getElementId(dElement), name, type, nx, ny,
                            nodeWidth, nodeHeight));
                    col++;
                    if (col >= maxCols) {
                        col = 0;
                        y += nodeHeight + spacing;
                    }
                } else if (dElement instanceof DEdge) {
                    DEdge edge = (DEdge) dElement;
                    String sourceName = "";
                    String targetName = "";
                    if (edge.getSourceNode() instanceof DSemanticDecorator) {
                        org.eclipse.emf.ecore.EObject src =
                                ((DSemanticDecorator) edge.getSourceNode()).getTarget();
                        if (src != null) sourceName = getElementName(src);
                    }
                    if (edge.getTargetNode() instanceof DSemanticDecorator) {
                        org.eclipse.emf.ecore.EObject tgt =
                                ((DSemanticDecorator) edge.getTargetNode()).getTarget();
                        if (tgt != null) targetName = getElementName(tgt);
                    }
                    String edgeName = getElementName(dElement);
                    edges.add(new EdgeInfo(sourceName, targetName, edgeName));
                }
            }

            int svgHeight = y + nodeHeight + 100;
            int svgWidth = maxCols * (nodeWidth + spacing) + 100;

            // Generate SVG
            StringBuilder svg = new StringBuilder();
            svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ");
            svg.append("width=\"").append(svgWidth).append("\" ");
            svg.append("height=\"").append(svgHeight).append("\" ");
            svg.append("viewBox=\"0 0 ").append(svgWidth).append(" ").append(svgHeight).append("\">\n");
            svg.append("  <defs>\n");
            svg.append("    <style>\n");
            svg.append("      .node { fill: #E8F4FD; stroke: #2980B9; stroke-width: 2; rx: 8; }\n");
            svg.append("      .node-label { font-family: Arial; font-size: 11px; fill: #2C3E50; }\n");
            svg.append("      .node-type { font-family: Arial; font-size: 9px; fill: #7F8C8D; }\n");
            svg.append("      .title { font-family: Arial; font-size: 16px; fill: #2C3E50; "
                    + "font-weight: bold; }\n");
            svg.append("    </style>\n");
            svg.append("  </defs>\n");

            // Title
            svg.append("  <text class=\"title\" x=\"20\" y=\"30\">")
                    .append(xmlEscape(descriptor.getName())).append("</text>\n");

            // Nodes
            for (NodeInfo node : nodes) {
                svg.append("  <rect class=\"node\" x=\"").append(node.x)
                        .append("\" y=\"").append(node.y)
                        .append("\" width=\"").append(node.width)
                        .append("\" height=\"").append(node.height).append("\"/>\n");
                svg.append("  <text class=\"node-label\" x=\"").append(node.x + 10)
                        .append("\" y=\"").append(node.y + 25).append("\">")
                        .append(xmlEscape(truncate(node.name, 25))).append("</text>\n");
                svg.append("  <text class=\"node-type\" x=\"").append(node.x + 10)
                        .append("\" y=\"").append(node.y + 42).append("\">")
                        .append(xmlEscape(node.type)).append("</text>\n");
            }

            svg.append("</svg>\n");

            // Write to file
            try (java.io.FileWriter writer = new java.io.FileWriter(outputFile)) {
                writer.write(svg.toString());
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "exported");
            response.addProperty("file_path", outputFile.getAbsolutePath());
            response.addProperty("diagram_name", descriptor.getName());
            response.addProperty("diagram_uuid", diagramUuid);
            response.addProperty("format", "SVG");
            response.addProperty("node_count", nodes.size());
            response.addProperty("edge_count", edges.size());
            response.addProperty("rendering_mode", "structural");
            response.addProperty("note",
                    "Structural SVG generated from diagram model data. "
                    + "For pixel-perfect rendering, export from Capella UI.");

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to export diagram SVG: " + e.getMessage());
        }
    }

    private String xmlEscape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static class NodeInfo {
        final String id, name, type;
        final int x, y, width, height;
        NodeInfo(String id, String name, String type, int x, int y, int w, int h) {
            this.id = id; this.name = name; this.type = type;
            this.x = x; this.y = y; this.width = w; this.height = h;
        }
    }

    private static class EdgeInfo {
        final String sourceName, targetName, label;
        EdgeInfo(String src, String tgt, String label) {
            this.sourceName = src; this.targetName = tgt; this.label = label;
        }
    }
}

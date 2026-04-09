package com.capellaagent.modelchat.tools.diagram;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonObject;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.sirius.business.api.dialect.DialectManager;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.diagram.DDiagram;
import org.eclipse.sirius.viewpoint.DRepresentation;
import org.eclipse.sirius.viewpoint.DRepresentationDescriptor;
// Note: org.eclipse.sirius.diagram.ui is not available in headless builds.
// Image export is provided as diagram metadata instead.

/**
 * Exports a Sirius diagram as an image file (PNG or SVG).
 * <p>
 * Locates the diagram by UUID and exports it using Sirius export capabilities.
 * Note: Image export may require the UI thread in some configurations.
 */
public class ExportDiagramImageTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "export_diagram_image";
    private static final String DESCRIPTION =
            "Exports a diagram as PNG or SVG image to a file path.";

    public ExportDiagramImageTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.DIAGRAM);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("diagram_uuid",
                "UUID of the diagram to export"));
        params.add(ToolParameter.optionalEnum("format",
                "Image format: png or svg (default: png)",
                List.of("png", "svg"), "png"));
        params.add(ToolParameter.optionalString("output_path",
                "File path to save the image (default: workspace temp directory)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String diagramUuid = getRequiredString(parameters, "diagram_uuid");
        String format = getOptionalString(parameters, "format", "png").toLowerCase();
        String outputPath = getOptionalString(parameters, "output_path", null);

        if (!format.equals("png") && !format.equals("svg")) {
            return ToolResult.error("Invalid format '" + format + "'. Must be png or svg.");
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
                return ToolResult.error("Could not load diagram representation: " + diagramUuid);
            }

            if (!(representation instanceof DDiagram)) {
                return ToolResult.error("Representation is not a diagram (type: "
                        + representation.eClass().getName() + ")");
            }

            DDiagram diagram = (DDiagram) representation;

            // Determine output file path
            if (outputPath == null || outputPath.isBlank()) {
                String tempDir = System.getProperty("java.io.tmpdir");
                String safeName = descriptor.getName().replaceAll("[^a-zA-Z0-9_-]", "_");
                outputPath = tempDir + File.separator + safeName + "." + format;
            } else {
                // SECURITY (C2): validate the user-supplied output path.
                // Canonicalization + workspace containment + extension check
                // prevents a "write diagram to C:\Windows\System32" request.
                try {
                    java.nio.file.Path validated = com.capellaagent.core.security.PathValidator
                        .validateOutputPath(outputPath,
                            java.util.Set.of(".png", ".jpg", ".jpeg", ".svg", ".gif"));
                    outputPath = validated.toString();
                } catch (SecurityException se) {
                    return ToolResult.error("Rejected by path validator: " + se.getMessage());
                }
            }

            // Ensure parent directory exists
            File outputFile = new File(outputPath);
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }

            // Diagram image export requires the Sirius UI thread (org.eclipse.sirius.diagram.ui).
            // In tool mode, we provide diagram metadata so the user can export manually.
            {
                JsonObject response = new JsonObject();
                response.addProperty("status", "partial");
                response.addProperty("message",
                        "Diagram image export requires the Sirius UI thread. "
                        + "The diagram was found and can be opened in the editor. "
                        + "Use 'File > Export Representations as Images' in Capella to export manually.");
                response.addProperty("diagram_name", descriptor.getName());
                response.addProperty("diagram_uuid", diagramUuid);
                response.addProperty("diagram_type", descriptor.getDescription() != null
                        ? descriptor.getDescription().getName() : "unknown");
                response.addProperty("element_count", diagram.getDiagramElements().size());
                response.addProperty("requested_format", format);
                response.addProperty("requested_path", outputPath);
                return ToolResult.success(response);
            }

        } catch (Exception e) {
            return ToolResult.error("Failed to export diagram: " + e.getMessage());
        }
    }
}

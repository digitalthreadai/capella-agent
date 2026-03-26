package com.capellaagent.modelchat.tools.export_;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.capella.CapellaModelService;
import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;
import org.polarsys.capella.core.data.cs.BlockArchitecture;

/**
 * Exports model elements as a nested JSON structure.
 * <p>
 * Can export an entire layer or a subtree rooted at a specific element.
 * Respects a max_depth parameter to control the depth of traversal.
 */
public class ExportToJsonTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "export_to_json";
    private static final String DESCRIPTION =
            "Exports model elements as nested JSON structure.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");
    private static final int MAX_ELEMENTS = 2000;

    public ExportToJsonTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.EXPORT);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalString("element_uuid",
                "UUID of root element to export (exports subtree)"));
        params.add(ToolParameter.optionalEnum("layer",
                "Architecture layer to export (used if element_uuid not provided)",
                VALID_LAYERS, null));
        params.add(ToolParameter.optionalInteger("max_depth",
                "Maximum depth of traversal (default: 3, max: 10)"));
        params.add(ToolParameter.optionalString("output_path",
                "File path to save JSON (returns inline if omitted for small models)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String elementUuid = getOptionalString(parameters, "element_uuid", null);
        String layer = getOptionalString(parameters, "layer", null);
        int maxDepth = Math.max(1, Math.min(getOptionalInt(parameters, "max_depth", 3), 10));
        String outputPath = getOptionalString(parameters, "output_path", null);

        if ((elementUuid == null || elementUuid.isBlank())
                && (layer == null || layer.isBlank())) {
            return ToolResult.error("Either 'element_uuid' or 'layer' must be provided");
        }

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();

            EObject root;
            if (elementUuid != null && !elementUuid.isBlank()) {
                root = resolveElementByUuid(elementUuid);
                if (root == null) {
                    return ToolResult.error("Element not found: " + elementUuid);
                }
            } else {
                root = modelService.getArchitecture(session, layer.toLowerCase());
            }

            // Build JSON tree
            int[] elementCount = {0};
            JsonObject tree = buildJsonTree(root, 0, maxDepth, elementCount, modelService);

            // If output_path is specified, write to file
            if (outputPath != null && !outputPath.isBlank()) {
                File outputFile = new File(outputPath);
                if (outputFile.getParentFile() != null) {
                    outputFile.getParentFile().mkdirs();
                }

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                try (FileWriter writer = new FileWriter(outputFile)) {
                    gson.toJson(tree, writer);
                }

                JsonObject response = new JsonObject();
                response.addProperty("status", "exported");
                response.addProperty("file_path", outputFile.getAbsolutePath());
                response.addProperty("element_count", elementCount[0]);
                response.addProperty("max_depth", maxDepth);
                return ToolResult.success(response);
            }

            // Return inline if element count is reasonable
            if (elementCount[0] <= 500) {
                JsonObject response = new JsonObject();
                response.addProperty("element_count", elementCount[0]);
                response.addProperty("max_depth", maxDepth);
                response.add("model", tree);
                return ToolResult.success(response);
            } else {
                // Too large for inline - save to temp file
                String tempDir = System.getProperty("java.io.tmpdir");
                String tempPath = tempDir + File.separator + "capella_export.json";
                File tempFile = new File(tempPath);

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                try (FileWriter writer = new FileWriter(tempFile)) {
                    gson.toJson(tree, writer);
                }

                JsonObject response = new JsonObject();
                response.addProperty("status", "exported");
                response.addProperty("file_path", tempFile.getAbsolutePath());
                response.addProperty("element_count", elementCount[0]);
                response.addProperty("max_depth", maxDepth);
                response.addProperty("message",
                        "Model too large for inline response. Saved to file.");
                return ToolResult.success(response);
            }

        } catch (Exception e) {
            return ToolResult.error("Failed to export to JSON: " + e.getMessage());
        }
    }

    /**
     * Recursively builds a JSON tree from a model element.
     */
    private JsonObject buildJsonTree(EObject element, int depth, int maxDepth,
                                       int[] elementCount, CapellaModelService modelService) {
        if (elementCount[0] >= MAX_ELEMENTS) return null;
        elementCount[0]++;

        JsonObject node = new JsonObject();
        node.addProperty("name", getElementName(element));
        node.addProperty("id", getElementId(element));
        node.addProperty("type", element.eClass().getName());

        String desc = getElementDescription(element);
        if (desc != null && !desc.isBlank()) {
            node.addProperty("description", truncate(desc, 300));
        }

        node.addProperty("layer", modelService.detectLayer(element));

        // Add children if within depth limit
        if (depth < maxDepth) {
            JsonArray children = new JsonArray();
            for (EObject child : element.eContents()) {
                if (elementCount[0] >= MAX_ELEMENTS) break;
                if (child instanceof AbstractNamedElement) {
                    String childName = getElementName(child);
                    if (childName != null && !childName.isBlank()) {
                        JsonObject childNode = buildJsonTree(child, depth + 1, maxDepth,
                                elementCount, modelService);
                        if (childNode != null) {
                            children.add(childNode);
                        }
                    }
                }
            }
            if (children.size() > 0) {
                node.add("children", children);
            }
        }

        return node;
    }
}

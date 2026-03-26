package com.capellaagent.modelchat.tools.export_;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.capella.CapellaModelService;
import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.cs.Interface;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.FunctionalExchange;
import org.polarsys.capella.core.data.fa.ComponentExchange;
import org.polarsys.capella.core.data.interaction.AbstractCapability;

/**
 * Exports model elements to a CSV file.
 * <p>
 * Queries elements of the specified type in the given layer and writes them
 * to a CSV file with columns: Name, ID, Type, Description, Parent, Layer.
 */
public class ExportToCsvTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "export_to_csv";
    private static final String DESCRIPTION =
            "Exports model elements to a CSV file with name, ID, type, description.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");
    private static final List<String> VALID_TYPES = List.of(
            "functions", "components", "exchanges", "capabilities", "interfaces", "all");

    public ExportToCsvTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.EXPORT);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS));
        params.add(ToolParameter.requiredEnum("element_type",
                "Element type: functions, components, exchanges, capabilities, interfaces, all",
                VALID_TYPES));
        params.add(ToolParameter.optionalString("output_path",
                "File path for CSV output (default: temp directory)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        String elementType = getRequiredString(parameters, "element_type").toLowerCase();
        String outputPath = getOptionalString(parameters, "output_path", null);

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();
            BlockArchitecture architecture = modelService.getArchitecture(session, layer);

            // Determine output path
            if (outputPath == null || outputPath.isBlank()) {
                String tempDir = System.getProperty("java.io.tmpdir");
                outputPath = tempDir + File.separator + "capella_export_" + layer + "_"
                        + elementType + ".csv";
            }

            File outputFile = new File(outputPath);
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }

            int rowCount = 0;
            String[] columns = {"Name", "ID", "Type", "Description", "Parent", "Layer"};

            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                // Write header
                writer.println(String.join(",", columns));

                Iterator<EObject> allContents = architecture.eAllContents();
                while (allContents.hasNext()) {
                    EObject obj = allContents.next();

                    if (!matchesType(obj, elementType)) continue;

                    String name = getElementName(obj);
                    if (name == null || name.isBlank()) continue;

                    String id = getElementId(obj);
                    String type = obj.eClass().getName();
                    String desc = truncate(getElementDescription(obj), 200)
                            .replace("\"", "\"\"").replace("\n", " ").replace("\r", "");
                    String parent = obj.eContainer() != null
                            ? getElementName(obj.eContainer()) : "";

                    writer.println(
                            csvEscape(name) + ","
                            + csvEscape(id) + ","
                            + csvEscape(type) + ","
                            + csvEscape(desc) + ","
                            + csvEscape(parent) + ","
                            + csvEscape(layer));
                    rowCount++;
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "exported");
            response.addProperty("file_path", outputFile.getAbsolutePath());
            response.addProperty("row_count", rowCount);
            response.addProperty("layer", layer);
            response.addProperty("element_type", elementType);

            // Include column info
            com.google.gson.JsonArray colArray = new com.google.gson.JsonArray();
            for (String col : columns) colArray.add(col);
            response.add("columns", colArray);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to export to CSV: " + e.getMessage());
        }
    }

    private boolean matchesType(EObject obj, String elementType) {
        switch (elementType) {
            case "functions": return obj instanceof AbstractFunction;
            case "components": return obj instanceof Component;
            case "exchanges": return obj instanceof FunctionalExchange || obj instanceof ComponentExchange;
            case "capabilities": return obj instanceof AbstractCapability;
            case "interfaces": return obj instanceof Interface;
            case "all": return obj instanceof AbstractNamedElement;
            default: return false;
        }
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

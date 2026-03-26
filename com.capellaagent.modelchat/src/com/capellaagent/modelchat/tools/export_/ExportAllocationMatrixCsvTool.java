package com.capellaagent.modelchat.tools.export_;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.fa.AbstractFunction;

/**
 * Exports the function-to-component allocation matrix as CSV.
 */
public class ExportAllocationMatrixCsvTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public ExportAllocationMatrixCsvTool() {
        super("export_allocation_matrix_csv",
                "Exports function-component allocation matrix as CSV.",
                ToolCategory.EXPORT);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS));
        params.add(ToolParameter.optionalString("output_path",
                "File path for CSV output (default: temp directory)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        String outputPath = getOptionalString(parameters, "output_path", null);

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();
            BlockArchitecture arch = modelService.getArchitecture(session, layer);

            // Collect components and functions
            Map<String, String> componentNames = new LinkedHashMap<>(); // id -> name
            Map<String, String> functionNames = new LinkedHashMap<>(); // id -> name
            Map<String, String> functionToComponent = new LinkedHashMap<>(); // funcId -> compId

            Iterator<EObject> it = arch.eAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();
                if (obj instanceof Component) {
                    Component comp = (Component) obj;
                    if (!comp.isActor()) {
                        String compId = getElementId(comp);
                        componentNames.put(compId, getElementName(comp));

                        // Check allocated functions
                        try {
                            @SuppressWarnings("unchecked")
                            List<AbstractFunction> allocatedFuncs = (List<AbstractFunction>)
                                    comp.getClass().getMethod("getAllocatedFunctions").invoke(comp);
                            for (AbstractFunction func : allocatedFuncs) {
                                String funcId = getElementId(func);
                                functionNames.put(funcId, getElementName(func));
                                functionToComponent.put(funcId, compId);
                            }
                        } catch (Exception e) { /* skip */ }
                    }
                }
                if (obj instanceof AbstractFunction) {
                    String funcName = getElementName(obj);
                    if (funcName != null && !funcName.isBlank() && !funcName.contains("Root")) {
                        functionNames.putIfAbsent(getElementId(obj), funcName);
                    }
                }
            }

            // Determine output file
            if (outputPath == null || outputPath.isBlank()) {
                String tempDir = System.getProperty("java.io.tmpdir");
                outputPath = tempDir + File.separator + "allocation_matrix_" + layer + ".csv";
            }

            File outputFile = new File(outputPath);
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }

            int rowCount = 0;
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                // Header: Function, Component1, Component2, ...
                StringBuilder header = new StringBuilder("Function");
                List<String> compIds = new ArrayList<>(componentNames.keySet());
                for (String compId : compIds) {
                    header.append(",").append(csvEscape(componentNames.get(compId)));
                }
                writer.println(header.toString());

                // One row per function
                for (Map.Entry<String, String> funcEntry : functionNames.entrySet()) {
                    String funcId = funcEntry.getKey();
                    StringBuilder row = new StringBuilder(csvEscape(funcEntry.getValue()));
                    String allocatedComp = functionToComponent.get(funcId);
                    for (String compId : compIds) {
                        row.append(",").append(compId.equals(allocatedComp) ? "X" : "");
                    }
                    writer.println(row.toString());
                    rowCount++;
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "exported");
            response.addProperty("file_path", outputFile.getAbsolutePath());
            response.addProperty("row_count", rowCount);
            response.addProperty("component_count", componentNames.size());
            response.addProperty("layer", layer);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to export allocation matrix: " + e.getMessage());
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

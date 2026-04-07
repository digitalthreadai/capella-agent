package com.capellaagent.requirements.tools;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.sirius.business.api.session.Session;

import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Imports requirements from an Excel (.xlsx or .xls) file.
 * <p>
 * Uses a two-pass heuristic to detect the ID and text columns:
 * reads the first 3 rows, scores each column header against synonym sets,
 * and falls back to columns 0 (ID) and 1 (text) if no match.
 * <p>
 * Uses Apache POI with a Thread Context ClassLoader fix to prevent
 * OSGi classloader delegation failures.
 */
public class ImportRequirementsExcelTool extends AbstractCapellaTool {

    private static final Logger LOG = Logger.getLogger(ImportRequirementsExcelTool.class.getName());

    private static final String TOOL_NAME = "import_requirements_excel";
    private static final String DESCRIPTION =
            "Imports requirements from an Excel (.xlsx/.xls) file. "
            + "Auto-detects the ID and text columns from headers. "
            + "Use dry_run=true (default) to preview. "
            + "Set dry_run=false to import into the model.";

    private static final String[] ID_SYNONYMS = {
        "req id", "req#", "identifier", "requirement id", "id", "req_id", "reqid", "number"
    };
    private static final String[] TEXT_SYNONYMS = {
        "description", "requirement", "statement", "text", "req text", "content", "detail"
    };

    public ImportRequirementsExcelTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("path",
                "Absolute path to the .xlsx or .xls file"));
        params.add(ToolParameter.optionalInteger("sheet",
                "Sheet index (0-based, default 0)"));
        params.add(ToolParameter.optionalBoolean("dry_run",
                "If true (default), preview only. If false, import into model."));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String path = getRequiredString(parameters, "path");
        int sheetIndex = getOptionalInt(parameters, "sheet", 0);
        boolean dryRun = getOptionalBoolean(parameters, "dry_run", true);

        File file = new File(path);
        if (!file.exists()) {
            return ToolResult.error("File not found: " + path);
        }

        // TCCL fix for Apache POI OSGi classloader
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            return doImport(file, sheetIndex, dryRun);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private ToolResult doImport(File file, int sheetIndex, boolean dryRun) {
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {

            if (sheetIndex >= workbook.getNumberOfSheets()) {
                return ToolResult.error("Sheet index " + sheetIndex + " out of range. "
                        + "File has " + workbook.getNumberOfSheets() + " sheet(s).");
            }

            Sheet sheet = workbook.getSheetAt(sheetIndex);

            // Two-pass column detection
            int idCol = -1;
            int textCol = -1;
            String detectedIdHeader = "column 0 (auto)";
            String detectedTextHeader = "column 1 (auto)";

            // Check up to 3 header rows
            for (int rowIdx = 0; rowIdx <= Math.min(2, sheet.getLastRowNum()); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;
                for (int col = 0; col < row.getLastCellNum(); col++) {
                    Cell cell = row.getCell(col);
                    if (cell == null) continue;
                    String header = getCellAsString(cell).toLowerCase().trim();
                    if (idCol == -1 && matchesSynonyms(header, ID_SYNONYMS)) {
                        idCol = col;
                        detectedIdHeader = header;
                    }
                    if (textCol == -1 && matchesSynonyms(header, TEXT_SYNONYMS)) {
                        textCol = col;
                        detectedTextHeader = header;
                    }
                }
                if (idCol != -1 && textCol != -1) break;
            }

            // Fallback
            if (idCol == -1) { idCol = 0; }
            if (textCol == -1) { textCol = 1; }

            // Skip header row (row 0)
            List<RequirementRow> rows = new ArrayList<>();
            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;
                String id = getCellAsString(row.getCell(idCol)).trim();
                String text = textCol < row.getLastCellNum()
                        ? getCellAsString(row.getCell(textCol)).trim() : "";
                if (!id.isEmpty() || !text.isEmpty()) {
                    rows.add(new RequirementRow(id.isEmpty() ? "ROW-" + rowIdx : id, text));
                }
            }

            // Build result
            JsonObject result = new JsonObject();
            result.addProperty("dry_run", dryRun);
            result.addProperty("file", file.getName());
            result.addProperty("sheet_index", sheetIndex);
            result.addProperty("detected_id_column", detectedIdHeader);
            result.addProperty("detected_text_column", detectedTextHeader);
            result.addProperty("requirements_found", rows.size());

            JsonArray preview = new JsonArray();
            for (RequirementRow row : rows) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", row.id());
                obj.addProperty("text_snippet", truncate(row.text(), 80));
                preview.add(obj);
            }
            result.add("requirements", preview);

            if (dryRun) {
                result.addProperty("message",
                        "Dry run complete. " + rows.size() + " requirements found. "
                        + "Verify column detection above, then call with dry_run=false to import.");
                return ToolResult.success(result);
            }

            // Check Requirements Viewpoint
            try {
                Class.forName("org.polarsys.kitalpha.vp.requirements.Requirements.Requirement");
            } catch (ClassNotFoundException e) {
                return ToolResult.error(
                        "Requirements Viewpoint is not installed. "
                        + "Enable it via Window -> Preferences -> Capella -> Viewpoints.");
            }

            Session session = getActiveSession();
            int created = 0;
            for (RequirementRow row : rows) {
                try {
                    final RequirementRow finalRow = row;
                    executeInTransaction(session, "Create requirement " + row.id(), () -> {
                        try {
                            createRequirementInModel(session, finalRow.id(), finalRow.text());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                    created++;
                } catch (Exception e) {
                    LOG.warning("Failed to create requirement " + row.id() + ": " + e.getMessage());
                }
            }

            result.addProperty("created", created);
            result.addProperty("message", created + " requirements imported from Excel.");
            return ToolResult.success(result);

        } catch (Exception e) {
            return ToolResult.error("Failed to read Excel file: " + e.getMessage());
        }
    }

    private boolean matchesSynonyms(String header, String[] synonyms) {
        for (String syn : synonyms) {
            if (header.equals(syn) || header.contains(syn)) return true;
        }
        return false;
    }

    /**
     * Reads a cell value as a String. For numeric cells (common for ID columns like 1001)
     * uses DataFormatter to avoid "1001.0" representation.
     */
    private String getCellAsString(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC) {
            DataFormatter formatter = new DataFormatter();
            return formatter.formatCellValue(cell);
        }
        try {
            return cell.getStringCellValue();
        } catch (Exception e) {
            return cell.toString();
        }
    }

    private void createRequirementInModel(Session session, String id, String text) throws Exception {
        Class<?> reqFactory = Class.forName(
                "org.polarsys.kitalpha.vp.requirements.Requirements.RequirementsFactory");
        Object factory = reqFactory.getField("eINSTANCE").get(null);
        Object req = null;
        for (java.lang.reflect.Method m : reqFactory.getMethods()) {
            if (m.getName().equals("createRequirement") && m.getParameterCount() == 0) {
                req = m.invoke(factory);
                break;
            }
        }
        if (req == null) {
            throw new RuntimeException("Could not invoke createRequirement() on factory.");
        }

        setFieldReflective(req, "setReqIFIdentifier", "setId", id);
        setFieldReflective(req, "setReqIFText", "setDescription", text);
        try {
            req.getClass().getMethod("setName", String.class).invoke(req,
                    id.length() > 50 ? id.substring(0, 50) : id);
        } catch (NoSuchMethodException ex) { /* ignore */ }

        addToRequirementPackage(session, req);
    }

    private void setFieldReflective(Object obj, String method1, String method2, String value) {
        try {
            obj.getClass().getMethod(method1, String.class).invoke(obj, value);
        } catch (Exception e1) {
            if (method2 != null) {
                try {
                    obj.getClass().getMethod(method2, String.class).invoke(obj, value);
                } catch (Exception e2) { /* ignore */ }
            }
        }
    }

    private void addToRequirementPackage(Session session, Object req) throws Exception {
        for (Resource resource : session.getSemanticResources()) {
            org.eclipse.emf.common.util.TreeIterator<EObject> it = resource.getAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();
                String className = obj.eClass().getName();
                if (className.contains("RequirementsPkg") || className.contains("RequirementsModule")) {
                    for (EReference ref : obj.eClass().getEAllContainments()) {
                        if (ref.getEReferenceType().isInstance(req)) {
                            @SuppressWarnings("unchecked")
                            java.util.List<Object> list =
                                    (java.util.List<Object>) obj.eGet(ref);
                            list.add(req);
                            return;
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("No RequirementsPkg found in model.");
    }

    private record RequirementRow(String id, String text) {}
}

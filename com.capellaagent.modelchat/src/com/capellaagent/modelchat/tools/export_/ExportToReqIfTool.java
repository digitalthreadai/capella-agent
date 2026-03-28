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
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.sirius.business.api.session.Session;

/**
 * Exports requirements to ReqIF (Requirements Interchange Format) XML.
 * <p>
 * Traverses the model to find requirement elements and generates a
 * ReqIF-compliant XML file. The ReqIF format is the OMG standard for
 * requirements interchange between tools (DOORS, Polarion, etc.).
 * <p>
 * This generates ReqIF 1.0.1 compatible output with SPEC-OBJECT entries
 * for each requirement, preserving IDs, names, descriptions, and types.
 */
public class ExportToReqIfTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "export_to_reqif";
    private static final String DESCRIPTION =
            "Exports requirements to ReqIF XML format for interchange with DOORS, Polarion, etc.";

    public ExportToReqIfTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.EXPORT);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalString("output_path",
                "File path for ReqIF output (default: temp directory)"));
        params.add(ToolParameter.optionalString("scope_uuid",
                "UUID of a container element to scope the export (default: all requirements)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String outputPath = getOptionalString(parameters, "output_path", null);
        String scopeUuid = getOptionalString(parameters, "scope_uuid", null);

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            // Determine output file
            if (outputPath == null || outputPath.isBlank()) {
                String tempDir = System.getProperty("java.io.tmpdir");
                outputPath = tempDir + File.separator + "capella_requirements.reqif";
            }

            File outputFile = new File(outputPath);
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }

            // Collect requirements
            List<EObject> requirements = new ArrayList<>();

            if (scopeUuid != null && !scopeUuid.isBlank()) {
                EObject scope = resolveElementByUuid(scopeUuid);
                if (scope == null) {
                    return ToolResult.error("Scope element not found: " + scopeUuid);
                }
                collectRequirements(scope, requirements);
            } else {
                // Scan all layers and project root
                for (String layer : List.of("oa", "sa", "la", "pa")) {
                    try {
                        EObject arch = modelService.getArchitecture(session, layer);
                        collectRequirements(arch, requirements);
                    } catch (Exception e) {
                        // Layer might not exist
                    }
                }
                // Also check project root for requirement modules
                for (org.eclipse.emf.ecore.resource.Resource res : session.getSemanticResources()) {
                    for (EObject root : res.getContents()) {
                        collectRequirements(root, requirements);
                    }
                }
            }

            // Generate ReqIF XML
            String timestamp = java.time.Instant.now().toString();
            int reqCount = 0;

            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                writer.println("<REQ-IF xmlns=\"http://www.omg.org/spec/ReqIF/20110401/reqif.xsd\"");
                writer.println("        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">");
                writer.println("  <THE-HEADER>");
                writer.println("    <REQ-IF-HEADER IDENTIFIER=\"capella-export\">");
                writer.println("      <COMMENT>Exported from Capella model</COMMENT>");
                writer.println("      <CREATION-TIME>" + timestamp + "</CREATION-TIME>");
                writer.println("      <REQ-IF-TOOL-ID>Capella Agent</REQ-IF-TOOL-ID>");
                writer.println("      <REQ-IF-VERSION>1.0.1</REQ-IF-VERSION>");
                writer.println("      <SOURCE-TOOL-ID>Capella</SOURCE-TOOL-ID>");
                writer.println("      <TITLE>Capella Requirements Export</TITLE>");
                writer.println("    </REQ-IF-HEADER>");
                writer.println("  </THE-HEADER>");
                writer.println("  <CORE-CONTENT>");
                writer.println("    <REQ-IF-CONTENT>");

                // Define data types
                writer.println("      <DATATYPES>");
                writer.println("        <DATATYPE-DEFINITION-STRING IDENTIFIER=\"DT-String\" "
                        + "LONG-NAME=\"String\" MAX-LENGTH=\"4096\"/>");
                writer.println("      </DATATYPES>");

                // Define spec object type
                writer.println("      <SPEC-TYPES>");
                writer.println("        <SPEC-OBJECT-TYPE IDENTIFIER=\"SOT-Requirement\" "
                        + "LONG-NAME=\"Requirement\">");
                writer.println("          <SPEC-ATTRIBUTES>");
                writer.println("            <ATTRIBUTE-DEFINITION-STRING IDENTIFIER=\"AD-Name\" "
                        + "LONG-NAME=\"Name\">");
                writer.println("              <TYPE><DATATYPE-DEFINITION-STRING-REF>"
                        + "DT-String</DATATYPE-DEFINITION-STRING-REF></TYPE>");
                writer.println("            </ATTRIBUTE-DEFINITION-STRING>");
                writer.println("            <ATTRIBUTE-DEFINITION-STRING IDENTIFIER=\"AD-Desc\" "
                        + "LONG-NAME=\"Description\">");
                writer.println("              <TYPE><DATATYPE-DEFINITION-STRING-REF>"
                        + "DT-String</DATATYPE-DEFINITION-STRING-REF></TYPE>");
                writer.println("            </ATTRIBUTE-DEFINITION-STRING>");
                writer.println("            <ATTRIBUTE-DEFINITION-STRING IDENTIFIER=\"AD-Type\" "
                        + "LONG-NAME=\"RequirementType\">");
                writer.println("              <TYPE><DATATYPE-DEFINITION-STRING-REF>"
                        + "DT-String</DATATYPE-DEFINITION-STRING-REF></TYPE>");
                writer.println("            </ATTRIBUTE-DEFINITION-STRING>");
                writer.println("          </SPEC-ATTRIBUTES>");
                writer.println("        </SPEC-OBJECT-TYPE>");
                writer.println("      </SPEC-TYPES>");

                // Write spec objects
                writer.println("      <SPEC-OBJECTS>");
                for (EObject req : requirements) {
                    String id = getElementId(req);
                    String name = xmlEscape(getElementName(req));
                    String desc = xmlEscape(truncate(getElementDescription(req), 2000));
                    String type = req.eClass().getName();

                    // Try to get the requirement's "requirementId" if it has one
                    EStructuralFeature reqIdFeature =
                            req.eClass().getEStructuralFeature("requirementId");
                    String reqId = "";
                    if (reqIdFeature != null) {
                        Object val = req.eGet(reqIdFeature);
                        if (val != null) reqId = val.toString();
                    }

                    writer.println("        <SPEC-OBJECT IDENTIFIER=\"" + xmlEscape(id)
                            + "\" LONG-NAME=\"" + name + "\">");
                    writer.println("          <TYPE><SPEC-OBJECT-TYPE-REF>"
                            + "SOT-Requirement</SPEC-OBJECT-TYPE-REF></TYPE>");
                    writer.println("          <VALUES>");
                    writer.println("            <ATTRIBUTE-VALUE-STRING THE-VALUE=\"" + name + "\">");
                    writer.println("              <DEFINITION><ATTRIBUTE-DEFINITION-STRING-REF>"
                            + "AD-Name</ATTRIBUTE-DEFINITION-STRING-REF></DEFINITION>");
                    writer.println("            </ATTRIBUTE-VALUE-STRING>");
                    writer.println("            <ATTRIBUTE-VALUE-STRING THE-VALUE=\"" + desc + "\">");
                    writer.println("              <DEFINITION><ATTRIBUTE-DEFINITION-STRING-REF>"
                            + "AD-Desc</ATTRIBUTE-DEFINITION-STRING-REF></DEFINITION>");
                    writer.println("            </ATTRIBUTE-VALUE-STRING>");
                    writer.println("            <ATTRIBUTE-VALUE-STRING THE-VALUE=\""
                            + xmlEscape(type) + "\">");
                    writer.println("              <DEFINITION><ATTRIBUTE-DEFINITION-STRING-REF>"
                            + "AD-Type</ATTRIBUTE-DEFINITION-STRING-REF></DEFINITION>");
                    writer.println("            </ATTRIBUTE-VALUE-STRING>");
                    writer.println("          </VALUES>");
                    writer.println("        </SPEC-OBJECT>");
                    reqCount++;
                }
                writer.println("      </SPEC-OBJECTS>");

                // Write specification (flat list)
                writer.println("      <SPECIFICATIONS>");
                writer.println("        <SPECIFICATION IDENTIFIER=\"SPEC-1\" "
                        + "LONG-NAME=\"Capella Requirements\">");
                writer.println("          <TYPE><SPECIFICATION-TYPE-REF>"
                        + "SOT-Requirement</SPECIFICATION-TYPE-REF></TYPE>");
                writer.println("          <CHILDREN>");
                for (EObject req : requirements) {
                    writer.println("            <SPEC-HIERARCHY IDENTIFIER=\"SH-"
                            + xmlEscape(getElementId(req)) + "\">");
                    writer.println("              <OBJECT><SPEC-OBJECT-REF>"
                            + xmlEscape(getElementId(req)) + "</SPEC-OBJECT-REF></OBJECT>");
                    writer.println("            </SPEC-HIERARCHY>");
                }
                writer.println("          </CHILDREN>");
                writer.println("        </SPECIFICATION>");
                writer.println("      </SPECIFICATIONS>");

                writer.println("    </REQ-IF-CONTENT>");
                writer.println("  </CORE-CONTENT>");
                writer.println("</REQ-IF>");
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "exported");
            response.addProperty("file_path", outputFile.getAbsolutePath());
            response.addProperty("requirement_count", reqCount);
            response.addProperty("format", "ReqIF 1.0.1");

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to export to ReqIF: " + e.getMessage());
        }
    }

    /**
     * Recursively collects requirement elements from a container.
     */
    private void collectRequirements(EObject root, List<EObject> requirements) {
        Iterator<EObject> it = root.eAllContents();
        while (it.hasNext()) {
            EObject obj = it.next();
            String className = obj.eClass().getName();
            if (className.contains("Requirement")) {
                String name = getElementName(obj);
                if (name != null && !name.isBlank()) {
                    requirements.add(obj);
                }
            }
        }
    }

    private String xmlEscape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}

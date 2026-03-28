package com.capellaagent.modelchat.tools.export_;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
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
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.sirius.business.api.dialect.DialectManager;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.viewpoint.DRepresentationDescriptor;
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;
import org.polarsys.capella.core.data.capellamodeller.Project;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.ComponentExchange;
import org.polarsys.capella.core.data.fa.FunctionalExchange;
import org.polarsys.capella.core.data.interaction.AbstractCapability;

/**
 * Generates an HTML document from model content.
 * <p>
 * Produces a structured HTML document containing:
 * <ul>
 *   <li>Project overview and layer summaries</li>
 *   <li>Component hierarchy with descriptions</li>
 *   <li>Function catalog with exchange information</li>
 *   <li>Capability list</li>
 *   <li>Diagram inventory</li>
 * </ul>
 */
public class GenerateDocumentTool extends AbstractCapellaTool {

    private static final List<String> VALID_FORMATS = List.of("html");
    private static final List<String> LAYERS = List.of("oa", "sa", "la", "pa");
    private static final String[] LAYER_NAMES = {
            "Operational Analysis", "System Analysis",
            "Logical Architecture", "Physical Architecture"
    };

    public GenerateDocumentTool() {
        super("generate_document",
                "Generates an HTML document from model content with components, functions, "
                + "capabilities, and diagrams.",
                ToolCategory.EXPORT);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalEnum("format",
                "Document format: html (default: html)",
                VALID_FORMATS, "html"));
        params.add(ToolParameter.optionalString("output_path",
                "File path for output (default: temp directory)"));
        params.add(ToolParameter.optionalString("title",
                "Document title (default: project name)"));
        params.add(ToolParameter.optionalBoolean("include_descriptions",
                "Include element descriptions (default: true)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String format = getOptionalString(parameters, "format", "html");
        String outputPath = getOptionalString(parameters, "output_path", null);
        String title = getOptionalString(parameters, "title", null);
        boolean includeDescriptions = getOptionalBoolean(
                parameters, "include_descriptions", true);

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            // Get project name
            String projectName = "Capella Model";
            for (Resource resource : session.getSemanticResources()) {
                for (EObject root : resource.getContents()) {
                    if (root instanceof Project) {
                        String name = ((Project) root).getName();
                        if (name != null && !name.isBlank()) {
                            projectName = name;
                            break;
                        }
                    }
                }
            }

            String docTitle = (title != null && !title.isBlank()) ? title : projectName;

            StringBuilder html = new StringBuilder();
            int totalElements = 0;

            // HTML header
            html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
            html.append("<meta charset=\"UTF-8\">\n");
            html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
            html.append("<title>").append(escapeHtml(docTitle)).append("</title>\n");
            html.append("<style>\n");
            html.append("body { font-family: 'Segoe UI', Arial, sans-serif; margin: 40px; ");
            html.append("line-height: 1.6; color: #333; }\n");
            html.append("h1 { color: #1a5276; border-bottom: 3px solid #1a5276; padding-bottom: 10px; }\n");
            html.append("h2 { color: #2874a6; border-bottom: 1px solid #d5d8dc; padding-bottom: 5px; }\n");
            html.append("h3 { color: #2e86c1; }\n");
            html.append("table { border-collapse: collapse; width: 100%; margin: 15px 0; }\n");
            html.append("th, td { border: 1px solid #d5d8dc; padding: 8px 12px; text-align: left; }\n");
            html.append("th { background-color: #eaf2f8; font-weight: bold; }\n");
            html.append("tr:nth-child(even) { background-color: #f8f9fa; }\n");
            html.append(".description { color: #666; font-style: italic; margin: 5px 0; }\n");
            html.append(".toc { background: #f8f9fa; padding: 20px; border-radius: 5px; }\n");
            html.append(".toc a { text-decoration: none; color: #2874a6; }\n");
            html.append(".toc ul { list-style-type: none; padding-left: 20px; }\n");
            html.append("</style>\n</head>\n<body>\n");

            // Title
            html.append("<h1>").append(escapeHtml(docTitle)).append("</h1>\n");
            html.append("<p>Generated from Capella model by Capella Agent</p>\n\n");

            // Table of contents
            html.append("<div class=\"toc\">\n<h2>Table of Contents</h2>\n<ul>\n");
            for (int i = 0; i < LAYERS.size(); i++) {
                html.append("<li><a href=\"#").append(LAYERS.get(i)).append("\">")
                        .append(LAYER_NAMES[i]).append("</a></li>\n");
            }
            html.append("<li><a href=\"#diagrams\">Diagrams</a></li>\n");
            html.append("</ul>\n</div>\n\n");

            // Process each layer
            for (int layerIdx = 0; layerIdx < LAYERS.size(); layerIdx++) {
                String layer = LAYERS.get(layerIdx);
                String layerName = LAYER_NAMES[layerIdx];

                try {
                    BlockArchitecture arch = modelService.getArchitecture(session, layer);

                    html.append("<h2 id=\"").append(layer).append("\">")
                            .append(escapeHtml(layerName)).append("</h2>\n");

                    // Collect elements
                    List<Component> components = new ArrayList<>();
                    List<AbstractFunction> functions = new ArrayList<>();
                    List<AbstractCapability> capabilities = new ArrayList<>();
                    int exchangeCount = 0;

                    Iterator<EObject> it = arch.eAllContents();
                    while (it.hasNext()) {
                        EObject obj = it.next();
                        if (obj instanceof Component) {
                            Component comp = (Component) obj;
                            String name = getElementName(comp);
                            if (name != null && !name.isBlank()) {
                                components.add(comp);
                            }
                        } else if (obj instanceof AbstractFunction) {
                            String name = getElementName(obj);
                            if (name != null && !name.isBlank() && !name.contains("Root")) {
                                functions.add((AbstractFunction) obj);
                            }
                        } else if (obj instanceof AbstractCapability) {
                            capabilities.add((AbstractCapability) obj);
                        } else if (obj instanceof ComponentExchange
                                || obj instanceof FunctionalExchange) {
                            exchangeCount++;
                        }
                    }

                    totalElements += components.size() + functions.size()
                            + capabilities.size() + exchangeCount;

                    // Layer summary
                    html.append("<p>Components: ").append(components.size())
                            .append(" | Functions: ").append(functions.size())
                            .append(" | Capabilities: ").append(capabilities.size())
                            .append(" | Exchanges: ").append(exchangeCount)
                            .append("</p>\n");

                    // Components table
                    if (!components.isEmpty()) {
                        html.append("<h3>Components</h3>\n");
                        html.append("<table>\n<tr><th>Name</th><th>Type</th><th>Actor</th>");
                        if (includeDescriptions) html.append("<th>Description</th>");
                        html.append("</tr>\n");

                        for (Component comp : components) {
                            html.append("<tr><td>").append(escapeHtml(getElementName(comp)))
                                    .append("</td><td>").append(comp.eClass().getName())
                                    .append("</td><td>").append(comp.isActor() ? "Yes" : "No")
                                    .append("</td>");
                            if (includeDescriptions) {
                                String desc = getElementDescription(comp);
                                html.append("<td>").append(desc != null
                                        ? escapeHtml(truncate(desc, 200)) : "").append("</td>");
                            }
                            html.append("</tr>\n");
                        }
                        html.append("</table>\n");
                    }

                    // Functions table
                    if (!functions.isEmpty()) {
                        html.append("<h3>Functions</h3>\n");
                        html.append("<table>\n<tr><th>Name</th><th>Inputs</th><th>Outputs</th>");
                        if (includeDescriptions) html.append("<th>Description</th>");
                        html.append("</tr>\n");

                        for (AbstractFunction func : functions) {
                            html.append("<tr><td>").append(escapeHtml(getElementName(func)))
                                    .append("</td><td>").append(func.getIncoming().size())
                                    .append("</td><td>").append(func.getOutgoing().size())
                                    .append("</td>");
                            if (includeDescriptions) {
                                String desc = getElementDescription(func);
                                html.append("<td>").append(desc != null
                                        ? escapeHtml(truncate(desc, 200)) : "").append("</td>");
                            }
                            html.append("</tr>\n");
                        }
                        html.append("</table>\n");
                    }

                    // Capabilities
                    if (!capabilities.isEmpty()) {
                        html.append("<h3>Capabilities</h3>\n");
                        html.append("<table>\n<tr><th>Name</th><th>Scenarios</th></tr>\n");
                        for (AbstractCapability cap : capabilities) {
                            html.append("<tr><td>").append(escapeHtml(getElementName(cap)))
                                    .append("</td><td>")
                                    .append(cap.getOwnedScenarios().size())
                                    .append("</td></tr>\n");
                        }
                        html.append("</table>\n");
                    }

                } catch (Exception e) {
                    html.append("<p><em>Layer not available: ").append(layer).append("</em></p>\n");
                }
            }

            // Diagrams section
            html.append("<h2 id=\"diagrams\">Diagrams</h2>\n");
            try {
                Collection<DRepresentationDescriptor> descriptors =
                        DialectManager.INSTANCE.getAllRepresentationDescriptors(session);
                if (!descriptors.isEmpty()) {
                    html.append("<table>\n<tr><th>Name</th><th>Type</th></tr>\n");
                    for (DRepresentationDescriptor desc : descriptors) {
                        html.append("<tr><td>").append(escapeHtml(desc.getName()))
                                .append("</td><td>")
                                .append(desc.getDescription() != null
                                        ? escapeHtml(desc.getDescription().getName()) : "")
                                .append("</td></tr>\n");
                    }
                    html.append("</table>\n");
                    totalElements += descriptors.size();
                } else {
                    html.append("<p>No diagrams found.</p>\n");
                }
            } catch (Exception e) {
                html.append("<p><em>Could not list diagrams.</em></p>\n");
            }

            // Footer
            html.append("<hr>\n<p style=\"color: #999; font-size: 0.9em;\">")
                    .append("Generated by Capella Agent | Elements documented: ")
                    .append(totalElements).append("</p>\n");
            html.append("</body>\n</html>\n");

            String htmlContent = html.toString();

            // Write to file
            if (outputPath == null || outputPath.isBlank()) {
                outputPath = System.getProperty("java.io.tmpdir") + File.separator
                        + "capella_document.html";
            }
            File outputFile = new File(outputPath);
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                writer.print(htmlContent);
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "generated");
            response.addProperty("format", "html");
            response.addProperty("file_path", outputFile.getAbsolutePath());
            response.addProperty("file_size_bytes", outputFile.length());
            response.addProperty("total_elements_documented", totalElements);
            response.addProperty("title", docTitle);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to generate document: " + e.getMessage());
        }
    }

    /**
     * Escapes HTML special characters.
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}

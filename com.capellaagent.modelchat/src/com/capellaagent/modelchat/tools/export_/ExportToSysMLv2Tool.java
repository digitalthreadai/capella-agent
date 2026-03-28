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
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.cs.Interface;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.ComponentExchange;
import org.polarsys.capella.core.data.fa.ComponentPort;
import org.polarsys.capella.core.data.fa.FunctionalExchange;
import org.polarsys.capella.core.data.information.ExchangeItem;
import org.polarsys.capella.core.data.information.Property;

/**
 * Exports model elements in SysML v2 textual notation.
 * <p>
 * Generates a {@code .sysml} file containing part definitions, port definitions,
 * connection definitions, and action definitions that correspond to
 * Capella components, ports, exchanges, and functions.
 * <p>
 * The SysML v2 output follows the KerML/SysML v2 textual grammar specification.
 */
public class ExportToSysMLv2Tool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public ExportToSysMLv2Tool() {
        super("export_to_sysmlv2",
                "Exports model elements in SysML v2 textual notation (.sysml file).",
                ToolCategory.EXPORT);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer to export: oa, sa, la, pa",
                VALID_LAYERS));
        params.add(ToolParameter.optionalString("output_path",
                "File path for the .sysml output (default: temp directory)"));
        params.add(ToolParameter.optionalString("element_uuid",
                "UUID of specific element subtree to export (exports all if omitted)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        String outputPath = getOptionalString(parameters, "output_path", null);
        String elementUuid = getOptionalString(parameters, "element_uuid", null);

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();
            BlockArchitecture arch = modelService.getArchitecture(session, layer);

            EObject root;
            if (elementUuid != null && !elementUuid.isBlank()) {
                root = resolveElementByUuid(elementUuid);
                if (root == null) {
                    return ToolResult.error("Element not found: " + elementUuid);
                }
            } else {
                root = arch;
            }

            // Collect elements
            List<Component> components = new ArrayList<>();
            List<AbstractFunction> functions = new ArrayList<>();
            List<ComponentExchange> exchanges = new ArrayList<>();
            List<FunctionalExchange> funcExchanges = new ArrayList<>();
            List<Interface> interfaces = new ArrayList<>();

            Iterator<EObject> it = root.eAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();
                if (obj instanceof Component) {
                    Component comp = (Component) obj;
                    String name = getElementName(comp);
                    if (name != null && !name.isBlank()) {
                        components.add(comp);
                    }
                } else if (obj instanceof AbstractFunction) {
                    AbstractFunction func = (AbstractFunction) obj;
                    String name = getElementName(func);
                    if (name != null && !name.isBlank() && !name.contains("Root")) {
                        functions.add(func);
                    }
                } else if (obj instanceof ComponentExchange) {
                    exchanges.add((ComponentExchange) obj);
                } else if (obj instanceof FunctionalExchange) {
                    funcExchanges.add((FunctionalExchange) obj);
                } else if (obj instanceof Interface) {
                    interfaces.add((Interface) obj);
                }
            }

            // Generate SysML v2 text
            StringBuilder sysml = new StringBuilder();
            String packageName = sanitizeIdentifier(getElementName(root));
            sysml.append("package ").append(packageName).append(" {\n\n");

            // Export interfaces as port definitions
            for (Interface iface : interfaces) {
                String ifaceName = sanitizeIdentifier(getElementName(iface));
                sysml.append("    port def ").append(ifaceName).append(" {\n");
                String desc = getElementDescription(iface);
                if (desc != null && !desc.isBlank()) {
                    sysml.append("        doc /* ").append(escapeComment(desc)).append(" */\n");
                }
                sysml.append("    }\n\n");
            }

            // Export functions as action definitions
            for (AbstractFunction func : functions) {
                String funcName = sanitizeIdentifier(getElementName(func));
                sysml.append("    action def ").append(funcName).append(" {\n");
                String desc = getElementDescription(func);
                if (desc != null && !desc.isBlank()) {
                    sysml.append("        doc /* ").append(escapeComment(desc)).append(" */\n");
                }

                // Add in/out flows based on exchanges
                for (var incoming : func.getIncoming()) {
                    if (incoming instanceof FunctionalExchange) {
                        FunctionalExchange fe = (FunctionalExchange) incoming;
                        sysml.append("        in item ")
                                .append(sanitizeIdentifier(getElementName(fe)))
                                .append(";\n");
                    }
                }
                for (var outgoing : func.getOutgoing()) {
                    if (outgoing instanceof FunctionalExchange) {
                        FunctionalExchange fe = (FunctionalExchange) outgoing;
                        sysml.append("        out item ")
                                .append(sanitizeIdentifier(getElementName(fe)))
                                .append(";\n");
                    }
                }

                sysml.append("    }\n\n");
            }

            // Export components as part definitions
            for (Component comp : components) {
                String compName = sanitizeIdentifier(getElementName(comp));
                if (comp.isActor()) {
                    sysml.append("    /* actor */ ");
                } else {
                    sysml.append("    ");
                }
                sysml.append("part def ").append(compName).append(" {\n");

                String desc = getElementDescription(comp);
                if (desc != null && !desc.isBlank()) {
                    sysml.append("        doc /* ").append(escapeComment(desc)).append(" */\n");
                }

                // Add ports
                try {
                    for (org.eclipse.emf.ecore.EObject feature : comp.getOwnedFeatures()) {
                        if (feature instanceof ComponentPort) {
                            ComponentPort port = (ComponentPort) feature;
                            String portName = sanitizeIdentifier(getElementName(port));
                            String direction = port.getOrientation() != null
                                    ? port.getOrientation().getName().toLowerCase() : "inout";
                            sysml.append("        port ").append(portName)
                                    .append(" : ").append(portName).append("Def;\n");
                        }
                    }
                } catch (Exception e) {
                    // Port iteration may fail
                }

                // Add allocated functions
                try {
                    @SuppressWarnings("unchecked")
                    List<?> allocatedFunctions = (List<?>) comp.getClass()
                            .getMethod("getAllocatedFunctions").invoke(comp);
                    for (Object f : allocatedFunctions) {
                        if (f instanceof AbstractFunction) {
                            sysml.append("        perform action ")
                                    .append(sanitizeIdentifier(getElementName((EObject) f)))
                                    .append(";\n");
                        }
                    }
                } catch (Exception e) {
                    // Allocation API may vary
                }

                sysml.append("    }\n\n");
            }

            // Export component exchanges as connection definitions
            for (ComponentExchange ce : exchanges) {
                String ceName = sanitizeIdentifier(getElementName(ce));
                EObject source = ce.getSource();
                EObject target = ce.getTarget();
                String sourceName = source != null ? sanitizeIdentifier(getElementName(source)) : "unknown";
                String targetName = target != null ? sanitizeIdentifier(getElementName(target)) : "unknown";

                sysml.append("    connection def ").append(ceName).append(" {\n");
                sysml.append("        end : ").append(sourceName).append(";\n");
                sysml.append("        end : ").append(targetName).append(";\n");
                sysml.append("    }\n\n");
            }

            sysml.append("}\n");

            String sysmlText = sysml.toString();

            // Write to file
            if (outputPath == null || outputPath.isBlank()) {
                outputPath = System.getProperty("java.io.tmpdir") + File.separator
                        + "capella_" + layer + "_export.sysml";
            }
            File outputFile = new File(outputPath);
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                writer.print(sysmlText);
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "exported");
            response.addProperty("layer", layer);
            response.addProperty("file_path", outputFile.getAbsolutePath());
            response.addProperty("component_count", components.size());
            response.addProperty("function_count", functions.size());
            response.addProperty("exchange_count", exchanges.size());
            response.addProperty("interface_count", interfaces.size());
            response.addProperty("file_size_bytes", outputFile.length());

            // Return inline preview if small enough
            if (sysmlText.length() <= 5000) {
                response.addProperty("preview", sysmlText);
            } else {
                response.addProperty("preview", sysmlText.substring(0, 2000) + "\n... (truncated)");
            }

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to export to SysML v2: " + e.getMessage());
        }
    }

    /**
     * Converts a Capella element name to a valid SysML v2 identifier.
     * Replaces spaces and special characters with underscores.
     */
    private String sanitizeIdentifier(String name) {
        if (name == null || name.isBlank()) return "Unnamed";
        // Replace non-alphanumeric characters (except underscore) with underscore
        String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_");
        // Ensure it doesn't start with a digit
        if (Character.isDigit(sanitized.charAt(0))) {
            sanitized = "_" + sanitized;
        }
        // Remove consecutive underscores
        sanitized = sanitized.replaceAll("_+", "_");
        // Trim trailing underscore
        if (sanitized.endsWith("_")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        return sanitized;
    }

    /**
     * Escapes text for use in a SysML v2 block comment.
     */
    private String escapeComment(String text) {
        if (text == null) return "";
        return text.replace("*/", "* /").replace("\n", " ").trim();
    }
}

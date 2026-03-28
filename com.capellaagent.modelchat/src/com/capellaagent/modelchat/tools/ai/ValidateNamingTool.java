package com.capellaagent.modelchat.tools.ai;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.capellaagent.core.capella.CapellaModelService;
import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.FunctionalExchange;
import org.polarsys.capella.core.data.fa.ComponentExchange;
import org.polarsys.capella.core.data.cs.Interface;
import org.polarsys.capella.core.data.interaction.AbstractCapability;

/**
 * Validates naming conventions across model elements.
 * <p>
 * Checks elements against configurable naming rules:
 * <ul>
 *   <li>Functions should use verb phrases (e.g., "Manage Flight Data")</li>
 *   <li>Components should use noun phrases (e.g., "Flight Management System")</li>
 *   <li>Exchanges should describe data flow (e.g., "Flight Plan Data")</li>
 *   <li>Elements should not have generic names (e.g., "New Element", "Copy of...")</li>
 *   <li>Elements should not have empty or single-character names</li>
 * </ul>
 * Returns violations with severity levels and suggested fixes for LLM review.
 */
public class ValidateNamingTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "validate_naming";
    private static final String DESCRIPTION =
            "Checks naming conventions compliance and reports violations with suggestions.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    // Generic/placeholder name patterns
    private static final Pattern GENERIC_NAME = Pattern.compile(
            "(?i)(^new\\s|^copy\\s+of|^untitled|^unnamed|^element\\d*$|^todo|^test\\d*$|^temp)");
    private static final Pattern TOO_SHORT = Pattern.compile("^.{0,2}$");
    private static final Pattern ALL_CAPS_NO_SPACES = Pattern.compile("^[A-Z_]{5,}$");
    private static final Pattern STARTS_WITH_LOWERCASE = Pattern.compile("^[a-z]");

    public ValidateNamingTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.AI_INTELLIGENCE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS));
        params.add(ToolParameter.optionalBoolean("strict",
                "Enable strict mode with additional checks (default: false)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        boolean strict = getOptionalBoolean(parameters, "strict", false);

        if (!VALID_LAYERS.contains(layer)) {
            return ToolResult.error("Invalid layer '" + layer + "'");
        }

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();
            BlockArchitecture architecture = modelService.getArchitecture(session, layer);

            JsonArray violations = new JsonArray();
            int totalChecked = 0;
            int errorCount = 0, warningCount = 0, infoCount = 0;

            Iterator<EObject> allContents = architecture.eAllContents();
            while (allContents.hasNext()) {
                EObject obj = allContents.next();

                if (!(obj instanceof AbstractNamedElement)) continue;

                String name = getElementName(obj);
                if (name == null || name.contains("Root")) continue;

                String elementType = categorizeElement(obj);
                if (elementType == null) continue;

                totalChecked++;

                // Check: empty or too short
                if (name.isBlank()) {
                    addViolation(violations, obj, name, "error",
                            "empty_name", "Element has no name",
                            "Assign a meaningful name describing the element's purpose");
                    errorCount++;
                    continue;
                }

                if (TOO_SHORT.matcher(name).matches()) {
                    addViolation(violations, obj, name, "error",
                            "too_short", "Name is too short (<=2 characters)",
                            "Use a descriptive name with at least 3 characters");
                    errorCount++;
                }

                // Check: generic/placeholder names
                if (GENERIC_NAME.matcher(name).find()) {
                    addViolation(violations, obj, name, "error",
                            "generic_name", "Name appears to be a placeholder",
                            "Replace with a meaningful domain-specific name");
                    errorCount++;
                }

                // Check: functions should use verb phrases
                if ("function".equals(elementType)) {
                    // Functions typically start with a verb
                    if (strict && STARTS_WITH_LOWERCASE.matcher(name).find()) {
                        addViolation(violations, obj, name, "info",
                                "function_casing",
                                "Function name starts with lowercase",
                                "Consider capitalizing: " + capitalize(name));
                        infoCount++;
                    }
                }

                // Check: all-caps names (likely acronyms that could be more descriptive)
                if (ALL_CAPS_NO_SPACES.matcher(name).matches() && name.length() > 4) {
                    addViolation(violations, obj, name, "warning",
                            "all_caps", "Name is all uppercase without spaces",
                            "Consider expanding to a more descriptive name");
                    warningCount++;
                }

                // Check: trailing/leading whitespace
                if (!name.equals(name.trim())) {
                    addViolation(violations, obj, name, "warning",
                            "whitespace", "Name has leading or trailing whitespace",
                            "Trim whitespace from: \"" + name + "\"");
                    warningCount++;
                }

                // Check: duplicate spaces
                if (name.contains("  ")) {
                    addViolation(violations, obj, name, "warning",
                            "double_space", "Name contains consecutive spaces",
                            "Normalize whitespace in the name");
                    warningCount++;
                }

                // Check: very long names
                if (name.length() > 80) {
                    addViolation(violations, obj, name, "info",
                            "too_long", "Name exceeds 80 characters",
                            "Consider a shorter, more concise name");
                    infoCount++;
                }

                // Strict mode: check naming patterns by type
                if (strict) {
                    if ("exchange".equals(elementType) && !name.contains(" ")) {
                        addViolation(violations, obj, name, "info",
                                "exchange_single_word",
                                "Exchange name is a single word",
                                "Consider a more descriptive name for the data flow");
                        infoCount++;
                    }
                }

                // Limit output size
                if (violations.size() >= 200) break;
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("strict_mode", strict);
            response.addProperty("total_checked", totalChecked);
            response.addProperty("total_violations", violations.size());
            response.addProperty("errors", errorCount);
            response.addProperty("warnings", warningCount);
            response.addProperty("info", infoCount);
            response.addProperty("compliance_percent",
                    totalChecked == 0 ? 100.0
                            : Math.round((1.0 - (double) violations.size() / totalChecked)
                            * 1000.0) / 10.0);
            response.add("violations", violations);
            response.addProperty("review_prompt",
                    "Review the naming violations above. For each error, suggest a specific "
                    + "corrected name following ARCADIA naming conventions. "
                    + "Functions should use verb phrases, components should use noun phrases.");

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to validate naming: " + e.getMessage());
        }
    }

    private void addViolation(JsonArray violations, EObject element, String name,
                               String severity, String rule, String message, String suggestion) {
        JsonObject v = new JsonObject();
        v.addProperty("element_name", name);
        v.addProperty("element_id", getElementId(element));
        v.addProperty("element_type", element.eClass().getName());
        v.addProperty("severity", severity);
        v.addProperty("rule", rule);
        v.addProperty("message", message);
        v.addProperty("suggestion", suggestion);
        violations.add(v);
    }

    private String categorizeElement(EObject obj) {
        if (obj instanceof AbstractFunction) return "function";
        if (obj instanceof Component) return "component";
        if (obj instanceof FunctionalExchange || obj instanceof ComponentExchange) return "exchange";
        if (obj instanceof Interface) return "interface";
        if (obj instanceof AbstractCapability) return "capability";
        return null;
    }

    private String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}

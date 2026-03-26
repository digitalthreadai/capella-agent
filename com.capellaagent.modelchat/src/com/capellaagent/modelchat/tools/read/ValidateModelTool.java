package com.capellaagent.modelchat.tools.read;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.capella.CapellaModelService;
import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.ComponentFunctionalAllocation;
import org.polarsys.capella.core.data.interaction.AbstractCapability;

/**
 * Runs structural validation checks on the Capella model and returns issues found.
 * <p>
 * Performs lightweight structural checks rather than full EMF Validation Framework
 * execution, which avoids dependencies on validation plug-ins that may not be
 * available at runtime. Checks include:
 * <ul>
 *   <li>Elements with empty or missing names</li>
 *   <li>Functions with no allocations to components</li>
 *   <li>Components with no ports defined</li>
 *   <li>Capabilities with no involved functions</li>
 * </ul>
 *
 * <h3>Tool Specification</h3>
 * <ul>
 *   <li><b>Name:</b> validate_model</li>
 *   <li><b>Category:</b> model_read</li>
 *   <li><b>Parameters:</b>
 *     <ul>
 *       <li>{@code layer} (string, optional) - Validate only a specific layer: oa, sa, la, pa</li>
 *       <li>{@code severity} (string, optional, default "all") - Minimum severity to include:
 *           error, warning, info, or all</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class ValidateModelTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "validate_model";
    private static final String DESCRIPTION =
            "Runs Capella model validation and returns issues found. "
            + "Can validate a specific layer or the entire model. "
            + "Returns issues with severity, message, and affected element.";

    private static final int MAX_ISSUES = 300;
    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");
    private static final List<String> VALID_SEVERITIES = List.of("error", "warning", "info", "all");

    public ValidateModelTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalEnum("layer",
                "Validate only a specific layer: oa, sa, la, pa (default: entire model)",
                VALID_LAYERS, null));
        params.add(ToolParameter.optionalEnum("severity",
                "Minimum severity to include: error, warning, info, all (default: all)",
                VALID_SEVERITIES, "all"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getOptionalString(parameters, "layer", null);
        String severity = getOptionalString(parameters, "severity", "all").toLowerCase();

        if (layer != null && !VALID_LAYERS.contains(layer.toLowerCase())) {
            return ToolResult.error("Invalid layer '" + layer
                    + "'. Must be one of: " + String.join(", ", VALID_LAYERS));
        }

        if (!VALID_SEVERITIES.contains(severity)) {
            return ToolResult.error("Invalid severity '" + severity
                    + "'. Must be one of: " + String.join(", ", VALID_SEVERITIES));
        }

        try {
            // Thread safety: read operations on the Capella model should ideally
            // be performed within a read-exclusive context via CapellaModelService
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();

            List<ValidationIssue> issues = runValidation(session, modelService, layer, severity);

            // Build counts by severity
            int errorCount = 0;
            int warningCount = 0;
            int infoCount = 0;

            JsonArray issuesArray = new JsonArray();
            int count = 0;
            for (ValidationIssue issue : issues) {
                switch (issue.severity) {
                    case "error" -> errorCount++;
                    case "warning" -> warningCount++;
                    case "info" -> infoCount++;
                }

                if (count < MAX_ISSUES) {
                    JsonObject issueObj = new JsonObject();
                    issueObj.addProperty("severity", issue.severity);
                    issueObj.addProperty("message", issue.message);
                    issueObj.addProperty("rule_id", issue.ruleId);
                    if (issue.elementUuid != null) {
                        issueObj.addProperty("element_uuid", issue.elementUuid);
                        issueObj.addProperty("element_name", issue.elementName);
                        issueObj.addProperty("element_type", issue.elementType);
                    }
                    issuesArray.add(issueObj);
                    count++;
                }
            }

            JsonObject response = new JsonObject();
            if (layer != null) {
                response.addProperty("validated_layer", layer.toLowerCase());
            } else {
                response.addProperty("validated_layer", "all");
            }
            response.addProperty("severity_filter", severity);

            JsonObject summary = new JsonObject();
            summary.addProperty("total_issues", issues.size());
            summary.addProperty("errors", errorCount);
            summary.addProperty("warnings", warningCount);
            summary.addProperty("info", infoCount);
            response.add("summary", summary);

            response.addProperty("returned", issuesArray.size());
            response.addProperty("truncated", issues.size() > MAX_ISSUES);
            response.add("issues", issuesArray);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Validation failed: " + e.getMessage());
        }
    }

    /**
     * Runs structural validation checks on the model, collecting issues.
     * <p>
     * Traverses the model elements and checks for common structural problems:
     * empty names, unallocated functions, portless components, and uninvolved
     * capabilities.
     *
     * @param session      the Sirius session
     * @param modelService the model service for navigation
     * @param layer        optional layer filter (null for all layers)
     * @param severity     minimum severity filter
     * @return list of validation issues found
     */
    private List<ValidationIssue> runValidation(Session session, CapellaModelService modelService,
                                                  String layer, String severity) {
        List<ValidationIssue> issues = new ArrayList<>();
        int maxCollect = MAX_ISSUES + 100; // Collect extra to get accurate total

        if (layer != null && !layer.isBlank()) {
            // Validate a single architecture layer
            BlockArchitecture arch = modelService.getArchitecture(session, layer.toLowerCase());
            validateContents(arch.eAllContents(), issues, severity, maxCollect);
        } else {
            // Validate all semantic resources
            for (Resource resource : session.getSemanticResources()) {
                Iterator<EObject> allContents = resource.getAllContents();
                validateContents(allContents, issues, severity, maxCollect);
                if (issues.size() >= maxCollect) break;
            }
        }

        return issues;
    }

    /**
     * Iterates through model contents and applies validation checks.
     *
     * @param contents   the iterator over model elements
     * @param issues     the list to populate with found issues
     * @param severity   the minimum severity filter
     * @param maxCollect maximum number of issues to collect
     */
    private void validateContents(Iterator<EObject> contents, List<ValidationIssue> issues,
                                    String severity, int maxCollect) {
        while (contents.hasNext() && issues.size() < maxCollect) {
            EObject obj = contents.next();

            // Check 1: Named elements with empty names (severity: error)
            if (obj instanceof AbstractNamedElement) {
                AbstractNamedElement named = (AbstractNamedElement) obj;
                if (shouldInclude(severity, "error")) {
                    String name = named.getName();
                    if (name == null || name.isBlank()) {
                        issues.add(new ValidationIssue(
                                "error",
                                "Element has an empty or missing name",
                                "EMPTY_NAME",
                                getElementId(obj),
                                "(unnamed)",
                                obj.eClass().getName()));
                    }
                }
            }

            // Check 2: Functions with no allocations (severity: warning)
            if (obj instanceof AbstractFunction && shouldInclude(severity, "warning")) {
                AbstractFunction fn = (AbstractFunction) obj;
                try {
                    // Check if the function has any component functional allocations
                    List<?> allocations = fn.getComponentFunctionalAllocations();
                    if (allocations == null || allocations.isEmpty()) {
                        String fnName = fn.getName();
                        // Skip root functions which are container functions
                        if (fnName != null && !fnName.isBlank()
                                && !fnName.contains("Root")) {
                            issues.add(new ValidationIssue(
                                    "warning",
                                    "Function is not allocated to any component",
                                    "UNALLOCATED_FUNCTION",
                                    getElementId(obj),
                                    fnName,
                                    obj.eClass().getName()));
                        }
                    }
                } catch (Exception e) {
                    // Allocation API may vary; skip this check for this element
                }
            }

            // Check 3: Components with no ports (severity: info)
            if (obj instanceof Component && shouldInclude(severity, "info")) {
                Component comp = (Component) obj;
                try {
                    boolean hasPorts = (comp.getContainedComponentPorts() != null
                            && !comp.getContainedComponentPorts().isEmpty())
                            || (comp.getOwnedFeatures() != null
                            && !comp.getOwnedFeatures().isEmpty());
                    if (!hasPorts && !comp.isActor()) {
                        String compName = comp.getName();
                        if (compName != null && !compName.isBlank()) {
                            issues.add(new ValidationIssue(
                                    "info",
                                    "Component has no ports defined",
                                    "NO_PORTS",
                                    getElementId(obj),
                                    compName,
                                    obj.eClass().getName()));
                        }
                    }
                } catch (Exception e) {
                    // Port API may vary; skip
                }
            }

            // Check 4: Capabilities with no involved functions (severity: warning)
            if (obj instanceof AbstractCapability && shouldInclude(severity, "warning")) {
                AbstractCapability cap = (AbstractCapability) obj;
                try {
                    boolean hasInvolvements =
                            cap.getOwnedAbstractFunctionAbstractCapabilityInvolvements() != null
                            && !cap.getOwnedAbstractFunctionAbstractCapabilityInvolvements().isEmpty();
                    if (!hasInvolvements) {
                        String capName = cap.getName();
                        if (capName != null && !capName.isBlank()) {
                            issues.add(new ValidationIssue(
                                    "warning",
                                    "Capability has no involved functions",
                                    "NO_INVOLVED_FUNCTIONS",
                                    getElementId(obj),
                                    capName,
                                    obj.eClass().getName()));
                        }
                    }
                } catch (Exception e) {
                    // Involvement API may vary; skip
                }
            }
        }
    }

    /**
     * Determines whether an issue of the given severity should be included
     * based on the severity filter.
     *
     * @param filter        the severity filter ("all", "error", "warning", "info")
     * @param issueSeverity the severity of the issue being checked
     * @return true if the issue should be included
     */
    private boolean shouldInclude(String filter, String issueSeverity) {
        if ("all".equals(filter)) return true;
        int filterLevel = severityLevel(filter);
        int issueLevel = severityLevel(issueSeverity);
        return issueLevel >= filterLevel;
    }

    /**
     * Returns a numeric severity level for comparison.
     * Higher values are more severe.
     */
    private int severityLevel(String severity) {
        return switch (severity) {
            case "error" -> 3;
            case "warning" -> 2;
            case "info" -> 1;
            default -> 0;
        };
    }

    /**
     * Internal data class representing a single validation issue.
     */
    private static class ValidationIssue {
        final String severity;
        final String message;
        final String ruleId;
        final String elementUuid;
        final String elementName;
        final String elementType;

        ValidationIssue(String severity, String message, String ruleId,
                        String elementUuid, String elementName, String elementType) {
            this.severity = severity;
            this.message = message;
            this.ruleId = ruleId;
            this.elementUuid = elementUuid;
            this.elementName = elementName;
            this.elementType = elementType;
        }
    }
}

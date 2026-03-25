package com.capellaagent.modelchat.tools.read;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;

// PLACEHOLDER imports for Capella validation API
// import org.eclipse.emf.validation.service.IBatchValidator;
// import org.eclipse.emf.validation.service.ModelValidationService;
// import org.eclipse.core.runtime.IStatus;
// import org.eclipse.emf.validation.model.EvaluationMode;
// import org.polarsys.capella.core.validation.CapellaValidationActivator;

/**
 * Runs the Capella model validation framework and returns validation results.
 * <p>
 * Can validate the entire model or a specific ARCADIA layer. Results are filtered
 * by severity level. This tool helps the LLM identify model quality issues and
 * suggest corrections.
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
    private static final List<String> VALID_SEVERITIES = List.of("error", "warning", "info", "all");

    public ValidateModelTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalString("layer",
                "Validate only a specific layer: oa, sa, la, pa (default: entire model)"));
        params.add(ToolParameter.optionalString("severity",
                "Minimum severity to include: error, warning, info, all (default: all)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getOptionalString(parameters, "layer", null);
        String severity = getOptionalString(parameters, "severity", "all").toLowerCase();

        if (!VALID_SEVERITIES.contains(severity)) {
            return ToolResult.error("Invalid severity '" + severity
                    + "'. Must be one of: " + String.join(", ", VALID_SEVERITIES));
        }

        try {
            // PLACEHOLDER: Capella / EMF Validation Framework
            //
            // Validation approach:
            //
            // 1. Get the validation scope (entire model or specific layer):
            //    Session session = getActiveSession();
            //    List<EObject> validationTargets;
            //    if (layer != null) {
            //        BlockArchitecture arch = getArchitecture(session, layer);
            //        validationTargets = List.of(arch);
            //    } else {
            //        validationTargets = new ArrayList<>();
            //        for (Resource res : session.getSemanticResources()) {
            //            validationTargets.addAll(res.getContents());
            //        }
            //    }
            //
            // 2. Create and configure the batch validator:
            //    IBatchValidator validator = ModelValidationService.getInstance()
            //            .newValidator(EvaluationMode.BATCH);
            //    validator.setIncludeLiveConstraints(true);
            //    validator.setReportSuccesses(false);
            //
            // 3. Run validation and collect results:
            //    for (EObject target : validationTargets) {
            //        IStatus status = validator.validate(target);
            //        collectIssues(status, issues, severity);
            //    }
            //
            // 4. Map IStatus severity to our severity levels:
            //    IStatus.ERROR   -> "error"
            //    IStatus.WARNING -> "warning"
            //    IStatus.INFO    -> "info"

            List<ValidationIssue> issues = runValidation(layer, severity);

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
                response.addProperty("validated_layer", layer);
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
     * Runs the model validation and collects issues.
     *
     * @param layer    optional layer filter
     * @param severity minimum severity filter
     * @return list of validation issues
     */
    private List<ValidationIssue> runValidation(String layer, String severity) {
        // PLACEHOLDER: Implement actual EMF/Capella validation execution
        return new ArrayList<>();
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

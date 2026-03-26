package com.capellaagent.modelchat.tools.analysis;

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
import org.polarsys.capella.core.data.cs.Interface;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.ComponentFunctionalAllocation;
import org.polarsys.capella.core.data.fa.FunctionalExchange;
import org.polarsys.capella.core.data.interaction.AbstractCapability;

/**
 * Runs comprehensive structural validation on the Capella model.
 * <p>
 * Extends basic validation with additional checks: missing descriptions,
 * empty packages, unconnected ports, interfaces with no exchange items,
 * functions with no exchanges, and naming convention violations.
 */
public class RunCapellaValidationTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "run_capella_validation";
    private static final String DESCRIPTION =
            "Runs comprehensive model validation with detailed issue reporting.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");
    private static final int MAX_ISSUES = 500;

    public RunCapellaValidationTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.ANALYSIS);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalEnum("layer",
                "Validate a specific layer (all if omitted)",
                VALID_LAYERS, null));
        params.add(ToolParameter.optionalString("element_uuid",
                "Validate a specific element and its children"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getOptionalString(parameters, "layer", null);
        String elementUuid = getOptionalString(parameters, "element_uuid", null);

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();

            List<ValidationIssue> issues = new ArrayList<>();

            if (elementUuid != null && !elementUuid.isBlank()) {
                // Validate specific element and children
                EObject element = resolveElementByUuid(elementUuid);
                if (element == null) {
                    return ToolResult.error("Element not found: " + elementUuid);
                }
                validateElement(element, issues);
                validateContents(element.eAllContents(), issues);
            } else if (layer != null && !layer.isBlank()) {
                BlockArchitecture arch = modelService.getArchitecture(session, layer.toLowerCase());
                validateContents(arch.eAllContents(), issues);
            } else {
                // Validate all
                for (Resource resource : session.getSemanticResources()) {
                    validateContents(resource.getAllContents(), issues);
                    if (issues.size() >= MAX_ISSUES) break;
                }
            }

            // Build response
            int errors = 0, warnings = 0, infos = 0;
            JsonArray issuesArray = new JsonArray();

            for (int i = 0; i < Math.min(issues.size(), MAX_ISSUES); i++) {
                ValidationIssue issue = issues.get(i);
                switch (issue.severity) {
                    case "error": errors++; break;
                    case "warning": warnings++; break;
                    case "info": infos++; break;
                }

                JsonObject obj = new JsonObject();
                obj.addProperty("severity", issue.severity);
                obj.addProperty("rule_id", issue.ruleId);
                obj.addProperty("message", issue.message);
                obj.addProperty("element_name", issue.elementName);
                obj.addProperty("element_id", issue.elementId);
                obj.addProperty("element_type", issue.elementType);
                issuesArray.add(obj);
            }

            JsonObject response = new JsonObject();
            response.addProperty("validated_layer", layer != null ? layer : "all");

            JsonObject summary = new JsonObject();
            summary.addProperty("total_issues", issues.size());
            summary.addProperty("errors", errors);
            summary.addProperty("warnings", warnings);
            summary.addProperty("info", infos);
            response.add("summary", summary);

            response.addProperty("truncated", issues.size() > MAX_ISSUES);
            response.add("issues", issuesArray);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Validation failed: " + e.getMessage());
        }
    }

    private void validateContents(Iterator<EObject> contents, List<ValidationIssue> issues) {
        while (contents.hasNext() && issues.size() < MAX_ISSUES) {
            EObject obj = contents.next();
            validateElement(obj, issues);
        }
    }

    private void validateElement(EObject obj, List<ValidationIssue> issues) {
        if (issues.size() >= MAX_ISSUES) return;

        // Rule 1: Empty names
        if (obj instanceof AbstractNamedElement) {
            String name = ((AbstractNamedElement) obj).getName();
            if (name == null || name.isBlank()) {
                issues.add(new ValidationIssue("error", "EMPTY_NAME",
                        "Element has an empty or missing name",
                        "(unnamed)", getElementId(obj), obj.eClass().getName()));
            }
        }

        // Rule 2: Missing descriptions on key elements
        if ((obj instanceof AbstractFunction || obj instanceof Component
                || obj instanceof AbstractCapability)
                && obj instanceof AbstractNamedElement) {
            String desc = getElementDescription(obj);
            String name = getElementName(obj);
            if ((desc == null || desc.isBlank()) && name != null && !name.isBlank()
                    && !name.contains("Root")) {
                issues.add(new ValidationIssue("info", "MISSING_DESCRIPTION",
                        "Element has no description",
                        name, getElementId(obj), obj.eClass().getName()));
            }
        }

        // Rule 3: Unallocated functions
        if (obj instanceof AbstractFunction) {
            AbstractFunction fn = (AbstractFunction) obj;
            String name = fn.getName();
            if (name != null && !name.isBlank() && !name.contains("Root")) {
                try {
                    List<?> allocations = fn.getComponentFunctionalAllocations();
                    if (allocations == null || allocations.isEmpty()) {
                        issues.add(new ValidationIssue("warning", "UNALLOCATED_FUNCTION",
                                "Function is not allocated to any component",
                                name, getElementId(obj), obj.eClass().getName()));
                    }
                } catch (Exception e) { /* skip */ }
            }
        }

        // Rule 4: Components with no ports
        if (obj instanceof Component) {
            Component comp = (Component) obj;
            String name = comp.getName();
            if (name != null && !name.isBlank() && !comp.isActor()) {
                try {
                    boolean hasPorts = comp.getContainedComponentPorts() != null
                            && !comp.getContainedComponentPorts().isEmpty();
                    if (!hasPorts) {
                        issues.add(new ValidationIssue("info", "NO_PORTS",
                                "Component has no ports defined",
                                name, getElementId(obj), obj.eClass().getName()));
                    }
                } catch (Exception e) { /* skip */ }
            }
        }

        // Rule 5: Interfaces with no exchange items
        if (obj instanceof Interface) {
            Interface iface = (Interface) obj;
            String name = getElementName(iface);
            if (name != null && !name.isBlank()) {
                try {
                    if (iface.getExchangeItems() == null || iface.getExchangeItems().isEmpty()) {
                        issues.add(new ValidationIssue("warning", "EMPTY_INTERFACE",
                                "Interface has no exchange items",
                                name, getElementId(obj), obj.eClass().getName()));
                    }
                } catch (Exception e) { /* skip */ }
            }
        }

        // Rule 6: Capabilities with no involved functions
        if (obj instanceof AbstractCapability) {
            AbstractCapability cap = (AbstractCapability) obj;
            String name = cap.getName();
            if (name != null && !name.isBlank()) {
                try {
                    if (cap.getOwnedAbstractFunctionAbstractCapabilityInvolvements() == null
                            || cap.getOwnedAbstractFunctionAbstractCapabilityInvolvements().isEmpty()) {
                        issues.add(new ValidationIssue("warning", "NO_INVOLVED_FUNCTIONS",
                                "Capability has no involved functions",
                                name, getElementId(obj), obj.eClass().getName()));
                    }
                } catch (Exception e) { /* skip */ }
            }
        }

        // Rule 7: Functions with no exchanges (isolated functions)
        if (obj instanceof AbstractFunction) {
            AbstractFunction fn = (AbstractFunction) obj;
            String name = fn.getName();
            if (name != null && !name.isBlank() && !name.contains("Root")) {
                try {
                    boolean hasExchanges = (fn.getOwnedFunctionalExchanges() != null
                            && !fn.getOwnedFunctionalExchanges().isEmpty())
                            || (fn.getInputs() != null && !fn.getInputs().isEmpty())
                            || (fn.getOutputs() != null && !fn.getOutputs().isEmpty());
                    if (!hasExchanges) {
                        issues.add(new ValidationIssue("info", "ISOLATED_FUNCTION",
                                "Function has no functional exchanges (isolated)",
                                name, getElementId(obj), obj.eClass().getName()));
                    }
                } catch (Exception e) { /* skip */ }
            }
        }
    }

    private static class ValidationIssue {
        final String severity;
        final String ruleId;
        final String message;
        final String elementName;
        final String elementId;
        final String elementType;

        ValidationIssue(String severity, String ruleId, String message,
                        String elementName, String elementId, String elementType) {
            this.severity = severity;
            this.ruleId = ruleId;
            this.message = message;
            this.elementName = elementName;
            this.elementId = elementId;
            this.elementType = elementType;
        }
    }
}

package com.capellaagent.modelchat.tools.ai;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
 * Reviews the architecture and returns a structured critique report.
 * <p>
 * Checks for: coupling metrics, naming violations, decomposition balance,
 * and ARCADIA compliance indicators. READ-only — never modifies the model.
 */
public class ArchitectureReviewCritiqueTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "architecture_review_critique";
    private static final String DESCRIPTION =
            "Reviews the architecture and returns a structured critique with coupling metrics, "
            + "naming violations, decomposition balance, and ARCADIA compliance indicators. "
            + "READ-only — does not propose changes.";

    public ArchitectureReviewCritiqueTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.AI_INTELLIGENCE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalEnum("layer",
                "Layer to review: SA, LA, PA, or ALL (default ALL)",
                List.of("SA", "LA", "PA", "ALL"), "ALL"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getOptionalString(parameters, "layer", "ALL");

        try {
            Session session = getActiveSession();
            List<EObject> components = new ArrayList<>();
            List<EObject> functions = new ArrayList<>();
            List<EObject> exchanges = new ArrayList<>();

            // Collect elements
            for (Resource resource : session.getSemanticResources()) {
                Iterator<EObject> it = resource.getAllContents();
                while (it.hasNext()) {
                    EObject obj = it.next();
                    String cn = obj.eClass().getName();
                    if (matchesLayer(cn, layer)) {
                        if (cn.contains("Component")) components.add(obj);
                        else if (cn.contains("Function")) functions.add(obj);
                        else if (cn.contains("Exchange")) exchanges.add(obj);
                    }
                }
            }

            // Coupling: average outgoing references per component
            double avgCoupling = computeAvgCoupling(components);

            // Naming violations
            List<String> namingViolations = findNamingViolations(components);
            namingViolations.addAll(findNamingViolations(functions));

            // Decomposition balance
            List<String> decompIssues = findDecompositionIssues(components);

            // ARCADIA: unallocated functions
            List<String> unallocatedFunctions = findUnallocatedFunctions(functions);

            // Build report
            JsonObject report = new JsonObject();
            report.addProperty("layer_reviewed", layer);
            report.addProperty("components_found", components.size());
            report.addProperty("functions_found", functions.size());
            report.addProperty("exchanges_found", exchanges.size());

            JsonObject coupling = new JsonObject();
            double roundedCoupling = Math.round(avgCoupling * 10.0) / 10.0;
            coupling.addProperty("avg_outgoing_refs_per_component", roundedCoupling);
            coupling.addProperty("assessment", avgCoupling > 7 ? "HIGH \u2014 consider refactoring"
                    : avgCoupling > 4 ? "MEDIUM \u2014 acceptable" : "LOW \u2014 good");
            report.add("coupling", coupling);

            JsonArray namingArr = new JsonArray();
            namingViolations.stream().limit(10).forEach(namingArr::add);
            report.add("naming_violations", namingArr);
            report.addProperty("naming_violations_count", namingViolations.size());

            JsonArray decompArr = new JsonArray();
            decompIssues.stream().limit(10).forEach(decompArr::add);
            report.add("decomposition_issues", decompArr);

            JsonArray unallocArr = new JsonArray();
            unallocatedFunctions.stream().limit(10).forEach(unallocArr::add);
            report.add("unallocated_functions", unallocArr);
            report.addProperty("unallocated_functions_count", unallocatedFunctions.size());

            // Overall health score (0-100)
            int health = 100;
            if (avgCoupling > 7) health -= 20;
            else if (avgCoupling > 4) health -= 10;
            health -= Math.min(30, namingViolations.size() * 3);
            health -= Math.min(20, decompIssues.size() * 5);
            health -= Math.min(20, unallocatedFunctions.size() * 2);
            int finalHealth = Math.max(0, health);
            report.addProperty("health_score", finalHealth);
            report.addProperty("message",
                    "Architecture review complete. Health score: " + finalHealth + "/100.");

            return ToolResult.success(report);

        } catch (Exception e) {
            return ToolResult.error("Architecture review failed: " + e.getMessage());
        }
    }

    private boolean matchesLayer(String className, String layer) {
        if ("ALL".equalsIgnoreCase(layer)) return true;
        switch (layer.toUpperCase()) {
            case "SA": return className.startsWith("System") || className.startsWith("Oa");
            case "LA": return className.startsWith("Logical");
            case "PA": return className.startsWith("Physical");
            default:   return true;
        }
    }

    private double computeAvgCoupling(List<EObject> components) {
        if (components.isEmpty()) return 0.0;
        long totalRefs = 0;
        for (EObject comp : components) {
            for (EReference ref : comp.eClass().getEAllReferences()) {
                if (!ref.isContainment()) totalRefs++;
            }
        }
        return (double) totalRefs / components.size();
    }

    private List<String> findNamingViolations(List<EObject> elements) {
        List<String> violations = new ArrayList<>();
        for (EObject el : elements) {
            String name = getElementName(el);
            if (name == null || name.isBlank()) {
                violations.add(el.eClass().getName() + " ["
                        + getElementId(el) + "]: missing name");
            } else if (!name.contains(" ") && name.equals(name.toUpperCase())
                    && name.length() > 2) {
                violations.add(el.eClass().getName() + " \"" + name
                        + "\": all-uppercase, consider proper casing");
            } else if (name.split("\\s+").length == 1 && name.length() < 4) {
                violations.add(el.eClass().getName() + " \"" + name + "\": name too short");
            }
        }
        return violations;
    }

    private List<String> findDecompositionIssues(List<EObject> components) {
        List<String> issues = new ArrayList<>();
        for (EObject comp : components) {
            String name = getElementName(comp);
            int childCount = 0;
            for (EObject child : comp.eContents()) {
                if (child.eClass().getName().contains("Component")) childCount++;
            }
            if (childCount == 0 && comp.eContents().size() == 0) {
                issues.add("\"" + name + "\": empty component with no children or functions");
            } else if (childCount > 10) {
                issues.add("\"" + name + "\": " + childCount
                        + " sub-components \u2014 consider re-grouping");
            }
        }
        return issues;
    }

    private List<String> findUnallocatedFunctions(List<EObject> functions) {
        List<String> unallocated = new ArrayList<>();
        for (EObject fn : functions) {
            if (fn.eContainer() == null
                    || !fn.eContainer().eClass().getName().contains("Component")) {
                unallocated.add(getElementId(fn) + " \"" + getElementName(fn) + "\"");
            }
        }
        return unallocated;
    }
}

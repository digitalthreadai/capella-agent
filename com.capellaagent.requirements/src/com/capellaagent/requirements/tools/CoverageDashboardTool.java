package com.capellaagent.requirements.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.sirius.business.api.session.Session;

import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Shows a requirements coverage dashboard.
 * <p>
 * Walks all Requirement elements in the model and counts those that have
 * at least one outgoing trace relation (CapellaOutgoingRelation or similar).
 * Returns coverage percentage, total count, traced count, and the IDs of
 * untraced requirements.
 * <p>
 * This is a read-only tool and does not require dry_run handling.
 */
public class CoverageDashboardTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "coverage_dashboard";
    private static final String DESCRIPTION =
            "Shows the requirements traceability coverage dashboard. "
            + "Reports percentage of requirements with at least one trace link, "
            + "and lists the IDs of untraced requirements.";

    public CoverageDashboardTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.REQUIREMENTS);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        return List.of(); // No parameters needed
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        try {
            Session session = getActiveSession();
            List<EObject> allRequirements = new ArrayList<>();
            List<EObject> traced = new ArrayList<>();
            List<EObject> untraced = new ArrayList<>();

            for (Resource resource : session.getSemanticResources()) {
                org.eclipse.emf.common.util.TreeIterator<EObject> it = resource.getAllContents();
                while (it.hasNext()) {
                    EObject obj = it.next();
                    String cn = obj.eClass().getName();
                    // Include only Requirement leaf objects, not packages/modules/relations
                    if (!cn.contains("Requirement")
                            || cn.contains("Pkg") || cn.contains("Package")
                            || cn.contains("Module") || cn.contains("Relation")) continue;

                    allRequirements.add(obj);
                    if (hasTraceLink(obj)) {
                        traced.add(obj);
                    } else {
                        untraced.add(obj);
                    }
                }
            }

            int total = allRequirements.size();
            int tracedCount = traced.size();
            double pct = total == 0 ? 0.0 : (tracedCount * 100.0 / total);

            JsonObject result = new JsonObject();
            result.addProperty("total_requirements", total);
            result.addProperty("traced_requirements", tracedCount);
            result.addProperty("untraced_requirements", untraced.size());
            result.addProperty("coverage_pct", Math.round(pct * 10.0) / 10.0);

            JsonArray untracedIds = new JsonArray();
            for (EObject req : untraced) {
                untracedIds.add(getReqId(req));
            }
            result.add("untraced_ids", untracedIds);

            if (total == 0) {
                result.addProperty("message",
                        "No requirements found in model. "
                        + "Import requirements first using import_reqif or import_requirements_excel.");
            } else {
                result.addProperty("message",
                        String.format("%.1f%% of %d requirements have trace links (%d traced, %d untraced).",
                                pct, total, tracedCount, untraced.size()));
            }

            return ToolResult.success(result);

        } catch (Exception e) {
            return ToolResult.error("Failed to compute coverage: " + e.getMessage());
        }
    }

    /**
     * Returns true if the requirement has at least one containment-owned child
     * that is a trace relation with a non-null target reference.
     * <p>
     * Only checks containment-owned children to avoid false positives from
     * cross-reference metamodel edges that are not user-created trace links.
     */
    private boolean hasTraceLink(EObject requirement) {
        for (EObject child : requirement.eContents()) {
            String cn = child.eClass().getName();
            if (cn.contains("Relation") || cn.contains("Link") || cn.contains("Trace")) {
                // Verify the relation points to at least one non-null non-self target
                for (EReference ref : child.eClass().getEAllReferences()) {
                    if (ref.isContainment()) continue;
                    try {
                        Object val = child.eGet(ref);
                        if (val instanceof EObject eObj && eObj != requirement) return true;
                        if (val instanceof List<?> list && !list.isEmpty()) return true;
                    } catch (Exception ignored) {}
                }
            }
        }
        return false;
    }

    private String getReqId(EObject obj) {
        EStructuralFeature f = obj.eClass().getEStructuralFeature("ReqIFIdentifier");
        if (f != null) {
            Object v = obj.eGet(f);
            if (v != null && !v.toString().isEmpty()) return v.toString();
        }
        f = obj.eClass().getEStructuralFeature("id");
        if (f != null) {
            Object v = obj.eGet(f);
            if (v != null && !v.toString().isEmpty()) return v.toString();
        }
        return getElementId(obj);
    }
}

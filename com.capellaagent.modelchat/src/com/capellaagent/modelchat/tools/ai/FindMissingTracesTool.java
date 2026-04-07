package com.capellaagent.modelchat.tools.ai;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
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
 * Finds requirements that have no trace links (orphaned requirements).
 * <p>
 * For each orphaned requirement, returns: ID, 60-character text snippet,
 * and suggested element types based on requirement keywords.
 */
public class FindMissingTracesTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "find_missing_traces";
    private static final String DESCRIPTION =
            "Finds requirements with no trace links (orphaned). "
            + "Returns requirement ID, 60-char text snippet, and suggested element types.";

    public FindMissingTracesTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.AI_INTELLIGENCE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        return List.of();
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        try {
            Session session = getActiveSession();
            List<JsonObject> orphans = new ArrayList<>();

            for (Resource resource : session.getSemanticResources()) {
                Iterator<EObject> it = resource.getAllContents();
                while (it.hasNext()) {
                    EObject obj = it.next();
                    String cn = obj.eClass().getName();
                    if (!cn.contains("Requirement") || cn.contains("Pkg")
                            || cn.contains("Module") || cn.contains("Relation")) continue;
                    if (!hasTrace(obj)) {
                        JsonObject orphan = new JsonObject();
                        orphan.addProperty("id", getReqId(obj));
                        String text = getReqText(obj);
                        orphan.addProperty("text_snippet", truncate(text, 60));
                        orphan.add("suggested_element_types", suggestTypes(text));
                        orphans.add(orphan);
                    }
                }
            }

            JsonObject result = new JsonObject();
            result.addProperty("orphaned_count", orphans.size());
            JsonArray arr = new JsonArray();
            orphans.forEach(arr::add);
            result.add("orphaned_requirements", arr);
            result.addProperty("message",
                    orphans.isEmpty()
                    ? "All requirements have at least one trace link."
                    : orphans.size() + " requirement(s) have no trace links.");
            return ToolResult.success(result);

        } catch (Exception e) {
            return ToolResult.error("Failed to find missing traces: " + e.getMessage());
        }
    }

    private boolean hasTrace(EObject req) {
        for (EObject child : req.eContents()) {
            String cn = child.eClass().getName();
            if (cn.contains("Relation") || cn.contains("Trace")) return true;
        }
        return false;
    }

    private String getReqId(EObject obj) {
        EStructuralFeature f = obj.eClass().getEStructuralFeature("ReqIFIdentifier");
        if (f != null) {
            Object v = obj.eGet(f);
            if (v != null) return v.toString();
        }
        return getElementId(obj);
    }

    private String getReqText(EObject obj) {
        EStructuralFeature f = obj.eClass().getEStructuralFeature("ReqIFText");
        if (f != null) {
            Object v = obj.eGet(f);
            if (v != null) return v.toString();
        }
        return getElementDescription(obj);
    }

    private JsonArray suggestTypes(String text) {
        JsonArray types = new JsonArray();
        if (text == null || text.isEmpty()) {
            types.add("LogicalComponent");
            return types;
        }
        String lower = text.toLowerCase();
        if (lower.contains("function") || lower.contains("process") || lower.contains("compute")) {
            types.add("LogicalFunction");
        }
        if (lower.contains("interface") || lower.contains("exchange")
                || lower.contains("communicate")) {
            types.add("ComponentExchange");
        }
        if (lower.contains("component") || lower.contains("system")
                || lower.contains("subsystem")) {
            types.add("LogicalComponent");
        }
        if (types.size() == 0) types.add("LogicalComponent");
        return types;
    }
}

package com.capellaagent.modelchat.tools.ai;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.sirius.business.api.session.Session;

import com.capellaagent.core.staging.InMemoryStagingArea;
import com.capellaagent.core.staging.ProposedChange;
import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonObject;

/**
 * Reads requirements and existing model elements, proposes an architecture
 * structure to satisfy untraced requirements, and stages it for review.
 * <p>
 * Results in a diff preview + diff_id, exactly like {@link ProposeArchitectureChangesTool}
 * but fully automated from requirements.
 */
public class ProposeArchitectureFromRequirementsTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "propose_architecture_from_requirements";
    private static final String DESCRIPTION =
            "Reads requirements and proposes an architecture structure to satisfy them. "
            + "Returns a diff preview + diff_id for user approval. "
            + "Use apply_architecture_diff to commit. Maximum 5 proposals per call.";

    public ProposeArchitectureFromRequirementsTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.AI_INTELLIGENCE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("session_id",
                "The current chat session ID"));
        params.add(ToolParameter.optionalEnum("layer",
                "Target layer: SA, LA, or PA. Default: LA",
                List.of("SA", "LA", "PA"), "LA"));
        params.add(ToolParameter.optionalString("parent_uuid",
                "UUID of the parent element to add new elements to. If omitted, uses model root."));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String sessionId = getRequiredString(parameters, "session_id");
        String layer = getOptionalString(parameters, "layer", "LA");
        String parentUuid = getOptionalString(parameters, "parent_uuid", null);

        try {
            Session session = getActiveSession();
            String projectName;
            try {
                projectName = session.getSessionResource().getURI().lastSegment();
            } catch (Exception e) {
                projectName = "unknown";
            }

            // Read untraced requirements (paged, max 20)
            List<RequirementSummary> untracedReqs = readUntracedRequirements(session, 20);

            if (untracedReqs.isEmpty()) {
                JsonObject result = new JsonObject();
                result.addProperty("message",
                        "All requirements appear to have trace links, or no requirements found. "
                        + "Nothing to propose.");
                return ToolResult.success(result);
            }

            // Determine component type for the target layer
            String componentType;
            switch (layer.toUpperCase()) {
                case "SA":
                    componentType = "SystemComponent";
                    break;
                case "PA":
                    componentType = "PhysicalComponent";
                    break;
                default:
                    componentType = "LogicalComponent"; // LA
                    break;
            }

            // Validate parent UUID if provided
            if (parentUuid != null && !parentUuid.isEmpty()) {
                EObject parent = resolveElementByUuid(parentUuid);
                if (parent == null) {
                    return ToolResult.error("parent_uuid '" + parentUuid + "' not found in model.");
                }
            }

            // Build up to 5 proposed elements (one per untraced requirement group)
            List<ProposedChange> changes = new ArrayList<>();
            int limit = Math.min(5, untracedReqs.size());
            for (int i = 0; i < limit; i++) {
                RequirementSummary req = untracedReqs.get(i);
                String componentName = deriveComponentName(req.text(), componentType, i);
                String rationale = req.id() + ": "
                        + (req.text() != null && req.text().length() > 60
                                ? req.text().substring(0, 60) + "…"
                                : (req.text() != null ? req.text() : ""));
                changes.add(new ProposedChange(
                        "CREATE",
                        layer,
                        componentType,
                        componentName,
                        parentUuid != null ? parentUuid : "",
                        null,
                        rationale
                ));
            }

            // Stage the diff
            String diffId = InMemoryStagingArea.getInstance().stage(sessionId, projectName, changes);

            // Format preview
            StringBuilder preview = new StringBuilder();
            for (ProposedChange change : changes) {
                preview.append("+ [CREATE] ").append(change.elementType())
                        .append(" \"").append(change.name()).append("\"");
                if (change.parentUuid() != null && !change.parentUuid().isEmpty()) {
                    preview.append(" (parent: ").append(change.parentUuid()).append(")");
                }
                preview.append(" \u2014 Rationale: ").append(change.rationale()).append("\n");
            }

            JsonObject result = new JsonObject();
            result.addProperty("diff_id", diffId);
            result.addProperty("session_id", sessionId);
            result.addProperty("layer", layer);
            result.addProperty("changes_staged", changes.size());
            result.addProperty("untraced_requirements_found", untracedReqs.size());
            result.addProperty("diff_preview", preview.toString());
            result.addProperty("message",
                    changes.size() + " " + componentType + "(s) proposed for "
                    + untracedReqs.size() + " untraced requirement(s). "
                    + "Review above, then call apply_architecture_diff with diff_id=" + diffId
                    + " to commit.");

            return ToolResult.success(result);

        } catch (Exception e) {
            return ToolResult.error("Failed to propose architecture: " + e.getMessage());
        }
    }

    private List<RequirementSummary> readUntracedRequirements(Session session, int limit) {
        List<RequirementSummary> result = new ArrayList<>();
        for (Resource resource : session.getSemanticResources()) {
            Iterator<EObject> it = resource.getAllContents();
            while (it.hasNext() && result.size() < limit) {
                EObject obj = it.next();
                String cn = obj.eClass().getName();
                if (!cn.contains("Requirement") || cn.contains("Pkg")
                        || cn.contains("Module") || cn.contains("Relation")) continue;
                if (!hasTrace(obj)) {
                    String id = getElemId(obj);
                    String text = getElemText(obj);
                    result.add(new RequirementSummary(id, text));
                }
            }
        }
        return result;
    }

    private boolean hasTrace(EObject req) {
        for (EObject child : req.eContents()) {
            String cn = child.eClass().getName();
            if (cn.contains("Relation") || cn.contains("Trace")) return true;
        }
        return false;
    }

    private String getElemId(EObject obj) {
        EStructuralFeature f = obj.eClass().getEStructuralFeature("ReqIFIdentifier");
        if (f != null) {
            Object v = obj.eGet(f);
            if (v != null) return v.toString();
        }
        return getElementId(obj);
    }

    private String getElemText(EObject obj) {
        EStructuralFeature f = obj.eClass().getEStructuralFeature("ReqIFText");
        if (f != null) {
            Object v = obj.eGet(f);
            if (v != null) return v.toString();
        }
        return getElementDescription(obj);
    }

    private String deriveComponentName(String reqText, String type, int index) {
        if (reqText == null || reqText.isBlank()) return type + "_" + (index + 1);
        // Take first 3 significant words
        String[] words = reqText.trim().split("\\s+");
        StringBuilder name = new StringBuilder();
        int wordCount = 0;
        for (String w : words) {
            if (w.length() > 3 && !w.equalsIgnoreCase("the") && !w.equalsIgnoreCase("shall")
                    && !w.equalsIgnoreCase("must") && !w.equalsIgnoreCase("will")) {
                name.append(Character.toUpperCase(w.charAt(0)))
                    .append(w.substring(1).toLowerCase());
                if (++wordCount >= 3) break;
            }
        }
        return name.length() > 0 ? name.toString() : type + "_" + (index + 1);
    }

    private record RequirementSummary(String id, String text) {}
}

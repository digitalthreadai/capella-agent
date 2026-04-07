package com.capellaagent.modelchat.tools.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;

import com.capellaagent.core.staging.InMemoryStagingArea;
import com.capellaagent.core.staging.ProposedChange;
import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * READ-category tool that proposes architecture changes by staging them for user review.
 * <p>
 * The LLM provides a JSON array of proposed changes. This tool validates each change
 * (UUID existence, layer/type legality), stages the batch in {@link InMemoryStagingArea},
 * and returns a human-readable diff preview + {@code diff_id} token.
 * <p>
 * <b>This tool NEVER modifies the model.</b> Use {@link ApplyArchitectureDiffTool}
 * to commit a staged diff after user approval.
 */
public class ProposeArchitectureChangesTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "propose_architecture_changes";
    private static final String DESCRIPTION =
            "Proposes architecture changes (CREATE/MODIFY/DELETE) as a diff preview. "
            + "Accepts up to 5 changes per call. Returns a diff_id for the user to approve. "
            + "This tool NEVER modifies the model — use apply_architecture_diff to commit.";

    private static final int MAX_CHANGES = 5;

    public ProposeArchitectureChangesTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.AI_INTELLIGENCE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("session_id",
                "The current chat session ID (used to scope the staged diff)"));
        params.add(ToolParameter.requiredString("changes",
                "JSON array of changes: [{op, layer, type, name, parent_uuid, rationale}]"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String sessionId = getRequiredString(parameters, "session_id");
        String changesJson = getRequiredString(parameters, "changes");

        // Get active project name for TTL check in apply step
        String projectName;
        try {
            projectName = getActiveSession() != null
                    ? getActiveSession().getSessionResource().getURI().lastSegment()
                    : "unknown";
        } catch (Exception e) {
            projectName = "unknown";
        }

        // Parse changes
        JsonArray changesArray;
        try {
            changesArray = JsonParser.parseString(changesJson).getAsJsonArray();
        } catch (Exception e) {
            return ToolResult.error("Invalid JSON in 'changes' parameter: " + e.getMessage());
        }

        if (changesArray.size() > MAX_CHANGES) {
            return ToolResult.error(
                    "Too many changes in one call (" + changesArray.size() + "). "
                    + "Maximum is " + MAX_CHANGES + ". Split into multiple proposals.");
        }

        if (changesArray.isEmpty()) {
            return ToolResult.error("No changes provided.");
        }

        // Parse and validate
        List<ProposedChange> changes = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        StringBuilder diffPreview = new StringBuilder();

        for (int i = 0; i < changesArray.size(); i++) {
            JsonObject ch = changesArray.get(i).getAsJsonObject();
            String op = getStr(ch, "op", "CREATE");
            String layer = getStr(ch, "layer", "");
            String type = getStr(ch, "type", "");
            String name = getStr(ch, "name", "");
            String parentUuid = getStr(ch, "parent_uuid", null);
            String targetUuid = getStr(ch, "target_uuid", null);
            String rationale = getStr(ch, "rationale", "");

            // Validate parent UUID
            if (parentUuid != null && !parentUuid.isEmpty()) {
                try {
                    EObject parent = resolveElementByUuid(parentUuid);
                    if (parent == null) {
                        errors.add("Change " + (i + 1) + ": parent_uuid '" + parentUuid
                                + "' not found in model");
                        continue;
                    }
                } catch (Exception e) {
                    errors.add("Change " + (i + 1) + ": parent_uuid validation failed: "
                            + e.getMessage());
                    continue;
                }
            }

            // Validate layer/type combination
            String validationError = validateLayerType(layer, type);
            if (validationError != null) {
                errors.add("Change " + (i + 1) + ": " + validationError);
                continue;
            }

            ProposedChange change = new ProposedChange(op, layer, type, name,
                    parentUuid, targetUuid, rationale);
            changes.add(change);

            // Format diff line
            switch (op.toUpperCase()) {
                case "CREATE":
                    diffPreview.append("+ [CREATE] ").append(type).append(" \"").append(name).append("\"");
                    break;
                case "MODIFY":
                    diffPreview.append("~ [MODIFY] ").append(type).append(" \"").append(name).append("\"");
                    break;
                case "DELETE":
                    diffPreview.append("- [DELETE] ").append(type).append(" \"").append(name).append("\"");
                    break;
                default:
                    diffPreview.append("  [").append(op).append("] ").append(type)
                            .append(" \"").append(name).append("\"");
                    break;
            }
            if (parentUuid != null && !parentUuid.isEmpty()) {
                diffPreview.append(" (parent: ").append(parentUuid).append(")");
            }
            if (rationale != null && !rationale.isEmpty()) {
                diffPreview.append(" \u2014 Rationale: ").append(rationale);
            }
            diffPreview.append("\n");
        }

        if (!errors.isEmpty() && changes.isEmpty()) {
            return ToolResult.error("All changes failed validation:\n" + String.join("\n", errors));
        }

        // Stage the diff using the 4-arg overload (caller supplies diffId)
        String diffId = InMemoryStagingArea.getInstance().stage(sessionId, projectName, changes);

        // Build result
        JsonObject result = new JsonObject();
        result.addProperty("diff_id", diffId);
        result.addProperty("session_id", sessionId);
        result.addProperty("changes_staged", changes.size());
        result.addProperty("diff_preview", diffPreview.toString());

        if (!errors.isEmpty()) {
            JsonArray errArray = new JsonArray();
            errors.forEach(errArray::add);
            result.add("validation_errors", errArray);
        }

        result.addProperty("message",
                changes.size() + " change(s) staged for review. diff_id=" + diffId
                + ". Ask the user to approve or discard before calling apply_architecture_diff.");

        return ToolResult.success(result);
    }

    private String validateLayerType(String layer, String type) {
        if (layer == null || layer.isEmpty()) return null; // no layer specified, allow
        if (type == null || type.isEmpty()) return null;   // no type specified, allow

        String layerLower = layer.toLowerCase();
        String typeLower = type.toLowerCase();

        // Physical layer should not contain Logical-only types
        if (layerLower.contains("physical") && typeLower.startsWith("logical")) {
            return "type '" + type + "' is not valid in layer '" + layer
                    + "'. Use PhysicalComponent instead.";
        }
        // Logical layer should not contain Physical-only types
        if (layerLower.contains("logical") && typeLower.startsWith("physical")) {
            return "type '" + type + "' is not valid in layer '" + layer
                    + "'. Use LogicalComponent instead.";
        }
        return null; // valid
    }

    private String getStr(JsonObject obj, String key, String defaultVal) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return defaultVal;
        return el.getAsString();
    }
}

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
import com.google.gson.JsonObject;

/**
 * Suggests a decomposition of a component into sub-components.
 * <p>
 * Returns a staged diff (diff_id) so the user can approve before anything is written.
 */
public class SuggestDecompositionTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "suggest_decomposition";
    private static final String DESCRIPTION =
            "Suggests a decomposition of a component into sub-components. "
            + "Returns a diff preview + diff_id for user approval via apply_architecture_diff. "
            + "Does NOT modify the model.";

    public SuggestDecompositionTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.AI_INTELLIGENCE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("component_uuid",
                "UUID of the component to decompose"));
        params.add(ToolParameter.requiredString("session_id",
                "The current chat session ID"));
        params.add(ToolParameter.optionalStringWithDefault("max_children",
                "Maximum number of sub-components to suggest (default 5)", "5"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String componentUuid = getRequiredString(parameters, "component_uuid");
        String sessionId = getRequiredString(parameters, "session_id");
        String maxChildrenStr = getOptionalString(parameters, "max_children", "5");

        int maxChildren;
        try {
            maxChildren = Math.min(5, Integer.parseInt(maxChildrenStr));
        } catch (NumberFormatException e) {
            maxChildren = 5;
        }

        try {
            EObject component = resolveElementByUuid(componentUuid);
            if (component == null) {
                return ToolResult.error("Component not found with UUID: " + componentUuid);
            }

            String componentName = getElementName(component);
            String componentType = component.eClass().getName();

            // Same type for sub-components (LogicalComponent → LogicalComponent, etc.)
            String childType = componentType;

            // Generate functional decomposition sub-names
            String[] suffixes = {"Management", "Processing", "Interface", "Storage", "Control"};
            List<ProposedChange> changes = new ArrayList<>();
            for (int i = 0; i < maxChildren; i++) {
                String suffix = i < suffixes.length ? suffixes[i] : ("Sub" + (i + 1));
                changes.add(new ProposedChange(
                        "CREATE",
                        "",
                        childType,
                        componentName + suffix,
                        componentUuid,
                        null,
                        "Decomposition of " + componentName
                ));
            }

            String projectName;
            try {
                projectName = getActiveSession().getSessionResource().getURI().lastSegment();
            } catch (Exception e) {
                projectName = "unknown";
            }

            String diffId = InMemoryStagingArea.getInstance().stage(sessionId, projectName, changes);

            StringBuilder preview = new StringBuilder();
            for (ProposedChange c : changes) {
                preview.append("+ [CREATE] ").append(c.elementType()).append(" \"")
                        .append(c.name()).append("\" (parent: ").append(componentUuid)
                        .append(")\n");
            }

            JsonObject result = new JsonObject();
            result.addProperty("diff_id", diffId);
            result.addProperty("component_name", componentName);
            result.addProperty("component_type", componentType);
            result.addProperty("changes_staged", changes.size());
            result.addProperty("diff_preview", preview.toString());
            result.addProperty("message",
                    changes.size() + " sub-components proposed for " + componentName
                    + ". Call apply_architecture_diff with diff_id=" + diffId
                    + " to create them.");
            return ToolResult.success(result);

        } catch (Exception e) {
            return ToolResult.error("Failed to suggest decomposition: " + e.getMessage());
        }
    }
}

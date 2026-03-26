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

/**
 * Retrieves the containment hierarchy for a model element.
 * <p>
 * Can traverse upward (ancestors), downward (descendants), or both directions.
 * The result is a tree structure rooted at the target element, with configurable
 * maximum depth to prevent excessive traversal in deeply nested models.
 *
 * <h3>Tool Specification</h3>
 * <ul>
 *   <li><b>Name:</b> get_element_hierarchy</li>
 *   <li><b>Category:</b> model_read</li>
 *   <li><b>Parameters:</b>
 *     <ul>
 *       <li>{@code uuid} (string, required) - UUID of the root element</li>
 *       <li>{@code direction} (string, optional, default "down") - Traversal direction: up, down, or both</li>
 *       <li>{@code max_depth} (integer, optional, default 5) - Maximum traversal depth</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class GetHierarchyTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "get_element_hierarchy";
    private static final String DESCRIPTION =
            "Returns the containment hierarchy tree for a model element. "
            + "Supports traversal upward (ancestors), downward (descendants), or both.";

    private static final List<String> VALID_DIRECTIONS = List.of("up", "down", "both");
    private static final int MAX_ALLOWED_DEPTH = 20;

    public GetHierarchyTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("uuid",
                "UUID of the element to use as the hierarchy root"));
        params.add(ToolParameter.optionalString("direction",
                "Traversal direction: up (ancestors), down (descendants), both (default: down)"));
        params.add(ToolParameter.optionalInteger("max_depth",
                "Maximum depth of hierarchy to traverse (default: 5, max: 20)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String uuid = getRequiredString(parameters, "uuid");
        String direction = getOptionalString(parameters, "direction", "down").toLowerCase();
        int maxDepth = getOptionalInt(parameters, "max_depth", 5);

        if (uuid.isBlank()) {
            return ToolResult.error("Parameter 'uuid' must not be empty");
        }

        if (!VALID_DIRECTIONS.contains(direction)) {
            return ToolResult.error("Invalid direction '" + direction
                    + "'. Must be one of: " + String.join(", ", VALID_DIRECTIONS));
        }

        maxDepth = Math.max(1, Math.min(maxDepth, MAX_ALLOWED_DEPTH));

        try {
            // Thread safety: hierarchy traversal should ideally be wrapped in a
            // read-exclusive transaction via TransactionalEditingDomain.runExclusive()
            // to prevent concurrent modification. Currently safe because the ChatJob
            // orchestration loop is single-threaded per conversation.
            EObject element = resolveElementByUuid(uuid);
            if (element == null) {
                return ToolResult.error("Element not found with UUID: " + uuid);
            }

            JsonObject response = new JsonObject();
            response.addProperty("uuid", uuid);
            response.addProperty("name", getElementName(element));
            response.addProperty("type", element.eClass().getName());
            response.addProperty("direction", direction);
            response.addProperty("max_depth", maxDepth);

            if ("up".equals(direction) || "both".equals(direction)) {
                JsonArray ancestors = buildAncestorChain(element, maxDepth);
                response.add("ancestors", ancestors);
            }

            if ("down".equals(direction) || "both".equals(direction)) {
                JsonObject subtree = buildSubtree(element, maxDepth, 0);
                response.add("subtree", subtree);
            }

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to get hierarchy: " + e.getMessage());
        }
    }

    /**
     * Builds the ancestor chain by walking up the containment hierarchy.
     *
     * @param element  the starting element
     * @param maxDepth maximum ancestors to include
     * @return a JsonArray of ancestors from immediate parent to root
     */
    private JsonArray buildAncestorChain(EObject element, int maxDepth) {
        JsonArray ancestors = new JsonArray();
        EObject current = element.eContainer();
        int depth = 0;

        while (current != null && depth < maxDepth) {
            JsonObject ancestorObj = new JsonObject();
            ancestorObj.addProperty("name", getElementName(current));
            ancestorObj.addProperty("uuid", getElementId(current));
            ancestorObj.addProperty("type", current.eClass().getName());
            ancestorObj.addProperty("depth", depth + 1);
            ancestors.add(ancestorObj);

            current = current.eContainer();
            depth++;
        }

        return ancestors;
    }

    /**
     * Recursively builds a subtree rooted at the given element.
     *
     * @param element      the root element
     * @param maxDepth     maximum recursion depth
     * @param currentDepth the current recursion depth
     * @return a JsonObject representing the subtree
     */
    private JsonObject buildSubtree(EObject element, int maxDepth, int currentDepth) {
        JsonObject node = new JsonObject();
        node.addProperty("name", getElementName(element));
        node.addProperty("uuid", getElementId(element));
        node.addProperty("type", element.eClass().getName());

        if (currentDepth < maxDepth) {
            JsonArray childNodes = new JsonArray();
            for (EObject child : element.eContents()) {
                childNodes.add(buildSubtree(child, maxDepth, currentDepth + 1));
            }
            node.add("children", childNodes);
            node.addProperty("child_count", childNodes.size());
        } else {
            // At max depth, just report how many children exist
            int childCount = element.eContents().size();
            node.addProperty("child_count", childCount);
            if (childCount > 0) {
                node.addProperty("truncated", true);
            }
        }

        return node;
    }
}

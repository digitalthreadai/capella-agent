package com.capellaagent.modelchat.tools.write;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.security.InputValidator;
import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.sirius.business.api.session.Session;

/**
 * Reorders children within a container element.
 * <p>
 * Given a container UUID and an ordered list of child UUIDs, rearranges
 * the children in the container's containment reference to match the specified order.
 * This is useful for controlling the display order in diagrams and reports.
 */
public class ReorderElementsTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "reorder_elements";
    private static final String DESCRIPTION =
            "Reorders children within a container to match the specified UUID order.";

    public ReorderElementsTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("container_uuid",
                "UUID of the container element whose children to reorder"));
        params.add(ToolParameter.requiredString("child_uuids",
                "Comma-separated UUIDs of children in the desired order. "
                + "Children not listed will be placed at the end in their current order."));
        return params;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String containerUuid = getRequiredString(parameters, "container_uuid");
        String childUuidsStr = getRequiredString(parameters, "child_uuids");

        try {
            Session session = getActiveSession();

            EObject container = resolveElementByUuid(containerUuid);
            if (container == null) {
                return ToolResult.error("Container not found: " + containerUuid);
            }

            // Parse child UUIDs
            List<String> orderedUuids = new ArrayList<>();
            for (String uid : childUuidsStr.split(",")) {
                String trimmed = uid.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        orderedUuids.add(InputValidator.validateUuid(trimmed));
                    } catch (IllegalArgumentException e) {
                        return ToolResult.error("Invalid UUID in list: " + trimmed);
                    }
                }
            }

            if (orderedUuids.isEmpty()) {
                return ToolResult.error("No valid child UUIDs provided");
            }

            // Find the containment reference that holds these children
            // We check all containment references on the container
            EReference targetRef = null;
            EList<EObject> targetList = null;

            // Resolve the first child to determine which containment reference it's in
            EObject firstChild = resolveElementByUuid(orderedUuids.get(0));
            if (firstChild == null) {
                return ToolResult.error("Child element not found: " + orderedUuids.get(0));
            }

            // Find the containment feature of the first child
            EReference containmentRef = firstChild.eContainmentFeature();
            if (containmentRef == null || firstChild.eContainer() != container) {
                return ToolResult.error("Element " + orderedUuids.get(0)
                        + " is not a direct child of the specified container");
            }

            Object refValue = container.eGet(containmentRef);
            if (!(refValue instanceof EList)) {
                return ToolResult.error("Containment reference '"
                        + containmentRef.getName() + "' is not a list; cannot reorder");
            }

            targetRef = containmentRef;
            targetList = (EList<EObject>) refValue;

            // Resolve all child objects in the requested order
            List<EObject> orderedChildren = new ArrayList<>();
            for (String uid : orderedUuids) {
                EObject child = resolveElementByUuid(uid);
                if (child == null) {
                    return ToolResult.error("Child element not found: " + uid);
                }
                if (!targetList.contains(child)) {
                    return ToolResult.error("Element " + uid
                            + " is not in the same containment list as the first child");
                }
                orderedChildren.add(child);
            }

            final EList<EObject> finalList = targetList;
            final List<EObject> finalOrdered = orderedChildren;
            final int[] moveCount = {0};

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Reorder children in '" + getElementName(container) + "'") {
                @Override
                protected void doExecute() {
                    // Move each element to its target position
                    for (int i = 0; i < finalOrdered.size(); i++) {
                        EObject child = finalOrdered.get(i);
                        int currentIndex = finalList.indexOf(child);
                        if (currentIndex != i) {
                            finalList.move(i, currentIndex);
                            moveCount[0]++;
                        }
                    }
                }
            });

            // Build response showing the new order
            JsonArray newOrder = new JsonArray();
            for (EObject child : targetList) {
                JsonObject entry = new JsonObject();
                entry.addProperty("name", getElementName(child));
                entry.addProperty("id", getElementId(child));
                entry.addProperty("type", child.eClass().getName());
                newOrder.add(entry);
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "reordered");
            response.addProperty("container_name", getElementName(container));
            response.addProperty("container_id", getElementId(container));
            response.addProperty("containment_feature", targetRef.getName());
            response.addProperty("total_children", targetList.size());
            response.addProperty("moves_performed", moveCount[0]);
            response.add("new_order", newOrder);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to reorder elements: " + e.getMessage());
        }
    }
}

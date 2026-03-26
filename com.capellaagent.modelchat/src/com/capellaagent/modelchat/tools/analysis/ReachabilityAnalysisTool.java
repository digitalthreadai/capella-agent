package com.capellaagent.modelchat.tools.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.FunctionalExchange;

/**
 * BFS reachability analysis through functional exchanges between two elements.
 */
public class ReachabilityAnalysisTool extends AbstractCapellaTool {

    public ReachabilityAnalysisTool() {
        super("reachability_analysis",
                "Finds a path between two elements via exchanges (BFS).",
                ToolCategory.ANALYSIS);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("source_uuid",
                "UUID of the source element"));
        params.add(ToolParameter.requiredString("target_uuid",
                "UUID of the target element"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String sourceUuid = getRequiredString(parameters, "source_uuid");
        String targetUuid = getRequiredString(parameters, "target_uuid");

        try {
            EObject sourceObj = resolveElementByUuid(sourceUuid);
            if (sourceObj == null) {
                return ToolResult.error("Source not found: " + sourceUuid);
            }

            EObject targetObj = resolveElementByUuid(targetUuid);
            if (targetObj == null) {
                return ToolResult.error("Target not found: " + targetUuid);
            }

            // BFS through functional exchanges
            if (!(sourceObj instanceof AbstractFunction)) {
                return ToolResult.error("Source must be a function, got: " + sourceObj.eClass().getName());
            }
            if (!(targetObj instanceof AbstractFunction)) {
                return ToolResult.error("Target must be a function, got: " + targetObj.eClass().getName());
            }

            AbstractFunction source = (AbstractFunction) sourceObj;
            AbstractFunction target = (AbstractFunction) targetObj;

            // BFS
            Queue<AbstractFunction> queue = new LinkedList<>();
            Set<String> visited = new HashSet<>();
            Map<String, String> parentMap = new HashMap<>(); // child_id -> parent_id

            String sourceId = getElementId(sourceObj);
            String targetId = getElementId(targetObj);

            queue.add(source);
            visited.add(sourceId);
            boolean found = false;

            while (!queue.isEmpty() && !found) {
                AbstractFunction current = queue.poll();
                String currentId = getElementId(current);

                // Follow outgoing functional exchanges
                for (org.polarsys.capella.common.data.activity.ActivityEdge edge : current.getOutgoing()) {
                    if (!(edge instanceof FunctionalExchange)) continue;
                    FunctionalExchange fe = (FunctionalExchange) edge;
                    EObject feTarget = fe.getTarget();
                    if (!(feTarget instanceof AbstractFunction)) continue;
                    AbstractFunction neighbor = (AbstractFunction) feTarget;
                    if (neighbor == null) continue;
                    String neighborId = getElementId(neighbor);

                    if (!visited.contains(neighborId)) {
                        visited.add(neighborId);
                        parentMap.put(neighborId, currentId);
                        queue.add(neighbor);

                        if (neighborId.equals(targetId)) {
                            found = true;
                            break;
                        }
                    }
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("source_name", getElementName(sourceObj));
            response.addProperty("target_name", getElementName(targetObj));
            response.addProperty("reachable", found);

            if (found) {
                // Reconstruct path
                List<String> pathIds = new ArrayList<>();
                String current = targetId;
                while (current != null) {
                    pathIds.add(current);
                    current = parentMap.get(current);
                }
                Collections.reverse(pathIds);

                JsonArray pathArray = new JsonArray();
                for (String id : pathIds) {
                    EObject el = resolveElementByUuid(id);
                    if (el != null) {
                        JsonObject node = new JsonObject();
                        node.addProperty("name", getElementName(el));
                        node.addProperty("id", id);
                        pathArray.add(node);
                    }
                }
                response.add("path", pathArray);
                response.addProperty("path_length", pathIds.size());
            }

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed reachability analysis: " + e.getMessage());
        }
    }
}

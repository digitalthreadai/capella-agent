package com.capellaagent.modelchat.tools.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.capellaagent.core.capella.CapellaModelService;
import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.ComponentExchange;
import org.polarsys.capella.core.data.fa.FunctionalExchange;

/**
 * Detects cycles in the model's exchange/dependency graph.
 * <p>
 * Builds an adjacency graph from functional exchanges, component exchanges,
 * or dependencies, then runs DFS-based cycle detection to find strongly
 * connected components (cycles).
 */
public class DetectCyclesTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "detect_cycles";
    private static final String DESCRIPTION =
            "Detects cycles in functional or component exchange graphs.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");
    private static final List<String> VALID_TYPES = List.of(
            "functional_exchange", "component_exchange", "dependency");

    public DetectCyclesTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.ANALYSIS);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS));
        params.add(ToolParameter.optionalEnum("relationship_type",
                "Relationship type: functional_exchange, component_exchange, dependency",
                VALID_TYPES, "functional_exchange"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        String relType = getOptionalString(parameters, "relationship_type", "functional_exchange");

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();
            BlockArchitecture architecture = modelService.getArchitecture(session, layer);

            // Build adjacency graph
            Map<String, GraphNode> nodes = new HashMap<>();
            Map<String, List<String>> adjacency = new HashMap<>();

            Iterator<EObject> allContents = architecture.eAllContents();
            while (allContents.hasNext()) {
                EObject obj = allContents.next();

                if ("functional_exchange".equals(relType) && obj instanceof FunctionalExchange) {
                    FunctionalExchange fe = (FunctionalExchange) obj;
                    EObject source = fe.getSource();
                    EObject target = fe.getTarget();

                    // Navigate from ports to functions
                    if (source != null) source = getOwningFunction(source);
                    if (target != null) target = getOwningFunction(target);

                    if (source != null && target != null) {
                        String srcId = getElementId(source);
                        String tgtId = getElementId(target);
                        if (!srcId.equals(tgtId)) {
                            nodes.putIfAbsent(srcId, new GraphNode(srcId, getElementName(source),
                                    source.eClass().getName()));
                            nodes.putIfAbsent(tgtId, new GraphNode(tgtId, getElementName(target),
                                    target.eClass().getName()));
                            adjacency.computeIfAbsent(srcId, k -> new ArrayList<>()).add(tgtId);
                        }
                    }

                } else if ("component_exchange".equals(relType) && obj instanceof ComponentExchange) {
                    ComponentExchange ce = (ComponentExchange) obj;
                    EObject source = ce.getSource();
                    EObject target = ce.getTarget();

                    if (source != null && !(source instanceof Component)) {
                        source = source.eContainer();
                    }
                    if (target != null && !(target instanceof Component)) {
                        target = target.eContainer();
                    }

                    if (source instanceof Component && target instanceof Component) {
                        String srcId = getElementId(source);
                        String tgtId = getElementId(target);
                        if (!srcId.equals(tgtId)) {
                            nodes.putIfAbsent(srcId, new GraphNode(srcId, getElementName(source),
                                    source.eClass().getName()));
                            nodes.putIfAbsent(tgtId, new GraphNode(tgtId, getElementName(target),
                                    target.eClass().getName()));
                            adjacency.computeIfAbsent(srcId, k -> new ArrayList<>()).add(tgtId);
                        }
                    }
                }
            }

            // Run DFS-based cycle detection
            List<List<String>> cycles = findCycles(adjacency, nodes.keySet());

            // Build response
            JsonArray cyclesArray = new JsonArray();
            for (List<String> cycle : cycles) {
                JsonArray cycleArray = new JsonArray();
                for (String nodeId : cycle) {
                    GraphNode node = nodes.get(nodeId);
                    if (node != null) {
                        JsonObject nodeObj = new JsonObject();
                        nodeObj.addProperty("name", node.name);
                        nodeObj.addProperty("id", node.id);
                        nodeObj.addProperty("type", node.type);
                        cycleArray.add(nodeObj);
                    }
                }
                cyclesArray.add(cycleArray);
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("relationship_type", relType);
            response.addProperty("node_count", nodes.size());
            response.addProperty("has_cycles", !cycles.isEmpty());
            response.addProperty("cycle_count", cycles.size());
            response.add("cycles", cyclesArray);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to detect cycles: " + e.getMessage());
        }
    }

    /**
     * Navigates from a port to its owning function.
     */
    private EObject getOwningFunction(EObject portOrFunction) {
        if (portOrFunction instanceof AbstractFunction) {
            return portOrFunction;
        }
        // Navigate up to find the owning function
        EObject current = portOrFunction;
        while (current != null) {
            if (current instanceof AbstractFunction) {
                return current;
            }
            current = current.eContainer();
        }
        return null;
    }

    /**
     * DFS-based cycle detection. Returns all cycles found.
     */
    private List<List<String>> findCycles(Map<String, List<String>> adjacency, Set<String> allNodes) {
        List<List<String>> cycles = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();
        LinkedList<String> stack = new LinkedList<>();

        for (String node : allNodes) {
            if (!visited.contains(node)) {
                dfs(node, adjacency, visited, inStack, stack, cycles);
            }
        }

        return cycles;
    }

    private void dfs(String node, Map<String, List<String>> adjacency,
                       Set<String> visited, Set<String> inStack,
                       LinkedList<String> stack, List<List<String>> cycles) {
        visited.add(node);
        inStack.add(node);
        stack.push(node);

        List<String> neighbors = adjacency.getOrDefault(node, List.of());
        for (String neighbor : neighbors) {
            if (!visited.contains(neighbor)) {
                dfs(neighbor, adjacency, visited, inStack, stack, cycles);
            } else if (inStack.contains(neighbor)) {
                // Found a cycle - extract it
                List<String> cycle = new ArrayList<>();
                boolean found = false;
                for (String s : stack) {
                    if (s.equals(neighbor)) found = true;
                    if (found) cycle.add(s);
                }
                // Reverse to get correct order
                java.util.Collections.reverse(cycle);
                if (cycles.size() < 20) { // Limit reported cycles
                    cycles.add(cycle);
                }
            }
        }

        stack.pop();
        inStack.remove(node);
    }

    private static class GraphNode {
        final String id;
        final String name;
        final String type;

        GraphNode(String id, String name, String type) {
            this.id = id;
            this.name = name;
            this.type = type;
        }
    }
}

package com.capellaagent.modelchat.tools.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

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
import org.polarsys.capella.core.data.fa.FunctionalExchange;

/**
 * Gathers detailed function and component context to enable AI-driven
 * optimization of function-to-component allocations.
 * <p>
 * Unlike {@link AutoAllocateTool} which just lists unallocated functions,
 * this tool performs deeper analysis including:
 * <ul>
 *   <li>Exchange coupling analysis between functions</li>
 *   <li>Current allocation balance across components</li>
 *   <li>Communication cost estimation for different allocation strategies</li>
 *   <li>Functional cohesion scoring</li>
 * </ul>
 * Returns comprehensive context for the LLM to suggest optimal allocations.
 */
public class OptimizeAllocationTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("sa", "la", "pa");

    public OptimizeAllocationTool() {
        super("optimize_allocation",
                "Analyzes function-component coupling for AI-driven allocation optimization. "
                + "Returns cohesion metrics and exchange patterns.",
                ToolCategory.AI_INTELLIGENCE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: sa, la, pa",
                VALID_LAYERS));
        params.add(ToolParameter.optionalString("optimization_goal",
                "Optimization goal: minimize_coupling, balance_load, "
                + "minimize_communication (default: minimize_coupling)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        String goal = getOptionalString(parameters, "optimization_goal", "minimize_coupling");

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();
            BlockArchitecture arch = modelService.getArchitecture(session, layer);

            // Collect all functions and components
            List<AbstractFunction> allFunctions = new ArrayList<>();
            List<AbstractFunction> unallocatedFunctions = new ArrayList<>();
            List<Component> allComponents = new ArrayList<>();
            Map<String, String> functionToComponent = new HashMap<>(); // funcId -> compId
            Map<String, List<String>> componentToFunctions = new HashMap<>(); // compId -> [funcIds]

            // Build exchange graph: funcId -> set of connected funcIds
            Map<String, Set<String>> exchangeGraph = new HashMap<>();

            Iterator<EObject> it = arch.eAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();

                if (obj instanceof AbstractFunction) {
                    AbstractFunction func = (AbstractFunction) obj;
                    String name = getElementName(func);
                    if (name == null || name.isBlank() || name.contains("Root")) continue;

                    allFunctions.add(func);
                    String funcId = getElementId(func);
                    exchangeGraph.putIfAbsent(funcId, new HashSet<>());

                    // Check allocation
                    boolean isAllocated = false;
                    try {
                        @SuppressWarnings("unchecked")
                        List<?> allocators = (List<?>) func.getClass()
                                .getMethod("getAllocationBlocks").invoke(func);
                        if (!allocators.isEmpty()) {
                            isAllocated = true;
                            EObject allocComp = (EObject) allocators.get(0);
                            String compId = getElementId(allocComp);
                            functionToComponent.put(funcId, compId);
                            componentToFunctions.computeIfAbsent(compId, k -> new ArrayList<>())
                                    .add(funcId);
                        }
                    } catch (Exception e) { /* skip */ }

                    if (!isAllocated) {
                        unallocatedFunctions.add(func);
                    }

                    // Build exchange connections
                    for (var incoming : func.getIncoming()) {
                        if (incoming instanceof FunctionalExchange) {
                            FunctionalExchange fe = (FunctionalExchange) incoming;
                            EObject sourcePort = fe.getSource();
                            if (sourcePort != null && sourcePort.eContainer() instanceof AbstractFunction) {
                                String sourceId = getElementId(sourcePort.eContainer());
                                exchangeGraph.computeIfAbsent(funcId, k -> new HashSet<>()).add(sourceId);
                                exchangeGraph.computeIfAbsent(sourceId, k -> new HashSet<>()).add(funcId);
                            }
                        }
                    }
                }

                if (obj instanceof Component) {
                    Component comp = (Component) obj;
                    if (!comp.isActor()) {
                        String name = getElementName(comp);
                        if (name != null && !name.isBlank()) {
                            allComponents.add(comp);
                        }
                    }
                }
            }

            // Build function details with coupling info
            JsonArray functionDetails = new JsonArray();
            for (AbstractFunction func : allFunctions) {
                String funcId = getElementId(func);
                JsonObject fObj = new JsonObject();
                fObj.addProperty("name", getElementName(func));
                fObj.addProperty("id", funcId);
                fObj.addProperty("input_count", func.getIncoming().size());
                fObj.addProperty("output_count", func.getOutgoing().size());

                String allocCompId = functionToComponent.get(funcId);
                if (allocCompId != null) {
                    fObj.addProperty("allocated_to_id", allocCompId);
                    // Find component name
                    for (Component c : allComponents) {
                        if (getElementId(c).equals(allocCompId)) {
                            fObj.addProperty("allocated_to", getElementName(c));
                            break;
                        }
                    }
                } else {
                    fObj.addProperty("allocated_to", "UNALLOCATED");
                }

                // Connected functions (exchange partners)
                Set<String> connectedIds = exchangeGraph.getOrDefault(funcId, new HashSet<>());
                JsonArray connected = new JsonArray();
                for (String connId : connectedIds) {
                    for (AbstractFunction af : allFunctions) {
                        if (getElementId(af).equals(connId)) {
                            JsonObject conn = new JsonObject();
                            conn.addProperty("name", getElementName(af));
                            conn.addProperty("id", connId);
                            String connAllocId = functionToComponent.get(connId);
                            conn.addProperty("same_component",
                                    allocCompId != null && allocCompId.equals(connAllocId));
                            connected.add(conn);
                            break;
                        }
                    }
                }
                fObj.add("exchange_partners", connected);
                fObj.addProperty("coupling_degree", connectedIds.size());
                functionDetails.add(fObj);
            }

            // Build component details with balance metrics
            JsonArray componentDetails = new JsonArray();
            for (Component comp : allComponents) {
                String compId = getElementId(comp);
                JsonObject cObj = new JsonObject();
                cObj.addProperty("name", getElementName(comp));
                cObj.addProperty("id", compId);

                List<String> allocFuncs = componentToFunctions.getOrDefault(compId, List.of());
                cObj.addProperty("allocated_function_count", allocFuncs.size());

                // Calculate internal cohesion (exchanges between functions in same component)
                int internalExchanges = 0;
                int externalExchanges = 0;
                for (String funcId : allocFuncs) {
                    Set<String> partners = exchangeGraph.getOrDefault(funcId, new HashSet<>());
                    for (String partnerId : partners) {
                        if (allocFuncs.contains(partnerId)) {
                            internalExchanges++;
                        } else {
                            externalExchanges++;
                        }
                    }
                }
                // Divide by 2 since each internal exchange is counted twice
                internalExchanges /= 2;

                cObj.addProperty("internal_exchanges", internalExchanges);
                cObj.addProperty("external_exchanges", externalExchanges);

                double cohesion = (internalExchanges + externalExchanges) > 0
                        ? (internalExchanges * 1.0 / (internalExchanges + externalExchanges))
                        : 0.0;
                cObj.addProperty("cohesion_score", Math.round(cohesion * 100.0) / 100.0);

                componentDetails.add(cObj);
            }

            // Compute cross-component communication cost
            int crossComponentExchanges = 0;
            int totalExchangeLinks = 0;
            for (Map.Entry<String, Set<String>> entry : exchangeGraph.entrySet()) {
                String funcId = entry.getKey();
                String compId = functionToComponent.get(funcId);
                for (String partnerId : entry.getValue()) {
                    totalExchangeLinks++;
                    String partnerCompId = functionToComponent.get(partnerId);
                    if (compId != null && partnerCompId != null && !compId.equals(partnerCompId)) {
                        crossComponentExchanges++;
                    }
                }
            }
            totalExchangeLinks /= 2; // undirected
            crossComponentExchanges /= 2;

            JsonObject metrics = new JsonObject();
            metrics.addProperty("total_functions", allFunctions.size());
            metrics.addProperty("unallocated_functions", unallocatedFunctions.size());
            metrics.addProperty("total_components", allComponents.size());
            metrics.addProperty("total_exchange_links", totalExchangeLinks);
            metrics.addProperty("cross_component_exchanges", crossComponentExchanges);
            metrics.addProperty("intra_component_exchanges",
                    totalExchangeLinks - crossComponentExchanges);

            double communicationCostRatio = totalExchangeLinks > 0
                    ? (crossComponentExchanges * 1.0 / totalExchangeLinks) : 0.0;
            metrics.addProperty("communication_cost_ratio",
                    Math.round(communicationCostRatio * 100.0) / 100.0);

            // Allocation balance (std deviation of function counts per component)
            double avgFuncsPerComp = allComponents.size() > 0
                    ? (allFunctions.size() * 1.0 / allComponents.size()) : 0;
            double variance = 0;
            for (Component comp : allComponents) {
                String compId = getElementId(comp);
                int count = componentToFunctions.getOrDefault(compId, List.of()).size();
                variance += Math.pow(count - avgFuncsPerComp, 2);
            }
            variance = allComponents.size() > 0 ? variance / allComponents.size() : 0;
            metrics.addProperty("allocation_balance_stddev",
                    Math.round(Math.sqrt(variance) * 100.0) / 100.0);
            metrics.addProperty("avg_functions_per_component",
                    Math.round(avgFuncsPerComp * 100.0) / 100.0);

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("optimization_goal", goal);
            response.add("metrics", metrics);
            response.add("functions", functionDetails);
            response.add("components", componentDetails);

            // Optimization prompt
            response.addProperty("optimization_prompt",
                    "Based on the exchange coupling data and current allocations:\n"
                    + "1. Identify suboptimal allocations (high cross-component communication)\n"
                    + "2. Suggest function moves to improve " + goal + "\n"
                    + "3. Propose allocations for unallocated functions\n"
                    + "4. Estimate the improvement in communication cost ratio\n"
                    + "5. Use allocate_function tool to execute recommended allocations");

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Allocation optimization analysis failed: " + e.getMessage());
        }
    }
}

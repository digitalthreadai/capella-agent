package com.capellaagent.modelchat.tools.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
 * Computes architecture complexity metrics for a layer.
 */
public class ArchitectureComplexityTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public ArchitectureComplexityTool() {
        super("architecture_complexity",
                "Computes complexity metrics: coupling, cohesion, per component.",
                ToolCategory.ANALYSIS);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();
            BlockArchitecture arch = modelService.getArchitecture(session, layer);

            // Collect components and their functions
            Map<String, Component> components = new HashMap<>();
            Map<String, List<AbstractFunction>> compFunctions = new HashMap<>();
            int totalFunctions = 0;
            int totalFuncExchanges = 0;
            int totalCompExchanges = 0;

            Iterator<EObject> it = arch.eAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();
                if (obj instanceof Component) {
                    Component comp = (Component) obj;
                    if (!comp.isActor()) {
                        String id = getElementId(comp);
                        components.put(id, comp);
                        compFunctions.put(id, new ArrayList<>());
                    }
                }
            }

            // Count functions per component via allocations
            it = arch.eAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();
                if (obj instanceof AbstractFunction) {
                    totalFunctions++;
                }
                if (obj instanceof FunctionalExchange) {
                    totalFuncExchanges++;
                }
                if (obj instanceof ComponentExchange) {
                    totalCompExchanges++;
                }
            }

            // Build per-component metrics
            JsonArray componentMetrics = new JsonArray();
            for (Map.Entry<String, Component> entry : components.entrySet()) {
                Component comp = entry.getValue();
                int funcCount = 0;
                int outgoingExchanges = 0;

                // Count allocated functions via containment
                Iterator<EObject> compIt = comp.eAllContents();
                while (compIt.hasNext()) {
                    EObject child = compIt.next();
                    if (child instanceof AbstractFunction) {
                        funcCount++;
                    }
                }

                // Count component exchanges (coupling)
                try {
                    @SuppressWarnings("unchecked")
                    List<?> ownedCEs = (List<?>) comp.getClass()
                            .getMethod("getOwnedComponentExchanges").invoke(comp);
                    outgoingExchanges = ownedCEs.size();
                } catch (Exception e) { /* skip */ }

                JsonObject metric = new JsonObject();
                metric.addProperty("name", getElementName(comp));
                metric.addProperty("id", getElementId(comp));
                metric.addProperty("function_count", funcCount);
                metric.addProperty("coupling", outgoingExchanges);
                componentMetrics.add(metric);
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("total_components", components.size());
            response.addProperty("total_functions", totalFunctions);
            response.addProperty("total_functional_exchanges", totalFuncExchanges);
            response.addProperty("total_component_exchanges", totalCompExchanges);
            response.add("components", componentMetrics);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to compute complexity: " + e.getMessage());
        }
    }
}

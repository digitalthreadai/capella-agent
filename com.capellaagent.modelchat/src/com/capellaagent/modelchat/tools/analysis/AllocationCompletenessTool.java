package com.capellaagent.modelchat.tools.analysis;

import java.util.ArrayList;
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
import org.polarsys.capella.core.data.fa.ComponentFunctionalAllocation;

/**
 * Checks allocation completeness for a layer.
 * <p>
 * For all functions: checks each has at least one component allocation.
 * For all components: checks each has at least one allocated function.
 * Reports unallocated functions and empty (no-function) components.
 */
public class AllocationCompletenessTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "allocation_completeness";
    private static final String DESCRIPTION =
            "Checks function-to-component allocation completeness for a layer.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public AllocationCompletenessTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.ANALYSIS);
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
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();
            BlockArchitecture architecture = modelService.getArchitecture(session, layer);

            int functionsTotal = 0;
            int functionsAllocated = 0;
            JsonArray functionsUnallocated = new JsonArray();

            int componentsTotal = 0;
            int componentsWithFunctions = 0;
            JsonArray componentsEmpty = new JsonArray();

            Iterator<EObject> allContents = architecture.eAllContents();
            while (allContents.hasNext()) {
                EObject obj = allContents.next();

                // Check functions
                if (obj instanceof AbstractFunction) {
                    AbstractFunction fn = (AbstractFunction) obj;
                    String name = fn.getName();
                    if (name == null || name.isBlank() || name.contains("Root")) continue;

                    functionsTotal++;

                    boolean allocated = false;
                    try {
                        List<?> allocations = fn.getComponentFunctionalAllocations();
                        allocated = allocations != null && !allocations.isEmpty();
                    } catch (Exception e) { /* skip */ }

                    if (allocated) {
                        functionsAllocated++;
                    } else {
                        JsonObject entry = new JsonObject();
                        entry.addProperty("name", name);
                        entry.addProperty("id", getElementId(fn));
                        entry.addProperty("type", fn.eClass().getName());
                        functionsUnallocated.add(entry);
                    }
                }

                // Check components
                if (obj instanceof Component) {
                    Component comp = (Component) obj;
                    String name = comp.getName();
                    if (name == null || name.isBlank()) continue;
                    if (comp.isActor()) continue; // Skip actors for allocation check

                    componentsTotal++;

                    boolean hasFunctions = false;
                    try {
                        List<ComponentFunctionalAllocation> allocations = comp.getFunctionalAllocations();
                        hasFunctions = allocations != null && !allocations.isEmpty();
                    } catch (Exception e) { /* skip */ }

                    if (hasFunctions) {
                        componentsWithFunctions++;
                    } else {
                        JsonObject entry = new JsonObject();
                        entry.addProperty("name", name);
                        entry.addProperty("id", getElementId(comp));
                        entry.addProperty("type", comp.eClass().getName());
                        componentsEmpty.add(entry);
                    }
                }
            }

            double functionCoverage = functionsTotal > 0
                    ? (functionsAllocated * 100.0 / functionsTotal) : 100.0;
            double componentCoverage = componentsTotal > 0
                    ? (componentsWithFunctions * 100.0 / componentsTotal) : 100.0;

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);

            JsonObject funcStats = new JsonObject();
            funcStats.addProperty("total", functionsTotal);
            funcStats.addProperty("allocated", functionsAllocated);
            funcStats.addProperty("unallocated_count", functionsTotal - functionsAllocated);
            funcStats.addProperty("coverage_percent", Math.round(functionCoverage * 10.0) / 10.0);
            funcStats.add("unallocated", functionsUnallocated);
            response.add("functions", funcStats);

            JsonObject compStats = new JsonObject();
            compStats.addProperty("total", componentsTotal);
            compStats.addProperty("with_functions", componentsWithFunctions);
            compStats.addProperty("empty_count", componentsTotal - componentsWithFunctions);
            compStats.addProperty("coverage_percent", Math.round(componentCoverage * 10.0) / 10.0);
            compStats.add("empty_components", componentsEmpty);
            response.add("components", compStats);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to check allocation completeness: " + e.getMessage());
        }
    }
}

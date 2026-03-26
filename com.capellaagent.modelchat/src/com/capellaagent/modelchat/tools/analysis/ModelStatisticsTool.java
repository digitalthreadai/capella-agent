package com.capellaagent.modelchat.tools.analysis;

import java.util.ArrayList;
import java.util.Collection;
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
import org.eclipse.sirius.business.api.dialect.DialectManager;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.viewpoint.DRepresentationDescriptor;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.cs.Interface;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.ComponentExchange;
import org.polarsys.capella.core.data.fa.ComponentFunctionalAllocation;
import org.polarsys.capella.core.data.fa.FunctionalExchange;
import org.polarsys.capella.core.data.interaction.AbstractCapability;
import org.polarsys.capella.core.data.interaction.Scenario;
import org.polarsys.capella.core.data.capellacommon.StateMachine;

/**
 * Produces comprehensive model statistics across all or specific ARCADIA layers.
 * <p>
 * Counts elements by type per layer: functions, components, actors, exchanges,
 * allocations, interfaces, capabilities, scenarios, diagrams.
 */
public class ModelStatisticsTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "model_statistics";
    private static final String DESCRIPTION =
            "Returns element counts and statistics per ARCADIA layer.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public ModelStatisticsTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.ANALYSIS);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalEnum("layer",
                "Specific layer to report (all layers if omitted)",
                VALID_LAYERS, null));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getOptionalString(parameters, "layer", null);

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();

            List<String> layersToCount;
            if (layer != null && !layer.isBlank()) {
                layersToCount = List.of(layer.toLowerCase());
            } else {
                layersToCount = VALID_LAYERS;
            }

            JsonArray layersArray = new JsonArray();
            int totalFunctions = 0, totalComponents = 0, totalActors = 0;
            int totalFuncExchanges = 0, totalCompExchanges = 0, totalAllocations = 0;
            int totalCapabilities = 0, totalScenarios = 0, totalInterfaces = 0;

            for (String l : layersToCount) {
                try {
                    BlockArchitecture arch = modelService.getArchitecture(session, l);
                    LayerStats stats = countElements(arch);

                    JsonObject layerObj = new JsonObject();
                    layerObj.addProperty("name", l);
                    layerObj.addProperty("functions", stats.functions);
                    layerObj.addProperty("components", stats.components);
                    layerObj.addProperty("actors", stats.actors);
                    layerObj.addProperty("functional_exchanges", stats.functionalExchanges);
                    layerObj.addProperty("component_exchanges", stats.componentExchanges);
                    layerObj.addProperty("allocations", stats.allocations);
                    layerObj.addProperty("capabilities", stats.capabilities);
                    layerObj.addProperty("scenarios", stats.scenarios);
                    layerObj.addProperty("interfaces", stats.interfaces);
                    layerObj.addProperty("total_named_elements", stats.totalNamedElements);
                    layersArray.add(layerObj);

                    totalFunctions += stats.functions;
                    totalComponents += stats.components;
                    totalActors += stats.actors;
                    totalFuncExchanges += stats.functionalExchanges;
                    totalCompExchanges += stats.componentExchanges;
                    totalAllocations += stats.allocations;
                    totalCapabilities += stats.capabilities;
                    totalScenarios += stats.scenarios;
                    totalInterfaces += stats.interfaces;
                } catch (Exception e) {
                    // Layer may not exist; skip
                }
            }

            // Count diagrams
            int diagramCount = 0;
            try {
                Collection<DRepresentationDescriptor> descriptors =
                        DialectManager.INSTANCE.getAllRepresentationDescriptors(session);
                diagramCount = descriptors.size();
            } catch (Exception e) {
                // Diagram counting may fail
            }

            JsonObject totals = new JsonObject();
            totals.addProperty("functions", totalFunctions);
            totals.addProperty("components", totalComponents);
            totals.addProperty("actors", totalActors);
            totals.addProperty("functional_exchanges", totalFuncExchanges);
            totals.addProperty("component_exchanges", totalCompExchanges);
            totals.addProperty("allocations", totalAllocations);
            totals.addProperty("capabilities", totalCapabilities);
            totals.addProperty("scenarios", totalScenarios);
            totals.addProperty("interfaces", totalInterfaces);
            totals.addProperty("diagrams", diagramCount);

            JsonObject response = new JsonObject();
            response.add("layers", layersArray);
            response.add("totals", totals);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to compute model statistics: " + e.getMessage());
        }
    }

    private LayerStats countElements(BlockArchitecture architecture) {
        LayerStats stats = new LayerStats();

        Iterator<EObject> allContents = architecture.eAllContents();
        while (allContents.hasNext()) {
            EObject obj = allContents.next();

            if (obj instanceof AbstractFunction) {
                String name = getElementName(obj);
                if (name != null && !name.isBlank() && !name.contains("Root")) {
                    stats.functions++;
                }
            }
            if (obj instanceof Component) {
                Component comp = (Component) obj;
                if (comp.isActor()) {
                    stats.actors++;
                } else {
                    stats.components++;
                }
            }
            if (obj instanceof FunctionalExchange) {
                stats.functionalExchanges++;
            }
            if (obj instanceof ComponentExchange) {
                stats.componentExchanges++;
            }
            if (obj instanceof ComponentFunctionalAllocation) {
                stats.allocations++;
            }
            if (obj instanceof AbstractCapability) {
                stats.capabilities++;
            }
            if (obj instanceof Scenario) {
                stats.scenarios++;
            }
            if (obj instanceof Interface) {
                stats.interfaces++;
            }
            if (obj instanceof org.polarsys.capella.common.data.modellingcore.AbstractNamedElement) {
                String name = getElementName(obj);
                if (name != null && !name.isBlank()) {
                    stats.totalNamedElements++;
                }
            }
        }

        return stats;
    }

    private static class LayerStats {
        int functions = 0;
        int components = 0;
        int actors = 0;
        int functionalExchanges = 0;
        int componentExchanges = 0;
        int allocations = 0;
        int capabilities = 0;
        int scenarios = 0;
        int interfaces = 0;
        int totalNamedElements = 0;
    }
}

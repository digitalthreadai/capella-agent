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
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.FunctionalExchange;

/**
 * Compares two architecture layers to identify structural differences.
 * <p>
 * Performs an element-level comparison between two layers (or the same layer
 * across time by comparing element counts and properties). Identifies elements
 * that exist in one layer but not the other, and highlights naming/structural
 * differences. Useful for checking transition completeness.
 */
public class CompareModelsTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "compare_models";
    private static final String DESCRIPTION =
            "Compares two architecture layers to identify structural differences and gaps.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public CompareModelsTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.ANALYSIS);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("source_layer",
                "Source architecture layer: oa, sa, la, pa",
                VALID_LAYERS));
        params.add(ToolParameter.requiredEnum("target_layer",
                "Target architecture layer: oa, sa, la, pa",
                VALID_LAYERS));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String sourceLayer = getRequiredString(parameters, "source_layer").toLowerCase();
        String targetLayer = getRequiredString(parameters, "target_layer").toLowerCase();

        if (!VALID_LAYERS.contains(sourceLayer) || !VALID_LAYERS.contains(targetLayer)) {
            return ToolResult.error("Invalid layer. Must be one of: oa, sa, la, pa");
        }

        if (sourceLayer.equals(targetLayer)) {
            return ToolResult.error("Source and target layers must be different");
        }

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();
            BlockArchitecture sourceArch = modelService.getArchitecture(session, sourceLayer);
            BlockArchitecture targetArch = modelService.getArchitecture(session, targetLayer);

            // Collect elements by name from source
            Map<String, List<EObject>> sourceByName = collectElementsByName(sourceArch);
            Map<String, List<EObject>> targetByName = collectElementsByName(targetArch);

            // Statistics
            JsonObject sourceStats = computeStats(sourceArch, sourceLayer);
            JsonObject targetStats = computeStats(targetArch, targetLayer);

            // Find elements in source but not in target (by name matching)
            JsonArray onlyInSource = new JsonArray();
            for (Map.Entry<String, List<EObject>> entry : sourceByName.entrySet()) {
                if (!targetByName.containsKey(entry.getKey())) {
                    for (EObject obj : entry.getValue()) {
                        if (onlyInSource.size() >= 100) break;
                        JsonObject item = new JsonObject();
                        item.addProperty("name", entry.getKey());
                        item.addProperty("id", getElementId(obj));
                        item.addProperty("type", obj.eClass().getName());
                        onlyInSource.add(item);
                    }
                }
            }

            // Find elements in target but not in source
            JsonArray onlyInTarget = new JsonArray();
            for (Map.Entry<String, List<EObject>> entry : targetByName.entrySet()) {
                if (!sourceByName.containsKey(entry.getKey())) {
                    for (EObject obj : entry.getValue()) {
                        if (onlyInTarget.size() >= 100) break;
                        JsonObject item = new JsonObject();
                        item.addProperty("name", entry.getKey());
                        item.addProperty("id", getElementId(obj));
                        item.addProperty("type", obj.eClass().getName());
                        onlyInTarget.add(item);
                    }
                }
            }

            // Find common elements (by name)
            int commonCount = 0;
            for (String name : sourceByName.keySet()) {
                if (targetByName.containsKey(name)) {
                    commonCount++;
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("source_layer", sourceLayer);
            response.addProperty("target_layer", targetLayer);
            response.add("source_statistics", sourceStats);
            response.add("target_statistics", targetStats);
            response.addProperty("common_element_names", commonCount);
            response.addProperty("only_in_source_count", onlyInSource.size());
            response.addProperty("only_in_target_count", onlyInTarget.size());
            response.add("only_in_source", onlyInSource);
            response.add("only_in_target", onlyInTarget);

            // Coverage percentage (how many source names appear in target)
            double coverage = sourceByName.isEmpty() ? 100.0
                    : (commonCount * 100.0) / sourceByName.size();
            response.addProperty("name_match_coverage_percent",
                    Math.round(coverage * 10.0) / 10.0);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to compare models: " + e.getMessage());
        }
    }

    private Map<String, List<EObject>> collectElementsByName(BlockArchitecture arch) {
        Map<String, List<EObject>> byName = new HashMap<>();
        Iterator<EObject> it = arch.eAllContents();
        while (it.hasNext()) {
            EObject obj = it.next();
            if (obj instanceof AbstractFunction || obj instanceof Component) {
                String name = getElementName(obj);
                if (name != null && !name.isBlank() && !name.contains("Root")) {
                    byName.computeIfAbsent(name, k -> new ArrayList<>()).add(obj);
                }
            }
        }
        return byName;
    }

    private JsonObject computeStats(BlockArchitecture arch, String layer) {
        int functions = 0, components = 0, exchanges = 0;
        Iterator<EObject> it = arch.eAllContents();
        while (it.hasNext()) {
            EObject obj = it.next();
            if (obj instanceof AbstractFunction) {
                String name = getElementName(obj);
                if (name != null && !name.isBlank() && !name.contains("Root")) {
                    functions++;
                }
            } else if (obj instanceof Component) {
                components++;
            } else if (obj instanceof FunctionalExchange) {
                exchanges++;
            }
        }
        JsonObject stats = new JsonObject();
        stats.addProperty("layer", layer);
        stats.addProperty("functions", functions);
        stats.addProperty("components", components);
        stats.addProperty("exchanges", exchanges);
        return stats;
    }
}

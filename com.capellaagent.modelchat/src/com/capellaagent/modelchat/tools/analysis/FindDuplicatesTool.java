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
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.cs.Interface;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.information.ExchangeItem;

/**
 * Finds duplicate or near-duplicate elements in the model.
 * <p>
 * Identifies elements with identical or similar names within the same layer.
 * Near-duplicates are detected using case-insensitive comparison and
 * whitespace normalization. Groups duplicate elements together for review.
 */
public class FindDuplicatesTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "find_duplicates";
    private static final String DESCRIPTION =
            "Finds duplicate or near-duplicate elements by name within a layer.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public FindDuplicatesTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.ANALYSIS);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS));
        params.add(ToolParameter.optionalBoolean("near_match",
                "If true, also find near-duplicates (case/whitespace differences). Default: true"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        boolean nearMatch = getOptionalBoolean(parameters, "near_match", true);

        if (!VALID_LAYERS.contains(layer)) {
            return ToolResult.error("Invalid layer '" + layer + "'");
        }

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();
            BlockArchitecture architecture = modelService.getArchitecture(session, layer);

            // Group elements by their normalized name
            Map<String, List<EObject>> groups = new HashMap<>();
            int totalElements = 0;

            Iterator<EObject> allContents = architecture.eAllContents();
            while (allContents.hasNext()) {
                EObject obj = allContents.next();

                if (!(obj instanceof AbstractNamedElement)) continue;
                if (!(obj instanceof AbstractFunction || obj instanceof Component
                        || obj instanceof Interface || obj instanceof ExchangeItem)) {
                    continue;
                }

                String name = getElementName(obj);
                if (name == null || name.isBlank() || name.contains("Root")) continue;

                totalElements++;

                String key = nearMatch ? normalizeName(name) : name;
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(obj);
            }

            // Filter to groups with more than one element (duplicates)
            JsonArray duplicateGroups = new JsonArray();
            int totalDuplicates = 0;

            for (Map.Entry<String, List<EObject>> entry : groups.entrySet()) {
                List<EObject> group = entry.getValue();
                if (group.size() <= 1) continue;

                // Check if they are truly duplicates (same type)
                // Group by type within the name group
                Map<String, List<EObject>> byType = new HashMap<>();
                for (EObject obj : group) {
                    String type = obj.eClass().getName();
                    byType.computeIfAbsent(type, k -> new ArrayList<>()).add(obj);
                }

                for (Map.Entry<String, List<EObject>> typeEntry : byType.entrySet()) {
                    List<EObject> sameTypeGroup = typeEntry.getValue();
                    if (sameTypeGroup.size() <= 1) continue;

                    if (duplicateGroups.size() >= 50) break; // Limit output size

                    JsonObject groupObj = new JsonObject();
                    groupObj.addProperty("normalized_name", entry.getKey());
                    groupObj.addProperty("element_type", typeEntry.getKey());
                    groupObj.addProperty("count", sameTypeGroup.size());

                    JsonArray elements = new JsonArray();
                    for (EObject obj : sameTypeGroup) {
                        JsonObject elemObj = new JsonObject();
                        elemObj.addProperty("name", getElementName(obj));
                        elemObj.addProperty("id", getElementId(obj));
                        elemObj.addProperty("type", obj.eClass().getName());
                        elemObj.addProperty("parent", obj.eContainer() != null
                                ? getElementName(obj.eContainer()) : "");
                        elements.add(elemObj);
                        totalDuplicates++;
                    }
                    groupObj.add("elements", elements);
                    duplicateGroups.add(groupObj);
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("total_elements_checked", totalElements);
            response.addProperty("duplicate_groups", duplicateGroups.size());
            response.addProperty("total_duplicate_elements", totalDuplicates);
            response.addProperty("near_match_enabled", nearMatch);
            response.add("groups", duplicateGroups);

            if (duplicateGroups.size() == 0) {
                response.addProperty("message", "No duplicate elements found");
            }

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to find duplicates: " + e.getMessage());
        }
    }

    /**
     * Normalizes a name for near-duplicate comparison.
     * Lowercases, trims, collapses whitespace, removes common suffixes.
     */
    private String normalizeName(String name) {
        return name.toLowerCase()
                .trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[_-]", " ");
    }
}

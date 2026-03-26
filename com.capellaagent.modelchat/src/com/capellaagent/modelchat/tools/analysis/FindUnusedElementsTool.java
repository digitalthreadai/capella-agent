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
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.cs.Interface;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.information.ExchangeItem;
import org.polarsys.capella.core.data.interaction.AbstractCapability;

/**
 * Finds unused (unreferenced) elements in the model.
 * <p>
 * For each element in the layer, checks if it has any non-containment incoming
 * references. Elements with zero references are considered "unused" and may be
 * candidates for cleanup.
 */
public class FindUnusedElementsTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "find_unused_elements";
    private static final String DESCRIPTION =
            "Finds model elements with no incoming references (potentially unused).";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");
    private static final List<String> VALID_TYPES = List.of(
            "function", "component", "interface", "exchange_item", "capability", "all");
    private static final int MAX_RESULTS = 300;

    public FindUnusedElementsTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.ANALYSIS);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS));
        params.add(ToolParameter.optionalEnum("element_type",
                "Type filter: function, component, interface, exchange_item, capability, all",
                VALID_TYPES, "all"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        String elementType = getOptionalString(parameters, "element_type", "all").toLowerCase();

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();
            BlockArchitecture architecture = modelService.getArchitecture(session, layer);

            int totalChecked = 0;
            JsonArray unused = new JsonArray();

            Iterator<EObject> allContents = architecture.eAllContents();
            while (allContents.hasNext() && unused.size() < MAX_RESULTS) {
                EObject obj = allContents.next();

                // Filter by type
                if (!matchesType(obj, elementType)) continue;

                // Skip root/container elements and unnamed elements
                String name = getElementName(obj);
                if (name == null || name.isBlank() || name.contains("Root")) continue;

                totalChecked++;

                // Check for non-containment references
                boolean hasReferences = false;
                try {
                    Collection<EStructuralFeature.Setting> usages =
                            EcoreUtil.UsageCrossReferencer.find(obj, obj.eResource().getResourceSet());

                    for (EStructuralFeature.Setting setting : usages) {
                        // Skip containment references (parent-child)
                        if (!setting.getEStructuralFeature().isDerived()) {
                            hasReferences = true;
                            break;
                        }
                    }
                } catch (Exception e) {
                    // If cross-reference check fails, assume it has references
                    hasReferences = true;
                }

                if (!hasReferences) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("name", name);
                    entry.addProperty("id", getElementId(obj));
                    entry.addProperty("type", obj.eClass().getName());
                    unused.add(entry);
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("element_type_filter", elementType);
            response.addProperty("total_checked", totalChecked);
            response.addProperty("unused_count", unused.size());
            response.addProperty("truncated", unused.size() >= MAX_RESULTS);
            response.add("unused", unused);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to find unused elements: " + e.getMessage());
        }
    }

    private boolean matchesType(EObject obj, String elementType) {
        switch (elementType) {
            case "function": return obj instanceof AbstractFunction;
            case "component": return obj instanceof Component;
            case "interface": return obj instanceof Interface;
            case "exchange_item": return obj instanceof ExchangeItem;
            case "capability": return obj instanceof AbstractCapability;
            case "all": return obj instanceof AbstractNamedElement
                    && (obj instanceof AbstractFunction || obj instanceof Component
                    || obj instanceof Interface || obj instanceof ExchangeItem
                    || obj instanceof AbstractCapability);
            default: return false;
        }
    }
}

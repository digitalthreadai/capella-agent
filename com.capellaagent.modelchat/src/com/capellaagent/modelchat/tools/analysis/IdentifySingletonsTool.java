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

/**
 * Identifies singleton components (1 function only, possibly over-decomposed).
 */
public class IdentifySingletonsTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public IdentifySingletonsTool() {
        super("identify_singletons",
                "Finds components with only 1 function (over-decomposed).",
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

            JsonArray results = new JsonArray();
            int totalComponents = 0;

            Iterator<EObject> it = arch.eAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();
                if (obj instanceof Component) {
                    Component comp = (Component) obj;
                    if (comp.isActor()) continue;

                    totalComponents++;
                    int funcCount = 0;
                    Iterator<EObject> compIt = comp.eAllContents();
                    while (compIt.hasNext()) {
                        EObject child = compIt.next();
                        if (child instanceof AbstractFunction) {
                            funcCount++;
                        }
                    }

                    // Also check allocated functions
                    try {
                        @SuppressWarnings("unchecked")
                        List<?> allocations = (List<?>) comp.getClass()
                                .getMethod("getAllocatedFunctions").invoke(comp);
                        if (funcCount == 0) {
                            funcCount = allocations.size();
                        }
                    } catch (Exception e) { /* skip */ }

                    if (funcCount <= 1) {
                        JsonObject item = new JsonObject();
                        item.addProperty("component_name", getElementName(comp));
                        item.addProperty("component_id", getElementId(comp));
                        item.addProperty("function_count", funcCount);
                        item.addProperty("suggestion",
                                funcCount == 0 ? "Empty component: consider removing"
                                        : "Singleton: consider merging with parent");
                        results.add(item);
                    }
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("total_components", totalComponents);
            response.addProperty("singleton_count", results.size());
            response.add("singletons", results);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to identify singletons: " + e.getMessage());
        }
    }
}

package com.capellaagent.modelchat.tools.ai;

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
 * Gathers unallocated functions and available components for AI-driven
 * allocation suggestions. Returns structured context for the LLM to
 * propose function-to-component allocation.
 */
public class AutoAllocateTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("sa", "la", "pa");

    public AutoAllocateTool() {
        super("auto_allocate",
                "Gathers context for AI-driven function allocation suggestions.",
                ToolCategory.AI_INTELLIGENCE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: sa, la, pa",
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

            JsonArray unallocatedFunctions = new JsonArray();
            JsonArray allocatedFunctions = new JsonArray();
            JsonArray components = new JsonArray();

            Iterator<EObject> it = arch.eAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();

                if (obj instanceof Component) {
                    Component comp = (Component) obj;
                    if (!comp.isActor()) {
                        JsonObject c = new JsonObject();
                        c.addProperty("name", getElementName(comp));
                        c.addProperty("id", getElementId(comp));

                        int allocCount = 0;
                        try {
                            @SuppressWarnings("unchecked")
                            List<?> allocs = (List<?>) comp.getClass()
                                    .getMethod("getAllocatedFunctions").invoke(comp);
                            allocCount = allocs.size();
                            JsonArray funcNames = new JsonArray();
                            for (Object f : allocs) {
                                funcNames.add(getElementName((EObject) f));
                            }
                            c.add("allocated_functions", funcNames);
                        } catch (Exception e) {
                            c.add("allocated_functions", new JsonArray());
                        }
                        c.addProperty("allocated_count", allocCount);
                        components.add(c);
                    }
                }

                if (obj instanceof AbstractFunction) {
                    AbstractFunction func = (AbstractFunction) obj;
                    String name = getElementName(func);
                    if (name == null || name.isBlank() || name.contains("Root")) continue;

                    // Check if allocated
                    boolean isAllocated = false;
                    try {
                        @SuppressWarnings("unchecked")
                        List<?> allocators = (List<?>) func.getClass()
                                .getMethod("getAllocationBlocks").invoke(func);
                        isAllocated = !allocators.isEmpty();
                    } catch (Exception e) { /* skip */ }

                    JsonObject f = new JsonObject();
                    f.addProperty("name", name);
                    f.addProperty("id", getElementId(func));
                    f.addProperty("in_exchanges", func.getIncoming().size());
                    f.addProperty("out_exchanges", func.getOutgoing().size());

                    if (isAllocated) {
                        allocatedFunctions.add(f);
                    } else {
                        unallocatedFunctions.add(f);
                    }
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("unallocated_count", unallocatedFunctions.size());
            response.addProperty("allocated_count", allocatedFunctions.size());
            response.addProperty("component_count", components.size());
            response.add("unallocated_functions", unallocatedFunctions);
            response.add("components", components);
            response.addProperty("allocation_prompt",
                    "Suggest which component each unallocated function should be allocated to, "
                    + "based on functional cohesion and exchange patterns. "
                    + "Use allocate_function tool to execute each allocation.");
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to gather allocation context: " + e.getMessage());
        }
    }
}

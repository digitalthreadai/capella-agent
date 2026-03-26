package com.capellaagent.modelchat.tools.read;

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
 * Retrieves the function-to-component allocation matrix for a given layer.
 * <p>
 * Iterates all components in the specified architecture and collects their
 * functional allocations, building a matrix showing which functions are
 * allocated to which components.
 */
public class GetAllocationMatrixTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "get_allocation_matrix";
    private static final String DESCRIPTION =
            "Returns the function-to-component allocation matrix for a layer.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public GetAllocationMatrixTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_READ);
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

        if (!VALID_LAYERS.contains(layer)) {
            return ToolResult.error("Invalid layer '" + layer
                    + "'. Must be one of: " + String.join(", ", VALID_LAYERS));
        }

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();
            BlockArchitecture architecture = modelService.getArchitecture(session, layer);

            JsonArray rows = new JsonArray();
            int totalAllocations = 0;

            Iterator<EObject> allContents = architecture.eAllContents();
            while (allContents.hasNext()) {
                EObject obj = allContents.next();
                if (!(obj instanceof Component)) {
                    continue;
                }

                Component comp = (Component) obj;
                List<ComponentFunctionalAllocation> allocations;
                try {
                    allocations = comp.getFunctionalAllocations();
                } catch (Exception e) {
                    continue;
                }

                if (allocations == null || allocations.isEmpty()) {
                    // Include components with no allocations for completeness
                    JsonObject row = new JsonObject();
                    row.addProperty("component_name", getElementName(comp));
                    row.addProperty("component_id", getElementId(comp));
                    row.addProperty("component_type", comp.eClass().getName());
                    row.addProperty("is_actor", comp.isActor());
                    row.add("allocated_functions", new JsonArray());
                    rows.add(row);
                    continue;
                }

                JsonArray allocatedFunctions = new JsonArray();
                for (ComponentFunctionalAllocation alloc : allocations) {
                    AbstractFunction fn = alloc.getFunction();
                    if (fn != null) {
                        JsonObject fnObj = new JsonObject();
                        fnObj.addProperty("name", getElementName(fn));
                        fnObj.addProperty("id", getElementId(fn));
                        fnObj.addProperty("type", fn.eClass().getName());
                        allocatedFunctions.add(fnObj);
                        totalAllocations++;
                    }
                }

                JsonObject row = new JsonObject();
                row.addProperty("component_name", getElementName(comp));
                row.addProperty("component_id", getElementId(comp));
                row.addProperty("component_type", comp.eClass().getName());
                row.addProperty("is_actor", comp.isActor());
                row.add("allocated_functions", allocatedFunctions);
                rows.add(row);
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("component_count", rows.size());
            response.addProperty("total_allocations", totalAllocations);
            response.add("rows", rows);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to get allocation matrix: " + e.getMessage());
        }
    }
}

package com.capellaagent.modelchat.tools.read;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;

// PLACEHOLDER imports for Capella traceability API
// import org.polarsys.capella.core.data.capellacore.Trace;
// import org.polarsys.capella.core.data.capellacore.TraceableElement;
// import org.polarsys.capella.core.data.cs.Component;
// import org.polarsys.capella.core.data.fa.AbstractFunction;
// import org.polarsys.capella.core.data.interaction.AbstractCapability;
// import org.polarsys.capella.core.data.capellacommon.TransfoLink;
// import org.polarsys.capella.core.model.helpers.RefinementLinkExt;

/**
 * Retrieves traceability links across ARCADIA architecture layers.
 * <p>
 * Given a model element, finds all realizing (downstream) and realized (upstream)
 * elements across the OA to SA to LA to PA refinement chain. This is essential for
 * understanding how operational concepts trace through system design to implementation.
 *
 * <h3>Tool Specification</h3>
 * <ul>
 *   <li><b>Name:</b> get_traceability</li>
 *   <li><b>Category:</b> model_read</li>
 *   <li><b>Parameters:</b>
 *     <ul>
 *       <li>{@code uuid} (string, required) - UUID of the element to trace</li>
 *       <li>{@code direction} (string, optional, default "both") - Direction of trace:
 *           realizing (downstream), realized (upstream), or both</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class GetTraceabilityTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "get_traceability";
    private static final String DESCRIPTION =
            "Retrieves traceability links across ARCADIA layers (OA->SA->LA->PA). "
            + "Shows which elements realize or are realized by the given element.";

    private static final List<String> VALID_DIRECTIONS = List.of("realizing", "realized", "both");

    public GetTraceabilityTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("uuid",
                "UUID of the element to trace across ARCADIA layers"));
        params.add(ToolParameter.optionalString("direction",
                "Trace direction: realizing (downstream refinements), "
                + "realized (upstream sources), or both (default: both)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String uuid = getRequiredString(parameters, "uuid");
        String direction = getOptionalString(parameters, "direction", "both").toLowerCase();

        if (uuid.isBlank()) {
            return ToolResult.error("Parameter 'uuid' must not be empty");
        }

        if (!VALID_DIRECTIONS.contains(direction)) {
            return ToolResult.error("Invalid direction '" + direction
                    + "'. Must be one of: " + String.join(", ", VALID_DIRECTIONS));
        }

        try {
            EObject element = resolveElementByUuid(uuid);
            if (element == null) {
                return ToolResult.error("Element not found with UUID: " + uuid);
            }

            JsonObject response = new JsonObject();
            response.addProperty("uuid", uuid);
            response.addProperty("name", getElementName(element));
            response.addProperty("type", element.eClass().getName());
            response.addProperty("layer", detectLayer(element));

            // PLACEHOLDER: Capella traceability API
            //
            // Traceability navigation approach:
            //
            // For functions (AbstractFunction):
            //   Realizing: fn.getRealizingAbstractFunctions()
            //     - SA SystemFunction -> LA LogicalFunction (via FunctionRealization)
            //     - LA LogicalFunction -> PA PhysicalFunction
            //   Realized: fn.getRealizedAbstractFunctions()
            //     - SA SystemFunction <- OA OperationalActivity
            //
            // For components (Component):
            //   Realizing: comp.getRealizingComponents()
            //   Realized: comp.getRealizedComponents()
            //
            // For capabilities (AbstractCapability):
            //   Realizing: cap.getRealizingCapabilities() (via CapabilityRealization)
            //   Realized: cap.getRealizedCapabilities()
            //
            // Generic approach via Trace links:
            //   TraceableElement te = (TraceableElement) element;
            //   for (Trace trace : te.getOutgoingTraces()) {
            //       // trace.getTargetElement() = the realized element
            //   }
            //   for (Trace trace : te.getIncomingTraces()) {
            //       // trace.getSourceElement() = the realizing element
            //   }

            if ("realizing".equals(direction) || "both".equals(direction)) {
                JsonArray realizing = buildRealizingLinks(element);
                response.add("realizing_elements", realizing);
            }

            if ("realized".equals(direction) || "both".equals(direction)) {
                JsonArray realized = buildRealizedLinks(element);
                response.add("realized_elements", realized);
            }

            // Build a summary chain showing the full trace path
            if ("both".equals(direction)) {
                JsonArray traceChain = buildTraceChain(element);
                response.add("trace_chain", traceChain);
            }

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to get traceability: " + e.getMessage());
        }
    }

    /**
     * Finds elements that realize (refine/implement) the given element.
     * These are downstream in the ARCADIA chain (e.g., LA component realizing an SA component).
     *
     * @param element the source element
     * @return a JsonArray of realizing element summaries
     */
    private JsonArray buildRealizingLinks(EObject element) {
        JsonArray realizing = new JsonArray();
        // PLACEHOLDER: Use Capella traceability API
        // TraceableElement te = (TraceableElement) element;
        // for (Trace trace : te.getIncomingTraces()) {
        //     if (trace instanceof TransfoLink || isRealizationLink(trace)) {
        //         EObject source = trace.getSourceElement();
        //         if (source != null) {
        //             JsonObject linkObj = new JsonObject();
        //             linkObj.addProperty("name", getElementName(source));
        //             linkObj.addProperty("uuid", getElementId(source));
        //             linkObj.addProperty("type", source.eClass().getName());
        //             linkObj.addProperty("layer", detectLayer(source));
        //             linkObj.addProperty("link_type", trace.eClass().getName());
        //             realizing.add(linkObj);
        //         }
        //     }
        // }
        return realizing;
    }

    /**
     * Finds elements that are realized (abstracted from) by the given element.
     * These are upstream in the ARCADIA chain (e.g., OA activity realized by an SA function).
     *
     * @param element the source element
     * @return a JsonArray of realized element summaries
     */
    private JsonArray buildRealizedLinks(EObject element) {
        JsonArray realized = new JsonArray();
        // PLACEHOLDER: Use Capella traceability API
        // TraceableElement te = (TraceableElement) element;
        // for (Trace trace : te.getOutgoingTraces()) {
        //     if (trace instanceof TransfoLink || isRealizationLink(trace)) {
        //         EObject target = trace.getTargetElement();
        //         if (target != null) {
        //             JsonObject linkObj = new JsonObject();
        //             linkObj.addProperty("name", getElementName(target));
        //             linkObj.addProperty("uuid", getElementId(target));
        //             linkObj.addProperty("type", target.eClass().getName());
        //             linkObj.addProperty("layer", detectLayer(target));
        //             linkObj.addProperty("link_type", trace.eClass().getName());
        //             realized.add(linkObj);
        //         }
        //     }
        // }
        return realized;
    }

    /**
     * Builds a full trace chain showing the element's position across all ARCADIA layers.
     *
     * @param element the starting element
     * @return a JsonArray representing the ordered chain from OA through PA
     */
    private JsonArray buildTraceChain(EObject element) {
        JsonArray chain = new JsonArray();
        // PLACEHOLDER: Walk both directions to build complete chain
        // The chain should show: OA element -> SA element -> LA element -> PA element
        // with the current element highlighted
        return chain;
    }

    /**
     * Detects which ARCADIA layer the given element belongs to.
     *
     * @param element the model element
     * @return the layer identifier (oa, sa, la, pa) or "unknown"
     */
    private String detectLayer(EObject element) {
        // PLACEHOLDER: Walk containment to find BlockArchitecture ancestor
        return "unknown";
    }
}

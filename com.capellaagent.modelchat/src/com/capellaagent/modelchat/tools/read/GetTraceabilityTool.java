package com.capellaagent.modelchat.tools.read;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.capellaagent.core.capella.CapellaModelService;
import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.polarsys.capella.common.data.modellingcore.ModelElement;
import org.polarsys.capella.common.data.modellingcore.TraceableElement;
import org.polarsys.capella.common.data.modellingcore.AbstractTrace;
import org.polarsys.capella.core.data.ctx.SystemAnalysis;
import org.polarsys.capella.core.data.la.LogicalArchitecture;
import org.polarsys.capella.core.data.oa.OperationalAnalysis;
import org.polarsys.capella.core.data.pa.PhysicalArchitecture;

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

            CapellaModelService modelService = getModelService();

            JsonObject response = new JsonObject();
            response.addProperty("uuid", uuid);
            response.addProperty("name", getElementName(element));
            response.addProperty("type", element.eClass().getName());
            response.addProperty("layer", modelService.detectLayer(element));

            if ("realizing".equals(direction) || "both".equals(direction)) {
                JsonArray realizing = buildRealizingLinks(element, modelService);
                response.add("realizing_elements", realizing);
            }

            if ("realized".equals(direction) || "both".equals(direction)) {
                JsonArray realized = buildRealizedLinks(element, modelService);
                response.add("realized_elements", realized);
            }

            // Build a summary chain showing the full trace path
            if ("both".equals(direction)) {
                JsonArray traceChain = buildTraceChain(element, modelService);
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
     * <p>
     * Uses incoming traces: the realizing element is the source of the trace
     * that targets this element.
     *
     * @param element      the source element
     * @param modelService the model service for layer detection
     * @return a JsonArray of realizing element summaries
     */
    private JsonArray buildRealizingLinks(EObject element, CapellaModelService modelService) {
        JsonArray realizing = new JsonArray();

        if (!(element instanceof TraceableElement)) {
            return realizing;
        }

        TraceableElement te = (TraceableElement) element;
        try {
            for (AbstractTrace trace : te.getIncomingTraces()) {
                TraceableElement source = trace.getSourceElement();
                if (source != null && source != element) {
                    JsonObject linkObj = new JsonObject();
                    linkObj.addProperty("name", getElementName(source));
                    linkObj.addProperty("uuid", getElementId(source));
                    linkObj.addProperty("type", source.eClass().getName());
                    linkObj.addProperty("layer", modelService.detectLayer(source));
                    linkObj.addProperty("link_type", trace.eClass().getName());
                    realizing.add(linkObj);
                }
            }
        } catch (Exception e) {
            // Trace API may not be available; return what we have
        }

        return realizing;
    }

    /**
     * Finds elements that are realized (abstracted from) by the given element.
     * These are upstream in the ARCADIA chain (e.g., OA activity realized by an SA function).
     * <p>
     * Uses outgoing traces: the realized element is the target of the trace
     * that originates from this element.
     *
     * @param element      the source element
     * @param modelService the model service for layer detection
     * @return a JsonArray of realized element summaries
     */
    private JsonArray buildRealizedLinks(EObject element, CapellaModelService modelService) {
        JsonArray realized = new JsonArray();

        if (!(element instanceof TraceableElement)) {
            return realized;
        }

        TraceableElement te = (TraceableElement) element;
        try {
            for (AbstractTrace trace : te.getOutgoingTraces()) {
                TraceableElement target = trace.getTargetElement();
                if (target != null && target != element) {
                    JsonObject linkObj = new JsonObject();
                    linkObj.addProperty("name", getElementName(target));
                    linkObj.addProperty("uuid", getElementId(target));
                    linkObj.addProperty("type", target.eClass().getName());
                    linkObj.addProperty("layer", modelService.detectLayer(target));
                    linkObj.addProperty("link_type", trace.eClass().getName());
                    realized.add(linkObj);
                }
            }
        } catch (Exception e) {
            // Trace API may not be available; return what we have
        }

        return realized;
    }

    /**
     * Builds a full trace chain showing the element's position across all ARCADIA layers.
     * Walks both upstream (realized) and downstream (realizing) to build the complete chain.
     *
     * @param element      the starting element
     * @param modelService the model service for layer detection
     * @return a JsonArray representing the ordered chain from OA through PA
     */
    private JsonArray buildTraceChain(EObject element, CapellaModelService modelService) {
        JsonArray chain = new JsonArray();
        Set<String> visited = new HashSet<>();

        // Walk upstream (realized direction) to find the root
        List<EObject> upstream = new ArrayList<>();
        collectTraceDirection(element, true, upstream, visited, modelService);

        // Add upstream elements in reverse order (OA first)
        for (int i = upstream.size() - 1; i >= 0; i--) {
            chain.add(buildChainEntry(upstream.get(i), false, modelService));
        }

        // Add the current element
        chain.add(buildChainEntry(element, true, modelService));

        // Walk downstream (realizing direction)
        List<EObject> downstream = new ArrayList<>();
        visited.clear();
        visited.add(getElementId(element));
        collectTraceDirection(element, false, downstream, visited, modelService);

        // Add downstream elements
        for (EObject obj : downstream) {
            chain.add(buildChainEntry(obj, false, modelService));
        }

        return chain;
    }

    /**
     * Recursively collects elements in one trace direction.
     *
     * @param element      current element
     * @param upstream     true to follow realized (upstream), false to follow realizing (downstream)
     * @param collected    accumulator list
     * @param visited      set of visited IDs to prevent cycles
     * @param modelService the model service
     */
    private void collectTraceDirection(EObject element, boolean upstream,
                                        List<EObject> collected, Set<String> visited,
                                        CapellaModelService modelService) {
        if (!(element instanceof TraceableElement)) return;

        String elementId = getElementId(element);
        visited.add(elementId);

        TraceableElement te = (TraceableElement) element;
        try {
            List<? extends AbstractTrace> traces = upstream ? te.getOutgoingTraces() : te.getIncomingTraces();
            for (AbstractTrace trace : traces) {
                TraceableElement linked = upstream ? trace.getTargetElement() : trace.getSourceElement();
                if (linked != null) {
                    String linkedId = getElementId(linked);
                    if (!visited.contains(linkedId)) {
                        collected.add(linked);
                        collectTraceDirection(linked, upstream, collected, visited, modelService);
                    }
                }
            }
        } catch (Exception e) {
            // Trace navigation may fail for some elements
        }
    }

    /**
     * Builds a chain entry JSON object.
     */
    private JsonObject buildChainEntry(EObject element, boolean isCurrent,
                                        CapellaModelService modelService) {
        JsonObject entry = new JsonObject();
        entry.addProperty("name", getElementName(element));
        entry.addProperty("uuid", getElementId(element));
        entry.addProperty("type", element.eClass().getName());
        entry.addProperty("layer", modelService.detectLayer(element));
        entry.addProperty("is_current", isCurrent);
        return entry;
    }
}

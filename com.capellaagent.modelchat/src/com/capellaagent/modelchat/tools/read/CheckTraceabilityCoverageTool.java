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
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;
import org.polarsys.capella.common.data.modellingcore.AbstractTrace;
import org.polarsys.capella.common.data.modellingcore.TraceableElement;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.fa.AbstractFunction;

/**
 * Checks traceability coverage between two ARCADIA layers.
 * <p>
 * For each function and component in the source layer, checks whether it has
 * realization traces to the target layer. Reports untraced elements.
 */
public class CheckTraceabilityCoverageTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "check_traceability_coverage";
    private static final String DESCRIPTION =
            "Checks traceability coverage between two ARCADIA layers.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public CheckTraceabilityCoverageTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.ANALYSIS);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("source_layer",
                "Source architecture layer (higher level, e.g., oa or sa)",
                VALID_LAYERS));
        params.add(ToolParameter.requiredEnum("target_layer",
                "Target architecture layer (lower level, e.g., sa or la)",
                VALID_LAYERS));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String sourceLayer = getRequiredString(parameters, "source_layer").toLowerCase();
        String targetLayer = getRequiredString(parameters, "target_layer").toLowerCase();

        if (!VALID_LAYERS.contains(sourceLayer)) {
            return ToolResult.error("Invalid source_layer: " + sourceLayer);
        }
        if (!VALID_LAYERS.contains(targetLayer)) {
            return ToolResult.error("Invalid target_layer: " + targetLayer);
        }
        if (sourceLayer.equals(targetLayer)) {
            return ToolResult.error("source_layer and target_layer must be different");
        }

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();
            BlockArchitecture sourceArch = modelService.getArchitecture(session, sourceLayer);

            int totalElements = 0;
            int tracedCount = 0;
            JsonArray untraced = new JsonArray();

            // Collect functions and components from the source layer
            Iterator<EObject> allContents = sourceArch.eAllContents();
            while (allContents.hasNext()) {
                EObject obj = allContents.next();

                boolean isRelevant = (obj instanceof AbstractFunction) || (obj instanceof Component);
                if (!isRelevant) continue;

                // Skip root/container functions
                if (obj instanceof AbstractFunction) {
                    String name = getElementName(obj);
                    if (name == null || name.isBlank() || name.contains("Root")) continue;
                }
                // Skip unnamed components
                if (obj instanceof Component) {
                    String name = getElementName(obj);
                    if (name == null || name.isBlank()) continue;
                }

                totalElements++;

                // Check if this element has any realization traces
                boolean hasTrace = false;
                if (obj instanceof TraceableElement) {
                    TraceableElement te = (TraceableElement) obj;
                    try {
                        // Check incoming traces (from the target layer realizing this element)
                        List<? extends AbstractTrace> incoming = te.getIncomingTraces();
                        if (incoming != null) {
                            for (AbstractTrace trace : incoming) {
                                TraceableElement source = trace.getSourceElement();
                                if (source != null) {
                                    String traceLayer = modelService.detectLayer(source);
                                    if (targetLayer.equals(traceLayer)) {
                                        hasTrace = true;
                                        break;
                                    }
                                }
                            }
                        }
                        // Also check outgoing traces (this element realizing something in target)
                        if (!hasTrace) {
                            List<? extends AbstractTrace> outgoing = te.getOutgoingTraces();
                            if (outgoing != null) {
                                for (AbstractTrace trace : outgoing) {
                                    TraceableElement target = trace.getTargetElement();
                                    if (target != null) {
                                        String traceLayer = modelService.detectLayer(target);
                                        if (targetLayer.equals(traceLayer)) {
                                            hasTrace = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Trace API may vary; treat as untraced
                    }
                }

                if (hasTrace) {
                    tracedCount++;
                } else {
                    JsonObject untracedObj = new JsonObject();
                    untracedObj.addProperty("name", getElementName(obj));
                    untracedObj.addProperty("id", getElementId(obj));
                    untracedObj.addProperty("type", obj.eClass().getName());
                    untraced.add(untracedObj);
                }
            }

            int untracedCount = totalElements - tracedCount;
            double coveragePercent = totalElements > 0
                    ? (tracedCount * 100.0 / totalElements) : 100.0;

            JsonObject response = new JsonObject();
            response.addProperty("source_layer", sourceLayer);
            response.addProperty("target_layer", targetLayer);
            response.addProperty("total_elements", totalElements);
            response.addProperty("traced_count", tracedCount);
            response.addProperty("untraced_count", untracedCount);
            response.addProperty("coverage_percent", Math.round(coveragePercent * 10.0) / 10.0);
            response.add("untraced", untraced);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to check traceability coverage: " + e.getMessage());
        }
    }
}

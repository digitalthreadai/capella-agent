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
 * Checks refinement/realization status between two architecture layers.
 */
public class GetRefinementStatusTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public GetRefinementStatusTool() {
        super("get_refinement_status",
                "Checks realization coverage between two ARCADIA layers.",
                ToolCategory.ANALYSIS);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("source_layer",
                "Source (lower) layer: oa, sa, la, pa",
                VALID_LAYERS));
        params.add(ToolParameter.requiredEnum("target_layer",
                "Target (higher) layer: oa, sa, la, pa",
                VALID_LAYERS));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String sourceLayer = getRequiredString(parameters, "source_layer").toLowerCase();
        String targetLayer = getRequiredString(parameters, "target_layer").toLowerCase();

        if (sourceLayer.equals(targetLayer)) {
            return ToolResult.error("Source and target layers must be different.");
        }

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();
            BlockArchitecture sourceArch = modelService.getArchitecture(session, sourceLayer);

            // Collect functions and components from the source layer
            List<TraceableElement> sourceElements = new ArrayList<>();
            Iterator<EObject> it = sourceArch.eAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();
                if ((obj instanceof AbstractFunction || obj instanceof Component)
                        && obj instanceof TraceableElement) {
                    String name = getElementName(obj);
                    if (name != null && !name.isBlank() && !name.contains("Root")) {
                        sourceElements.add((TraceableElement) obj);
                    }
                }
            }

            int refined = 0;
            int unrefined = 0;
            JsonArray elements = new JsonArray();

            for (TraceableElement elem : sourceElements) {
                boolean hasRealization = false;
                // Check outgoing traces for realizations
                for (AbstractTrace trace : elem.getOutgoingTraces()) {
                    EObject target = trace.getTargetElement();
                    if (target != null) {
                        String targetLayerDetected = modelService.detectLayer(target);
                        if (targetLayer.equals(targetLayerDetected)) {
                            hasRealization = true;
                            break;
                        }
                    }
                }
                // Also check incoming traces
                if (!hasRealization) {
                    for (AbstractTrace trace : elem.getIncomingTraces()) {
                        EObject source = trace.getSourceElement();
                        if (source != null) {
                            String srcLayerDetected = modelService.detectLayer(source);
                            if (targetLayer.equals(srcLayerDetected)) {
                                hasRealization = true;
                                break;
                            }
                        }
                    }
                }

                if (hasRealization) {
                    refined++;
                } else {
                    unrefined++;
                }

                JsonObject elemObj = new JsonObject();
                elemObj.addProperty("name", getElementName((EObject) elem));
                elemObj.addProperty("id", getElementId((EObject) elem));
                elemObj.addProperty("type", ((EObject) elem).eClass().getName());
                elemObj.addProperty("status", hasRealization ? "refined" : "unrefined");
                elements.add(elemObj);
            }

            JsonObject response = new JsonObject();
            response.addProperty("source_layer", sourceLayer);
            response.addProperty("target_layer", targetLayer);
            response.addProperty("total", sourceElements.size());
            response.addProperty("refined", refined);
            response.addProperty("unrefined", unrefined);
            response.addProperty("coverage_pct",
                    sourceElements.isEmpty() ? 100.0
                            : Math.round(refined * 1000.0 / sourceElements.size()) / 10.0);
            response.add("elements", elements);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to check refinement status: " + e.getMessage());
        }
    }
}

package com.capellaagent.modelchat.tools.transition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
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
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.common.data.modellingcore.AbstractTrace;
import org.polarsys.capella.common.data.modellingcore.TraceableElement;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.fa.AbstractFunction;

/**
 * Reconciles two ARCADIA layers by comparing elements and their realization links.
 * <p>
 * Finds elements in the source layer with no realization in the target layer,
 * and elements in the target layer with broken or missing realization links back
 * to the source.
 */
public class ReconcileLayersTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "reconcile_layers";
    private static final String DESCRIPTION =
            "Compares two layers and reports missing or broken realization links.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public ReconcileLayersTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.TRANSITION);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("source_layer",
                "Source (higher-level) architecture layer",
                VALID_LAYERS));
        params.add(ToolParameter.requiredEnum("target_layer",
                "Target (lower-level) architecture layer",
                VALID_LAYERS));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String sourceLayer = getRequiredString(parameters, "source_layer").toLowerCase();
        String targetLayer = getRequiredString(parameters, "target_layer").toLowerCase();

        if (sourceLayer.equals(targetLayer)) {
            return ToolResult.error("source_layer and target_layer must be different");
        }

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();
            BlockArchitecture sourceArch = modelService.getArchitecture(session, sourceLayer);
            BlockArchitecture targetArch = modelService.getArchitecture(session, targetLayer);

            // Collect source elements (functions and components)
            List<EObject> sourceElements = collectRelevantElements(sourceArch);
            List<EObject> targetElements = collectRelevantElements(targetArch);

            // Check source elements for missing realizations in target
            JsonArray missingInTarget = new JsonArray();
            int consistentCount = 0;

            for (EObject srcObj : sourceElements) {
                boolean hasRealization = false;
                if (srcObj instanceof TraceableElement) {
                    TraceableElement te = (TraceableElement) srcObj;
                    try {
                        for (AbstractTrace trace : te.getIncomingTraces()) {
                            TraceableElement source = trace.getSourceElement();
                            if (source != null && targetLayer.equals(modelService.detectLayer(source))) {
                                hasRealization = true;
                                break;
                            }
                        }
                    } catch (Exception e) {
                        // Trace API may vary
                    }
                }

                if (hasRealization) {
                    consistentCount++;
                } else {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("name", getElementName(srcObj));
                    entry.addProperty("id", getElementId(srcObj));
                    entry.addProperty("type", srcObj.eClass().getName());
                    missingInTarget.add(entry);
                }
            }

            // Check target elements for broken realization links
            JsonArray brokenLinks = new JsonArray();
            Set<String> sourceIds = new HashSet<>();
            for (EObject srcObj : sourceElements) {
                sourceIds.add(getElementId(srcObj));
            }

            for (EObject tgtObj : targetElements) {
                if (!(tgtObj instanceof TraceableElement)) continue;
                TraceableElement te = (TraceableElement) tgtObj;

                try {
                    for (AbstractTrace trace : te.getOutgoingTraces()) {
                        TraceableElement target = trace.getTargetElement();
                        if (target == null) {
                            // Broken link - target is null
                            JsonObject entry = new JsonObject();
                            entry.addProperty("name", getElementName(tgtObj));
                            entry.addProperty("id", getElementId(tgtObj));
                            entry.addProperty("type", tgtObj.eClass().getName());
                            entry.addProperty("issue", "Realization target is null (broken link)");
                            brokenLinks.add(entry);
                        } else if (target.eResource() == null) {
                            // Target element was deleted/detached
                            JsonObject entry = new JsonObject();
                            entry.addProperty("name", getElementName(tgtObj));
                            entry.addProperty("id", getElementId(tgtObj));
                            entry.addProperty("type", tgtObj.eClass().getName());
                            entry.addProperty("issue", "Realization target is detached/deleted");
                            brokenLinks.add(entry);
                        }
                    }
                } catch (Exception e) {
                    // Trace API may vary
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("source_layer", sourceLayer);
            response.addProperty("target_layer", targetLayer);
            response.addProperty("source_element_count", sourceElements.size());
            response.addProperty("target_element_count", targetElements.size());
            response.addProperty("consistent_count", consistentCount);
            response.addProperty("missing_in_target_count", missingInTarget.size());
            response.add("missing_in_target", missingInTarget);
            response.addProperty("broken_links_count", brokenLinks.size());
            response.add("broken_links", brokenLinks);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to reconcile layers: " + e.getMessage());
        }
    }

    /**
     * Collects functions and components from an architecture,
     * filtering out root/unnamed elements.
     */
    private List<EObject> collectRelevantElements(BlockArchitecture architecture) {
        List<EObject> elements = new ArrayList<>();
        Iterator<EObject> allContents = architecture.eAllContents();
        while (allContents.hasNext()) {
            EObject obj = allContents.next();
            if (obj instanceof AbstractFunction) {
                String name = getElementName(obj);
                if (name != null && !name.isBlank() && !name.contains("Root")) {
                    elements.add(obj);
                }
            } else if (obj instanceof Component) {
                String name = getElementName(obj);
                if (name != null && !name.isBlank()) {
                    elements.add(obj);
                }
            }
        }
        return elements;
    }
}

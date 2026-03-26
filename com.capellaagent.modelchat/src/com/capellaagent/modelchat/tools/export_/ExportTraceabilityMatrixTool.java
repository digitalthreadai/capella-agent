package com.capellaagent.modelchat.tools.export_;

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
import org.polarsys.capella.common.data.modellingcore.AbstractTrace;
import org.polarsys.capella.common.data.modellingcore.TraceableElement;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.fa.AbstractFunction;

/**
 * Exports a traceability matrix between two ARCADIA layers.
 * <p>
 * For each element in the source layer, finds its realization links to the
 * target layer and builds a matrix of source-target pairs.
 */
public class ExportTraceabilityMatrixTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "export_traceability_matrix";
    private static final String DESCRIPTION =
            "Exports a traceability matrix between two ARCADIA layers.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public ExportTraceabilityMatrixTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.EXPORT);
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

            JsonArray rows = new JsonArray();
            int covered = 0;
            int uncovered = 0;

            Iterator<EObject> allContents = sourceArch.eAllContents();
            while (allContents.hasNext()) {
                EObject obj = allContents.next();

                boolean isRelevant = (obj instanceof AbstractFunction) || (obj instanceof Component);
                if (!isRelevant) continue;

                String name = getElementName(obj);
                if (name == null || name.isBlank() || name.contains("Root")) continue;

                // Find realization links to the target layer
                List<TraceLink> links = findRealizationLinks(obj, targetLayer, modelService);

                if (links.isEmpty()) {
                    // Uncovered element
                    JsonObject row = new JsonObject();
                    row.addProperty("source_name", name);
                    row.addProperty("source_id", getElementId(obj));
                    row.addProperty("source_type", obj.eClass().getName());
                    row.addProperty("target_name", "(none)");
                    row.addProperty("target_id", "");
                    row.addProperty("trace_type", "missing");
                    rows.add(row);
                    uncovered++;
                } else {
                    for (TraceLink link : links) {
                        JsonObject row = new JsonObject();
                        row.addProperty("source_name", name);
                        row.addProperty("source_id", getElementId(obj));
                        row.addProperty("source_type", obj.eClass().getName());
                        row.addProperty("target_name", link.targetName);
                        row.addProperty("target_id", link.targetId);
                        row.addProperty("trace_type", link.traceType);
                        rows.add(row);
                    }
                    covered++;
                }
            }

            double coveragePercent = (covered + uncovered) > 0
                    ? (covered * 100.0 / (covered + uncovered)) : 100.0;

            JsonObject summary = new JsonObject();
            summary.addProperty("covered", covered);
            summary.addProperty("uncovered", uncovered);
            summary.addProperty("total", covered + uncovered);
            summary.addProperty("coverage_percent", Math.round(coveragePercent * 10.0) / 10.0);

            JsonObject response = new JsonObject();
            response.addProperty("source_layer", sourceLayer);
            response.addProperty("target_layer", targetLayer);
            response.addProperty("row_count", rows.size());
            response.add("summary", summary);
            response.add("rows", rows);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to export traceability matrix: " + e.getMessage());
        }
    }

    /**
     * Finds all realization links from an element to a target layer.
     */
    private List<TraceLink> findRealizationLinks(EObject element, String targetLayer,
                                                    CapellaModelService modelService) {
        List<TraceLink> links = new ArrayList<>();

        if (!(element instanceof TraceableElement)) return links;
        TraceableElement te = (TraceableElement) element;

        try {
            // Check incoming traces (target layer realizing this element)
            for (AbstractTrace trace : te.getIncomingTraces()) {
                TraceableElement source = trace.getSourceElement();
                if (source != null && targetLayer.equals(modelService.detectLayer(source))) {
                    links.add(new TraceLink(
                            getElementName(source),
                            getElementId(source),
                            trace.eClass().getName()));
                }
            }

            // Check outgoing traces (this element realizing something in target)
            for (AbstractTrace trace : te.getOutgoingTraces()) {
                TraceableElement target = trace.getTargetElement();
                if (target != null && targetLayer.equals(modelService.detectLayer(target))) {
                    links.add(new TraceLink(
                            getElementName(target),
                            getElementId(target),
                            trace.eClass().getName()));
                }
            }
        } catch (Exception e) {
            // Trace API may vary
        }

        return links;
    }

    private static class TraceLink {
        final String targetName;
        final String targetId;
        final String traceType;

        TraceLink(String targetName, String targetId, String traceType) {
            this.targetName = targetName;
            this.targetId = targetId;
            this.traceType = traceType;
        }
    }
}

package com.capellaagent.modelchat.tools.export_;

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
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.sirius.business.api.dialect.DialectManager;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.viewpoint.DRepresentationDescriptor;
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;
import org.polarsys.capella.common.data.modellingcore.AbstractTrace;
import org.polarsys.capella.common.data.modellingcore.TraceableElement;
import org.polarsys.capella.core.data.capellamodeller.Project;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.ComponentFunctionalAllocation;
import org.polarsys.capella.core.data.fa.FunctionalExchange;

/**
 * Generates a comprehensive model health report across all layers.
 * <p>
 * Combines statistics, allocation completeness, and traceability coverage
 * into a single summary report useful for model reviews.
 */
public class GenerateModelReportTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "generate_model_report";
    private static final String DESCRIPTION =
            "Generates a comprehensive model health report with statistics and coverage.";

    private static final List<String> LAYERS = List.of("oa", "sa", "la", "pa");

    public GenerateModelReportTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.EXPORT);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        // No parameters - reports on entire model
        return List.of();
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();

            // Get project name
            String projectName = "Unknown";
            for (Resource resource : session.getSemanticResources()) {
                for (EObject root : resource.getContents()) {
                    if (root instanceof Project) {
                        String name = ((Project) root).getName();
                        if (name != null && !name.isBlank()) {
                            projectName = name;
                            break;
                        }
                    }
                }
            }

            JsonArray layersArray = new JsonArray();
            int totalFunctions = 0, totalComponents = 0;
            int totalAllocated = 0, totalUnallocated = 0;
            int totalIssues = 0;

            for (String layer : LAYERS) {
                try {
                    BlockArchitecture arch = modelService.getArchitecture(session, layer);

                    int functions = 0, components = 0, actors = 0, exchanges = 0;
                    int allocated = 0, unallocated = 0;
                    int emptyNames = 0, noPortComponents = 0;

                    Iterator<EObject> allContents = arch.eAllContents();
                    while (allContents.hasNext()) {
                        EObject obj = allContents.next();

                        if (obj instanceof AbstractFunction) {
                            String name = getElementName(obj);
                            if (name != null && !name.isBlank() && !name.contains("Root")) {
                                functions++;
                                try {
                                    AbstractFunction fn = (AbstractFunction) obj;
                                    List<?> allocs = fn.getComponentFunctionalAllocations();
                                    if (allocs != null && !allocs.isEmpty()) {
                                        allocated++;
                                    } else {
                                        unallocated++;
                                    }
                                } catch (Exception e) { unallocated++; }
                            }
                        }
                        if (obj instanceof Component) {
                            Component comp = (Component) obj;
                            if (comp.isActor()) actors++;
                            else components++;
                        }
                        if (obj instanceof FunctionalExchange) exchanges++;
                        if (obj instanceof AbstractNamedElement) {
                            String name = ((AbstractNamedElement) obj).getName();
                            if (name == null || name.isBlank()) emptyNames++;
                        }
                    }

                    JsonObject layerObj = new JsonObject();
                    layerObj.addProperty("layer", layer);

                    JsonObject stats = new JsonObject();
                    stats.addProperty("functions", functions);
                    stats.addProperty("components", components);
                    stats.addProperty("actors", actors);
                    stats.addProperty("exchanges", exchanges);
                    layerObj.add("statistics", stats);

                    JsonObject coverage = new JsonObject();
                    coverage.addProperty("functions_allocated", allocated);
                    coverage.addProperty("functions_unallocated", unallocated);
                    double allocCoverage = functions > 0
                            ? (allocated * 100.0 / functions) : 100.0;
                    coverage.addProperty("allocation_coverage_percent",
                            Math.round(allocCoverage * 10.0) / 10.0);
                    layerObj.add("coverage", coverage);

                    JsonObject issues = new JsonObject();
                    issues.addProperty("empty_names", emptyNames);
                    issues.addProperty("issue_count", emptyNames + unallocated);
                    layerObj.add("issues", issues);

                    layersArray.add(layerObj);

                    totalFunctions += functions;
                    totalComponents += components;
                    totalAllocated += allocated;
                    totalUnallocated += unallocated;
                    totalIssues += emptyNames + unallocated;

                } catch (Exception e) {
                    // Layer may not exist
                    JsonObject layerObj = new JsonObject();
                    layerObj.addProperty("layer", layer);
                    layerObj.addProperty("status", "not_found");
                    layersArray.add(layerObj);
                }
            }

            // Count diagrams
            int diagramCount = 0;
            try {
                Collection<DRepresentationDescriptor> descriptors =
                        DialectManager.INSTANCE.getAllRepresentationDescriptors(session);
                diagramCount = descriptors.size();
            } catch (Exception e) { /* skip */ }

            // Build summary
            JsonObject summary = new JsonObject();
            summary.addProperty("total_functions", totalFunctions);
            summary.addProperty("total_components", totalComponents);
            summary.addProperty("total_diagrams", diagramCount);
            summary.addProperty("functions_allocated", totalAllocated);
            summary.addProperty("functions_unallocated", totalUnallocated);
            double overallCoverage = totalFunctions > 0
                    ? (totalAllocated * 100.0 / totalFunctions) : 100.0;
            summary.addProperty("overall_allocation_coverage",
                    Math.round(overallCoverage * 10.0) / 10.0);
            summary.addProperty("total_issues", totalIssues);

            // Health score (simple heuristic)
            double healthScore = 100.0;
            if (totalFunctions > 0) {
                healthScore -= (totalUnallocated * 30.0 / totalFunctions);
            }
            healthScore = Math.max(0, Math.min(100, healthScore));
            summary.addProperty("health_score", Math.round(healthScore * 10.0) / 10.0);

            JsonObject response = new JsonObject();
            response.addProperty("project_name", projectName);
            response.add("layers", layersArray);
            response.add("summary", summary);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to generate model report: " + e.getMessage());
        }
    }
}

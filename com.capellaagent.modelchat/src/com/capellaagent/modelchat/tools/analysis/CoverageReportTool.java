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
import org.polarsys.capella.common.data.modellingcore.AbstractTrace;
import org.polarsys.capella.common.data.modellingcore.TraceableElement;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.FunctionalExchange;
import org.polarsys.capella.core.data.interaction.AbstractCapability;

/**
 * Measures requirement and traceability coverage as percentages.
 * <p>
 * For each major element type (functions, components, capabilities, exchanges),
 * calculates the percentage that have at least one incoming or outgoing trace
 * link (requirement allocation, realization, etc.). Helps assess model maturity
 * and ARCADIA coverage completeness.
 */
public class CoverageReportTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "coverage_report";
    private static final String DESCRIPTION =
            "Measures requirement/traceability coverage percentage across element types.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public CoverageReportTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.ANALYSIS);
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
            return ToolResult.error("Invalid layer '" + layer + "'");
        }

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();
            BlockArchitecture architecture = modelService.getArchitecture(session, layer);

            // Counters for each category
            int totalFunctions = 0, coveredFunctions = 0;
            int totalComponents = 0, coveredComponents = 0;
            int totalCapabilities = 0, coveredCapabilities = 0;
            int totalExchanges = 0, coveredExchanges = 0;

            JsonArray uncoveredFunctions = new JsonArray();
            JsonArray uncoveredComponents = new JsonArray();
            JsonArray uncoveredCapabilities = new JsonArray();

            Iterator<EObject> allContents = architecture.eAllContents();
            while (allContents.hasNext()) {
                EObject obj = allContents.next();

                if (obj instanceof AbstractFunction) {
                    String name = getElementName(obj);
                    if (name == null || name.isBlank() || name.contains("Root")) continue;

                    totalFunctions++;
                    if (hasTraceLinks(obj)) {
                        coveredFunctions++;
                    } else if (uncoveredFunctions.size() < 50) {
                        JsonObject entry = new JsonObject();
                        entry.addProperty("name", name);
                        entry.addProperty("id", getElementId(obj));
                        entry.addProperty("type", obj.eClass().getName());
                        uncoveredFunctions.add(entry);
                    }
                } else if (obj instanceof Component) {
                    totalComponents++;
                    if (hasTraceLinks(obj)) {
                        coveredComponents++;
                    } else if (uncoveredComponents.size() < 50) {
                        JsonObject entry = new JsonObject();
                        entry.addProperty("name", getElementName(obj));
                        entry.addProperty("id", getElementId(obj));
                        entry.addProperty("type", obj.eClass().getName());
                        uncoveredComponents.add(entry);
                    }
                } else if (obj instanceof AbstractCapability) {
                    totalCapabilities++;
                    if (hasTraceLinks(obj)) {
                        coveredCapabilities++;
                    } else if (uncoveredCapabilities.size() < 50) {
                        JsonObject entry = new JsonObject();
                        entry.addProperty("name", getElementName(obj));
                        entry.addProperty("id", getElementId(obj));
                        entry.addProperty("type", obj.eClass().getName());
                        uncoveredCapabilities.add(entry);
                    }
                } else if (obj instanceof FunctionalExchange) {
                    totalExchanges++;
                    if (hasTraceLinks(obj)) {
                        coveredExchanges++;
                    }
                }
            }

            // Build category reports
            JsonObject functionsReport = buildCategoryReport(
                    "functions", totalFunctions, coveredFunctions, uncoveredFunctions);
            JsonObject componentsReport = buildCategoryReport(
                    "components", totalComponents, coveredComponents, uncoveredComponents);
            JsonObject capabilitiesReport = buildCategoryReport(
                    "capabilities", totalCapabilities, coveredCapabilities, uncoveredCapabilities);
            JsonObject exchangesReport = buildCategoryReport(
                    "exchanges", totalExchanges, coveredExchanges, new JsonArray());

            // Overall coverage
            int totalAll = totalFunctions + totalComponents + totalCapabilities + totalExchanges;
            int coveredAll = coveredFunctions + coveredComponents + coveredCapabilities + coveredExchanges;
            double overallCoverage = totalAll == 0 ? 100.0 : (coveredAll * 100.0) / totalAll;

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("overall_coverage_percent",
                    Math.round(overallCoverage * 10.0) / 10.0);
            response.addProperty("total_elements", totalAll);
            response.addProperty("covered_elements", coveredAll);
            response.add("functions", functionsReport);
            response.add("components", componentsReport);
            response.add("capabilities", capabilitiesReport);
            response.add("exchanges", exchangesReport);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to generate coverage report: " + e.getMessage());
        }
    }

    /**
     * Checks if an element has any trace links (incoming or outgoing).
     */
    private boolean hasTraceLinks(EObject element) {
        if (!(element instanceof TraceableElement)) {
            return false;
        }

        TraceableElement traceable = (TraceableElement) element;

        List<AbstractTrace> outgoing = traceable.getOutgoingTraces();
        if (outgoing != null && !outgoing.isEmpty()) {
            return true;
        }

        List<AbstractTrace> incoming = traceable.getIncomingTraces();
        return incoming != null && !incoming.isEmpty();
    }

    /**
     * Builds a JSON report for a single element category.
     */
    private JsonObject buildCategoryReport(String category, int total, int covered,
                                            JsonArray uncoveredList) {
        JsonObject report = new JsonObject();
        report.addProperty("category", category);
        report.addProperty("total", total);
        report.addProperty("covered", covered);
        report.addProperty("uncovered", total - covered);

        double pct = total == 0 ? 100.0 : (covered * 100.0) / total;
        report.addProperty("coverage_percent", Math.round(pct * 10.0) / 10.0);

        if (uncoveredList.size() > 0) {
            report.add("uncovered_elements", uncoveredList);
        }

        return report;
    }
}

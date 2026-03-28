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
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.sirius.business.api.dialect.DialectManager;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;
import org.polarsys.capella.common.data.modellingcore.AbstractTrace;
import org.polarsys.capella.common.data.modellingcore.TraceableElement;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.cs.Interface;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.ComponentExchange;
import org.polarsys.capella.core.data.fa.FunctionalExchange;
import org.polarsys.capella.core.data.interaction.AbstractCapability;
import org.polarsys.capella.core.data.capellacommon.StateMachine;
import org.polarsys.capella.core.data.information.ExchangeItem;
import org.polarsys.capella.core.data.capellacore.Constraint;
import org.polarsys.capella.core.data.capellamodeller.Project;
import org.eclipse.sirius.viewpoint.DRepresentationDescriptor;

/**
 * Gathers comprehensive model statistics and context for LLM-generated summaries.
 * <p>
 * Returns structured data about each architecture layer including element counts,
 * top-level elements, diagrams, and key metrics. The LLM uses this data to
 * generate an executive brief or model summary.
 */
public class SummarizeModelTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "summarize_model";
    private static final String DESCRIPTION =
            "Gathers model context for AI-generated executive summary/brief across all layers.";

    public SummarizeModelTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.AI_INTELLIGENCE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalString("focus",
                "Optional focus area: overview, functions, components, exchanges, "
                + "capabilities, or all (default: overview)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String focus = getOptionalString(parameters, "focus", "overview").toLowerCase();

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();

            // Get project name
            String projectName = "Unknown Project";
            for (Resource res : session.getSemanticResources()) {
                for (EObject root : res.getContents()) {
                    if (root instanceof Project) {
                        projectName = getElementName(root);
                        break;
                    }
                    String name = getElementName(root);
                    if (name != null && !name.isBlank()) {
                        projectName = name;
                        break;
                    }
                }
            }

            // Collect per-layer statistics
            JsonArray layerSummaries = new JsonArray();
            String[] layers = {"oa", "sa", "la", "pa"};
            String[] layerNames = {"Operational Analysis", "System Analysis",
                    "Logical Architecture", "Physical Architecture"};

            int totalElements = 0;

            for (int i = 0; i < layers.length; i++) {
                try {
                    BlockArchitecture arch = modelService.getArchitecture(session, layers[i]);
                    JsonObject layerSummary = summarizeLayer(arch, layers[i], layerNames[i], focus);
                    layerSummaries.add(layerSummary);
                    totalElements += layerSummary.get("total_elements").getAsInt();
                } catch (Exception e) {
                    // Layer might not exist or be empty
                    JsonObject empty = new JsonObject();
                    empty.addProperty("layer", layers[i]);
                    empty.addProperty("layer_name", layerNames[i]);
                    empty.addProperty("total_elements", 0);
                    empty.addProperty("status", "empty or unavailable");
                    layerSummaries.add(empty);
                }
            }

            // Count diagrams
            int diagramCount = 0;
            try {
                diagramCount = DialectManager.INSTANCE
                        .getAllRepresentationDescriptors(session).size();
            } catch (Exception e) {
                // Ignore
            }

            JsonObject response = new JsonObject();
            response.addProperty("project_name", projectName);
            response.addProperty("total_elements_across_layers", totalElements);
            response.addProperty("diagram_count", diagramCount);
            response.addProperty("layer_count", layers.length);
            response.add("layers", layerSummaries);
            response.addProperty("summary_prompt",
                    "Based on the model data above, generate a concise executive summary "
                    + "covering: project scope, architecture maturity per layer, "
                    + "key components and functions, coverage gaps, and recommendations.");

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to gather model summary data: " + e.getMessage());
        }
    }

    /**
     * Summarizes a single architecture layer.
     */
    private JsonObject summarizeLayer(BlockArchitecture arch, String layerCode,
                                       String layerName, String focus) {
        int functions = 0, components = 0, exchanges = 0, capabilities = 0;
        int interfaces = 0, exchangeItems = 0, stateMachines = 0, constraints = 0;
        int traceCount = 0;

        JsonArray topFunctions = new JsonArray();
        JsonArray topComponents = new JsonArray();
        JsonArray topCapabilities = new JsonArray();

        Iterator<EObject> it = arch.eAllContents();
        while (it.hasNext()) {
            EObject obj = it.next();

            if (obj instanceof AbstractFunction) {
                String name = getElementName(obj);
                if (name != null && !name.isBlank() && !name.contains("Root")) {
                    functions++;
                    if (topFunctions.size() < 10) {
                        JsonObject f = new JsonObject();
                        f.addProperty("name", name);
                        f.addProperty("id", getElementId(obj));
                        topFunctions.add(f);
                    }
                }
            } else if (obj instanceof Component) {
                components++;
                if (topComponents.size() < 10) {
                    JsonObject c = new JsonObject();
                    c.addProperty("name", getElementName(obj));
                    c.addProperty("id", getElementId(obj));
                    c.addProperty("is_actor", ((Component) obj).isActor());
                    topComponents.add(c);
                }
            } else if (obj instanceof FunctionalExchange || obj instanceof ComponentExchange) {
                exchanges++;
            } else if (obj instanceof AbstractCapability) {
                capabilities++;
                if (topCapabilities.size() < 10) {
                    JsonObject cap = new JsonObject();
                    cap.addProperty("name", getElementName(obj));
                    cap.addProperty("id", getElementId(obj));
                    topCapabilities.add(cap);
                }
            } else if (obj instanceof Interface) {
                interfaces++;
            } else if (obj instanceof ExchangeItem) {
                exchangeItems++;
            } else if (obj instanceof StateMachine) {
                stateMachines++;
            } else if (obj instanceof Constraint) {
                constraints++;
            }

            // Count trace links
            if (obj instanceof TraceableElement) {
                TraceableElement te = (TraceableElement) obj;
                List<AbstractTrace> out = te.getOutgoingTraces();
                if (out != null) traceCount += out.size();
            }
        }

        int total = functions + components + exchanges + capabilities
                + interfaces + exchangeItems + stateMachines + constraints;

        JsonObject summary = new JsonObject();
        summary.addProperty("layer", layerCode);
        summary.addProperty("layer_name", layerName);
        summary.addProperty("total_elements", total);
        summary.addProperty("functions", functions);
        summary.addProperty("components", components);
        summary.addProperty("exchanges", exchanges);
        summary.addProperty("capabilities", capabilities);
        summary.addProperty("interfaces", interfaces);
        summary.addProperty("exchange_items", exchangeItems);
        summary.addProperty("state_machines", stateMachines);
        summary.addProperty("constraints", constraints);
        summary.addProperty("trace_links", traceCount);

        if ("overview".equals(focus) || "all".equals(focus) || "functions".equals(focus)) {
            summary.add("top_functions", topFunctions);
        }
        if ("overview".equals(focus) || "all".equals(focus) || "components".equals(focus)) {
            summary.add("top_components", topComponents);
        }
        if ("overview".equals(focus) || "all".equals(focus) || "capabilities".equals(focus)) {
            summary.add("top_capabilities", topCapabilities);
        }

        return summary;
    }
}

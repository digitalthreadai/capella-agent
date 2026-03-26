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
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.FunctionalExchange;

/**
 * Generates an FMEA-style safety report identifying potential failure modes
 * based on model structure analysis.
 */
public class GenerateSafetyReportTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("sa", "la", "pa");

    public GenerateSafetyReportTool() {
        super("generate_safety_report",
                "Generates FMEA-style safety analysis from model structure.",
                ToolCategory.ANALYSIS);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: sa, la, pa",
                VALID_LAYERS));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();
            BlockArchitecture arch = modelService.getArchitecture(session, layer);

            JsonArray failureModes = new JsonArray();

            Iterator<EObject> it = arch.eAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();

                // Functions with single input/output = single point of failure
                if (obj instanceof AbstractFunction) {
                    AbstractFunction func = (AbstractFunction) obj;
                    String name = getElementName(func);
                    if (name == null || name.isBlank() || name.contains("Root")) continue;

                    int inCount = func.getIncoming().size();
                    int outCount = func.getOutgoing().size();

                    if (inCount == 1 && outCount == 1) {
                        JsonObject fm = new JsonObject();
                        fm.addProperty("element_name", name);
                        fm.addProperty("element_id", getElementId(func));
                        fm.addProperty("element_type", "Function");
                        fm.addProperty("failure_mode", "Single point of failure - single input/output path");
                        fm.addProperty("severity", "medium");
                        fm.addProperty("recommendation", "Consider redundancy or alternative paths");
                        failureModes.add(fm);
                    }

                    if (outCount == 0 && inCount > 0) {
                        JsonObject fm = new JsonObject();
                        fm.addProperty("element_name", name);
                        fm.addProperty("element_id", getElementId(func));
                        fm.addProperty("element_type", "Function");
                        fm.addProperty("failure_mode", "Sink function - data loss if function fails");
                        fm.addProperty("severity", "low");
                        fm.addProperty("recommendation", "Verify data persistence or monitoring");
                        failureModes.add(fm);
                    }
                }

                // Components with many exchanges = high coupling risk
                if (obj instanceof Component) {
                    Component comp = (Component) obj;
                    if (comp.isActor()) continue;
                    String name = getElementName(comp);
                    if (name == null || name.isBlank()) continue;

                    int exchangeCount = 0;
                    Iterator<EObject> compIt = comp.eAllContents();
                    while (compIt.hasNext()) {
                        if (compIt.next() instanceof FunctionalExchange) {
                            exchangeCount++;
                        }
                    }

                    if (exchangeCount > 10) {
                        JsonObject fm = new JsonObject();
                        fm.addProperty("element_name", name);
                        fm.addProperty("element_id", getElementId(comp));
                        fm.addProperty("element_type", "Component");
                        fm.addProperty("failure_mode",
                                "High coupling (" + exchangeCount + " exchanges) - cascading failure risk");
                        fm.addProperty("severity", "high");
                        fm.addProperty("recommendation", "Consider decomposing or adding error isolation");
                        failureModes.add(fm);
                    }
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("failure_mode_count", failureModes.size());
            response.add("failure_modes", failureModes);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to generate safety report: " + e.getMessage());
        }
    }
}

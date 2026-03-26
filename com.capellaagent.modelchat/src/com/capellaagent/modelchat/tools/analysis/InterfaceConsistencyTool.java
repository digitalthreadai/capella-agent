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
import org.polarsys.capella.core.data.cs.Interface;
import org.polarsys.capella.core.data.fa.ComponentExchange;
import org.polarsys.capella.core.data.fa.ComponentPort;
import org.polarsys.capella.core.data.information.ExchangeItem;

/**
 * Checks interface consistency: ports match interfaces, exchange items allocated.
 */
public class InterfaceConsistencyTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public InterfaceConsistencyTool() {
        super("interface_consistency",
                "Checks interface consistency: ports, exchange items.",
                ToolCategory.ANALYSIS);
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

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();
            BlockArchitecture arch = modelService.getArchitecture(session, layer);

            JsonArray consistent = new JsonArray();
            JsonArray inconsistencies = new JsonArray();

            // Check interfaces have exchange items
            Iterator<EObject> it = arch.eAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();

                if (obj instanceof Interface) {
                    Interface iface = (Interface) obj;
                    if (iface.getExchangeItems().isEmpty()) {
                        JsonObject issue = new JsonObject();
                        issue.addProperty("element", getElementName(iface));
                        issue.addProperty("element_id", getElementId(iface));
                        issue.addProperty("issue", "Interface has no exchange items");
                        issue.addProperty("severity", "warning");
                        inconsistencies.add(issue);
                    } else {
                        JsonObject ok = new JsonObject();
                        ok.addProperty("element", getElementName(iface));
                        ok.addProperty("element_id", getElementId(iface));
                        ok.addProperty("exchange_item_count", iface.getExchangeItems().size());
                        consistent.add(ok);
                    }
                }

                if (obj instanceof ComponentExchange) {
                    ComponentExchange ce = (ComponentExchange) obj;
                    if (ce.getConvoyedInformations().isEmpty()) {
                        JsonObject issue = new JsonObject();
                        issue.addProperty("element", getElementName(ce));
                        issue.addProperty("element_id", getElementId(ce));
                        issue.addProperty("issue", "Component exchange has no conveyed exchange items");
                        issue.addProperty("severity", "info");
                        inconsistencies.add(issue);
                    }
                }

                if (obj instanceof ComponentPort) {
                    ComponentPort port = (ComponentPort) obj;
                    if (port.getProvidedInterfaces().isEmpty()
                            && port.getRequiredInterfaces().isEmpty()) {
                        JsonObject issue = new JsonObject();
                        issue.addProperty("element", getElementName(port));
                        issue.addProperty("element_id", getElementId(port));
                        issue.addProperty("issue", "Port has no provided or required interfaces");
                        issue.addProperty("severity", "info");
                        inconsistencies.add(issue);
                    }
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("consistent_count", consistent.size());
            response.addProperty("inconsistent_count", inconsistencies.size());
            response.add("consistent", consistent);
            response.add("inconsistent", inconsistencies);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to check interface consistency: " + e.getMessage());
        }
    }
}

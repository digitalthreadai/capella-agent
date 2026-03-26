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
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.cs.Interface;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.ComponentExchange;
import org.polarsys.capella.core.data.fa.FunctionalExchange;

/**
 * Gathers structured model context for LLM-based architecture review.
 * Returns component decomposition, exchange patterns, and metrics
 * so the LLM can analyze and provide improvement recommendations.
 */
public class ReviewArchitectureTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public ReviewArchitectureTool() {
        super("review_architecture",
                "Gathers model context for AI architecture review.",
                ToolCategory.AI_INTELLIGENCE);
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

            JsonArray components = new JsonArray();
            JsonArray functions = new JsonArray();
            JsonArray exchanges = new JsonArray();
            JsonArray interfaces = new JsonArray();

            Iterator<EObject> it = arch.eAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();

                if (obj instanceof Component) {
                    Component comp = (Component) obj;
                    JsonObject c = new JsonObject();
                    c.addProperty("name", getElementName(comp));
                    c.addProperty("id", getElementId(comp));
                    c.addProperty("is_actor", comp.isActor());
                    c.addProperty("parent", comp.eContainer() != null
                            ? getElementName(comp.eContainer()) : "");
                    components.add(c);
                }
                if (obj instanceof AbstractFunction) {
                    String name = getElementName(obj);
                    if (name != null && !name.isBlank() && !name.contains("Root")) {
                        JsonObject f = new JsonObject();
                        f.addProperty("name", name);
                        f.addProperty("id", getElementId(obj));
                        f.addProperty("parent", obj.eContainer() != null
                                ? getElementName(obj.eContainer()) : "");
                        functions.add(f);
                    }
                }
                if (obj instanceof FunctionalExchange) {
                    FunctionalExchange fe = (FunctionalExchange) obj;
                    JsonObject e = new JsonObject();
                    e.addProperty("name", getElementName(fe));
                    e.addProperty("source", fe.getSource() != null
                            ? getElementName(fe.getSource().eContainer()) : "");
                    e.addProperty("target", fe.getTarget() != null
                            ? getElementName(fe.getTarget().eContainer()) : "");
                    e.addProperty("type", "functional");
                    exchanges.add(e);
                }
                if (obj instanceof ComponentExchange) {
                    ComponentExchange ce = (ComponentExchange) obj;
                    JsonObject e = new JsonObject();
                    e.addProperty("name", getElementName(ce));
                    e.addProperty("source", ce.getSource() != null
                            ? getElementName(ce.getSource().eContainer()) : "");
                    e.addProperty("target", ce.getTarget() != null
                            ? getElementName(ce.getTarget().eContainer()) : "");
                    e.addProperty("type", "component");
                    exchanges.add(e);
                }
                if (obj instanceof Interface) {
                    Interface iface = (Interface) obj;
                    JsonObject i = new JsonObject();
                    i.addProperty("name", getElementName(iface));
                    i.addProperty("exchange_item_count", iface.getExchangeItems().size());
                    interfaces.add(i);
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("component_count", components.size());
            response.addProperty("function_count", functions.size());
            response.addProperty("exchange_count", exchanges.size());
            response.addProperty("interface_count", interfaces.size());
            response.add("components", components);
            response.add("functions", functions);
            response.add("exchanges", exchanges);
            response.add("interfaces", interfaces);
            response.addProperty("review_prompt",
                    "Analyze this architecture for: coupling, cohesion, naming, "
                    + "decomposition balance, interface completeness, and ARCADIA best practices.");
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to gather review context: " + e.getMessage());
        }
    }
}

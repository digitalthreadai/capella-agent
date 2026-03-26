package com.capellaagent.modelchat.tools.ai;

import java.util.ArrayList;
import java.util.HashMap;
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
import org.polarsys.capella.core.data.fa.ComponentExchange;
import org.polarsys.capella.core.data.fa.FunctionalExchange;

/**
 * Gathers exchange patterns for the LLM to suggest interface definitions.
 * Identifies component pairs that communicate and their exchange items,
 * enabling AI-driven interface suggestions.
 */
public class SuggestInterfacesTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("sa", "la", "pa");

    public SuggestInterfacesTool() {
        super("suggest_interfaces",
                "Gathers exchange patterns for AI interface suggestions.",
                ToolCategory.AI_INTELLIGENCE);
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

            // Map component pairs to their exchanges
            Map<String, JsonArray> pairExchanges = new HashMap<>();
            JsonArray allExchanges = new JsonArray();

            Iterator<EObject> it = arch.eAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();

                if (obj instanceof ComponentExchange) {
                    ComponentExchange ce = (ComponentExchange) obj;
                    String src = ce.getSource() != null
                            ? getElementName(ce.getSource().eContainer()) : "unknown";
                    String tgt = ce.getTarget() != null
                            ? getElementName(ce.getTarget().eContainer()) : "unknown";

                    String pairKey = src + " <-> " + tgt;
                    pairExchanges.computeIfAbsent(pairKey, k -> new JsonArray());

                    JsonObject ex = new JsonObject();
                    ex.addProperty("name", getElementName(ce));
                    ex.addProperty("source", src);
                    ex.addProperty("target", tgt);
                    ex.addProperty("conveyed_count", ce.getConvoyedInformations().size());
                    pairExchanges.get(pairKey).add(ex);
                    allExchanges.add(ex);
                }
            }

            // Build pair summaries
            JsonArray pairs = new JsonArray();
            for (Map.Entry<String, JsonArray> entry : pairExchanges.entrySet()) {
                JsonObject pair = new JsonObject();
                pair.addProperty("component_pair", entry.getKey());
                pair.addProperty("exchange_count", entry.getValue().size());
                pair.add("exchanges", entry.getValue());
                pairs.add(pair);
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("total_exchanges", allExchanges.size());
            response.addProperty("component_pairs", pairs.size());
            response.add("pairs", pairs);
            response.addProperty("suggestion_prompt",
                    "Based on these component exchange patterns, suggest Interface definitions "
                    + "that should be created, including which ExchangeItems to allocate.");
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to gather interface context: " + e.getMessage());
        }
    }
}

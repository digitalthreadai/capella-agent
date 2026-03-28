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
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.information.communication.CommunicationLink;
import org.polarsys.capella.core.data.information.communication.CommunicationLinkKind;
import org.polarsys.capella.core.data.information.ExchangeItem;

/**
 * Lists communication links in the specified architecture layer.
 * <p>
 * Communication links define how components interact with exchange items
 * (SEND, RECEIVE, PRODUCE, CONSUME, etc.). This tool collects all
 * {@link CommunicationLink} instances and returns their details.
 */
public class GetCommunicationLinksTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "get_communication_links";
    private static final String DESCRIPTION =
            "Lists communication links (SEND, RECEIVE, PRODUCE, CONSUME) in a layer.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public GetCommunicationLinksTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS));
        params.add(ToolParameter.optionalInteger("max_results",
                "Maximum number of links to return (default: 200, max: 500)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        int maxResults = getOptionalInt(parameters, "max_results", 200);
        maxResults = Math.max(1, Math.min(maxResults, 500));

        if (!VALID_LAYERS.contains(layer)) {
            return ToolResult.error("Invalid layer '" + layer
                    + "'. Must be one of: " + String.join(", ", VALID_LAYERS));
        }

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();
            BlockArchitecture architecture = modelService.getArchitecture(session, layer);

            JsonArray linksArray = new JsonArray();
            int totalFound = 0;

            Iterator<EObject> allContents = architecture.eAllContents();
            while (allContents.hasNext()) {
                EObject obj = allContents.next();

                if (!(obj instanceof CommunicationLink)) {
                    continue;
                }

                totalFound++;
                if (linksArray.size() >= maxResults) {
                    continue; // Keep counting but stop adding
                }

                CommunicationLink link = (CommunicationLink) obj;
                JsonObject linkObj = new JsonObject();
                linkObj.addProperty("name", getElementName(link));
                linkObj.addProperty("id", getElementId(link));

                // Kind: SEND, RECEIVE, PRODUCE, CONSUME, CALL, EXECUTE, etc.
                CommunicationLinkKind kind = link.getKind();
                linkObj.addProperty("kind", kind != null ? kind.getLiteral() : "UNSET");

                // Exchange item referenced by the link
                ExchangeItem exchangeItem = link.getExchangeItem();
                if (exchangeItem != null) {
                    JsonObject eiObj = new JsonObject();
                    eiObj.addProperty("name", getElementName(exchangeItem));
                    eiObj.addProperty("id", getElementId(exchangeItem));
                    eiObj.addProperty("type", exchangeItem.eClass().getName());
                    linkObj.add("exchange_item", eiObj);
                }

                // Owning component/interface
                EObject owner = link.eContainer();
                if (owner != null) {
                    linkObj.addProperty("owner_name", getElementName(owner));
                    linkObj.addProperty("owner_id", getElementId(owner));
                    linkObj.addProperty("owner_type", owner.eClass().getName());
                }

                linksArray.add(linkObj);
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("total_found", totalFound);
            response.addProperty("returned_count", linksArray.size());
            response.addProperty("truncated", totalFound > maxResults);
            response.add("communication_links", linksArray);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to get communication links: " + e.getMessage());
        }
    }
}

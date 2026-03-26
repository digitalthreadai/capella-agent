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
import org.polarsys.capella.core.data.information.ExchangeItem;
import org.polarsys.capella.core.data.information.ExchangeItemElement;

/**
 * Lists exchange items and their elements in a layer or interface.
 */
public class GetExchangeItemsTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public GetExchangeItemsTool() {
        super("get_exchange_items",
                "Lists exchange items with their elements.",
                ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS, null));
        params.add(ToolParameter.optionalString("interface_uuid",
                "UUID of interface to filter exchange items"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getOptionalString(parameters, "layer", null);
        String interfaceUuid = getOptionalString(parameters, "interface_uuid", null);

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            List<ExchangeItem> items = new ArrayList<>();

            if (interfaceUuid != null && !interfaceUuid.isBlank()) {
                EObject iface = resolveElementByUuid(interfaceUuid);
                if (iface == null) {
                    return ToolResult.error("Interface not found: " + interfaceUuid);
                }
                Iterator<EObject> it = iface.eAllContents();
                while (it.hasNext()) {
                    EObject obj = it.next();
                    if (obj instanceof ExchangeItem) {
                        items.add((ExchangeItem) obj);
                    }
                }
            } else {
                List<String> layers = (layer != null && !layer.isBlank())
                        ? List.of(layer.toLowerCase()) : VALID_LAYERS;
                for (String l : layers) {
                    try {
                        BlockArchitecture arch = modelService.getArchitecture(session, l);
                        Iterator<EObject> it = arch.eAllContents();
                        while (it.hasNext()) {
                            EObject obj = it.next();
                            if (obj instanceof ExchangeItem) {
                                items.add((ExchangeItem) obj);
                            }
                        }
                    } catch (Exception e) { /* skip */ }
                }
            }

            JsonArray results = new JsonArray();
            for (ExchangeItem ei : items) {
                JsonObject eiObj = new JsonObject();
                eiObj.addProperty("name", getElementName(ei));
                eiObj.addProperty("id", getElementId(ei));
                eiObj.addProperty("kind", ei.getExchangeMechanism() != null
                        ? ei.getExchangeMechanism().getName() : "UNSET");

                JsonArray elements = new JsonArray();
                for (ExchangeItemElement elem : ei.getOwnedElements()) {
                    JsonObject elemObj = new JsonObject();
                    elemObj.addProperty("name", getElementName(elem));
                    elemObj.addProperty("type",
                            elem.getType() != null ? getElementName(elem.getType()) : "untyped");
                    elements.add(elemObj);
                }
                eiObj.add("elements", elements);
                results.add(eiObj);
            }

            JsonObject response = new JsonObject();
            response.addProperty("count", results.size());
            response.add("exchange_items", results);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to get exchange items: " + e.getMessage());
        }
    }
}

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
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.cs.Interface;
import org.polarsys.capella.core.data.information.ExchangeItem;

/**
 * Lists interfaces in a Capella architecture layer with their exchange items,
 * implementor components, and user components.
 */
public class GetInterfacesTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "get_interfaces";
    private static final String DESCRIPTION =
            "Lists interfaces with exchange items, implementors, and users.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public GetInterfacesTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalEnum("layer",
                "Architecture layer to query (searches all if omitted)",
                VALID_LAYERS, null));
        params.add(ToolParameter.optionalString("component_uuid",
                "Filter to interfaces of a specific component"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getOptionalString(parameters, "layer", null);
        String componentUuid = getOptionalString(parameters, "component_uuid", null);

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();

            // If filtering by component, resolve it first
            Component filterComponent = null;
            if (componentUuid != null && !componentUuid.isBlank()) {
                EObject compObj = resolveElementByUuid(componentUuid);
                if (compObj == null) {
                    return ToolResult.error("Component not found with UUID: " + componentUuid);
                }
                if (!(compObj instanceof Component)) {
                    return ToolResult.error("Element is not a Component (type: "
                            + compObj.eClass().getName() + ")");
                }
                filterComponent = (Component) compObj;
            }

            List<String> layersToSearch = new ArrayList<>();
            if (layer != null && !layer.isBlank()) {
                layersToSearch.add(layer.toLowerCase());
            } else {
                layersToSearch.addAll(VALID_LAYERS);
            }

            JsonArray interfaces = new JsonArray();

            for (String l : layersToSearch) {
                try {
                    BlockArchitecture architecture = modelService.getArchitecture(session, l);
                    collectInterfaces(architecture, filterComponent, interfaces);
                } catch (Exception e) {
                    // Layer may not exist; skip
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer != null ? layer : "all");
            if (filterComponent != null) {
                response.addProperty("filter_component", getElementName(filterComponent));
                response.addProperty("filter_component_id", getElementId(filterComponent));
            }
            response.addProperty("interface_count", interfaces.size());
            response.add("interfaces", interfaces);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to get interfaces: " + e.getMessage());
        }
    }

    /**
     * Collects interfaces from an architecture, optionally filtering by a component.
     */
    private void collectInterfaces(BlockArchitecture architecture, Component filterComponent,
                                     JsonArray result) {
        Iterator<EObject> allContents = architecture.eAllContents();
        while (allContents.hasNext()) {
            EObject obj = allContents.next();
            if (!(obj instanceof Interface)) {
                continue;
            }

            Interface iface = (Interface) obj;

            // If filtering by component, check if this interface relates to it
            if (filterComponent != null) {
                boolean related = false;
                try {
                    List<Component> implementors = iface.getImplementorComponents();
                    List<Component> users = iface.getUserComponents();
                    if (implementors != null) {
                        for (Component c : implementors) {
                            if (c == filterComponent) { related = true; break; }
                        }
                    }
                    if (!related && users != null) {
                        for (Component c : users) {
                            if (c == filterComponent) { related = true; break; }
                        }
                    }
                } catch (Exception e) {
                    // API may vary; skip filter
                }
                if (!related) continue;
            }

            JsonObject ifaceObj = new JsonObject();
            ifaceObj.addProperty("name", getElementName(iface));
            ifaceObj.addProperty("id", getElementId(iface));
            ifaceObj.addProperty("type", iface.eClass().getName());

            // Exchange items
            JsonArray exchangeItems = new JsonArray();
            try {
                List<ExchangeItem> items = iface.getExchangeItems();
                if (items != null) {
                    for (ExchangeItem ei : items) {
                        JsonObject eiObj = new JsonObject();
                        eiObj.addProperty("name", getElementName(ei));
                        eiObj.addProperty("id", getElementId(ei));
                        exchangeItems.add(eiObj);
                    }
                }
            } catch (Exception e) {
                // ExchangeItem API may vary
            }
            ifaceObj.add("exchange_items", exchangeItems);

            // Implementor components
            JsonArray implementors = new JsonArray();
            try {
                List<Component> impls = iface.getImplementorComponents();
                if (impls != null) {
                    for (Component c : impls) {
                        JsonObject cObj = new JsonObject();
                        cObj.addProperty("name", getElementName(c));
                        cObj.addProperty("id", getElementId(c));
                        implementors.add(cObj);
                    }
                }
            } catch (Exception e) {
                // API may vary
            }
            ifaceObj.add("implementors", implementors);

            // User components
            JsonArray users = new JsonArray();
            try {
                List<Component> usrs = iface.getUserComponents();
                if (usrs != null) {
                    for (Component c : usrs) {
                        JsonObject cObj = new JsonObject();
                        cObj.addProperty("name", getElementName(c));
                        cObj.addProperty("id", getElementId(c));
                        users.add(cObj);
                    }
                }
            } catch (Exception e) {
                // API may vary
            }
            ifaceObj.add("users", users);

            result.add(ifaceObj);
        }
    }
}

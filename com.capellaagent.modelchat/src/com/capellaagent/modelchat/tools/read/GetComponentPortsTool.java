package com.capellaagent.modelchat.tools.read;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.fa.ComponentPort;
import org.polarsys.capella.core.data.fa.ComponentExchange;
import org.polarsys.capella.core.data.fa.ComponentPortKind;
import org.polarsys.capella.core.data.fa.OrientationPortKind;
import org.polarsys.capella.core.data.information.Port;

/**
 * Lists ComponentPort instances on a given component.
 * <p>
 * Returns each port's name, kind (STANDARD, FLOW), orientation (IN, OUT, INOUT),
 * and connected component exchanges.
 */
public class GetComponentPortsTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "get_component_ports";
    private static final String DESCRIPTION =
            "Lists ports (ComponentPort) on a component with kind, orientation, and connections.";

    public GetComponentPortsTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("component_uuid",
                "UUID of the component whose ports to list"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String componentUuid = getRequiredString(parameters, "component_uuid");

        try {
            EObject element = resolveElementByUuid(componentUuid);
            if (element == null) {
                return ToolResult.error("Component not found: " + componentUuid);
            }

            if (!(element instanceof Component)) {
                return ToolResult.error("Element is not a component (type: "
                        + element.eClass().getName() + ")");
            }

            Component component = (Component) element;
            JsonArray portsArray = new JsonArray();

            // Iterate owned features looking for ComponentPort instances
            List<? extends Port> containedPorts = component.getContainedComponentPorts();
            if (containedPorts != null) {
                for (Port port : containedPorts) {
                    if (port instanceof ComponentPort) {
                        ComponentPort cp = (ComponentPort) port;
                        JsonObject portObj = new JsonObject();
                        portObj.addProperty("name", getElementName(cp));
                        portObj.addProperty("id", getElementId(cp));
                        portObj.addProperty("type", "ComponentPort");

                        // Port kind
                        ComponentPortKind kind = cp.getKind();
                        portObj.addProperty("kind", kind != null ? kind.getLiteral() : "UNSET");

                        // Orientation
                        OrientationPortKind orientation = cp.getOrientation();
                        portObj.addProperty("orientation",
                                orientation != null ? orientation.getLiteral() : "UNSET");

                        // Connected component exchanges
                        JsonArray exchangesArray = new JsonArray();
                        List<ComponentExchange> exchanges = cp.getComponentExchanges();
                        if (exchanges != null) {
                            for (ComponentExchange ce : exchanges) {
                                JsonObject ceObj = new JsonObject();
                                ceObj.addProperty("name", getElementName(ce));
                                ceObj.addProperty("id", getElementId(ce));

                                // Determine the other end of the exchange
                                if (ce.getSourcePort() == cp && ce.getTargetPort() != null) {
                                    ceObj.addProperty("connected_to",
                                            getElementName(ce.getTargetPort().eContainer()));
                                    ceObj.addProperty("direction", "outgoing");
                                } else if (ce.getTargetPort() == cp && ce.getSourcePort() != null) {
                                    ceObj.addProperty("connected_to",
                                            getElementName(ce.getSourcePort().eContainer()));
                                    ceObj.addProperty("direction", "incoming");
                                }
                                exchangesArray.add(ceObj);
                            }
                        }
                        portObj.add("component_exchanges", exchangesArray);
                        portObj.addProperty("exchange_count", exchangesArray.size());

                        portsArray.add(portObj);
                    }
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("component_name", getElementName(component));
            response.addProperty("component_id", getElementId(component));
            response.addProperty("component_type", component.eClass().getName());
            response.addProperty("is_actor", component.isActor());
            response.addProperty("port_count", portsArray.size());
            response.add("ports", portsArray);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to get component ports: " + e.getMessage());
        }
    }
}

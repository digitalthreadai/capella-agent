package com.capellaagent.modelchat.tools.export_;

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
import org.polarsys.capella.core.data.fa.ComponentPort;
import org.polarsys.capella.core.data.information.ExchangeItem;
import org.polarsys.capella.core.data.information.Port;

/**
 * Generates an Interface Control Document (ICD) report for components.
 * <p>
 * For each component (or a specific one), lists its ports, provided interfaces,
 * required interfaces, and the exchange items flowing through each.
 */
public class GenerateIcdReportTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "generate_icd_report";
    private static final String DESCRIPTION =
            "Generates an ICD report showing component interfaces and exchange items.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public GenerateIcdReportTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.EXPORT);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS));
        params.add(ToolParameter.optionalString("component_uuid",
                "UUID of a specific component (all components if omitted)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        String componentUuid = getOptionalString(parameters, "component_uuid", null);

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();
            BlockArchitecture architecture = modelService.getArchitecture(session, layer);

            JsonArray components = new JsonArray();

            if (componentUuid != null && !componentUuid.isBlank()) {
                // Report for a specific component
                EObject obj = resolveElementByUuid(componentUuid);
                if (obj == null) {
                    return ToolResult.error("Component not found: " + componentUuid);
                }
                if (!(obj instanceof Component)) {
                    return ToolResult.error("Element is not a Component: " + obj.eClass().getName());
                }
                components.add(buildComponentIcd((Component) obj));
            } else {
                // Report for all components in the layer
                Iterator<EObject> allContents = architecture.eAllContents();
                while (allContents.hasNext()) {
                    EObject obj = allContents.next();
                    if (obj instanceof Component) {
                        Component comp = (Component) obj;
                        String name = getElementName(comp);
                        if (name != null && !name.isBlank()) {
                            components.add(buildComponentIcd(comp));
                        }
                    }
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("component_count", components.size());
            response.add("components", components);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to generate ICD report: " + e.getMessage());
        }
    }

    /**
     * Builds the ICD data for a single component.
     */
    private JsonObject buildComponentIcd(Component comp) {
        JsonObject compObj = new JsonObject();
        compObj.addProperty("name", getElementName(comp));
        compObj.addProperty("id", getElementId(comp));
        compObj.addProperty("type", comp.eClass().getName());
        compObj.addProperty("is_actor", comp.isActor());

        // Ports
        JsonArray ports = new JsonArray();
        try {
            List<? extends Port> componentPorts = comp.getContainedComponentPorts();
            if (componentPorts != null) {
                for (Port port : componentPorts) {
                    JsonObject portObj = new JsonObject();
                    portObj.addProperty("name", getElementName(port));
                    portObj.addProperty("id", getElementId(port));
                    portObj.addProperty("type", port.eClass().getName());

                    // Get interfaces allocated to this port
                    if (port instanceof ComponentPort) {
                        ComponentPort cp = (ComponentPort) port;
                        JsonArray providedIfaces = new JsonArray();
                        JsonArray requiredIfaces = new JsonArray();

                        try {
                            List<Interface> provided = cp.getProvidedInterfaces();
                            if (provided != null) {
                                for (Interface iface : provided) {
                                    providedIfaces.add(buildInterfaceJson(iface));
                                }
                            }
                        } catch (Exception e) { /* skip */ }

                        try {
                            List<Interface> required = cp.getRequiredInterfaces();
                            if (required != null) {
                                for (Interface iface : required) {
                                    requiredIfaces.add(buildInterfaceJson(iface));
                                }
                            }
                        } catch (Exception e) { /* skip */ }

                        portObj.add("provided_interfaces", providedIfaces);
                        portObj.add("required_interfaces", requiredIfaces);
                    }

                    ports.add(portObj);
                }
            }
        } catch (Exception e) {
            // Port API may vary
        }
        compObj.add("ports", ports);

        // Direct interface relationships
        JsonArray implementedInterfaces = new JsonArray();
        JsonArray usedInterfaces = new JsonArray();

        try {
            List<Interface> implemented = comp.getImplementedInterfaces();
            if (implemented != null) {
                for (Interface iface : implemented) {
                    implementedInterfaces.add(buildInterfaceJson(iface));
                }
            }
        } catch (Exception e) { /* skip */ }

        try {
            List<Interface> used = comp.getUsedInterfaces();
            if (used != null) {
                for (Interface iface : used) {
                    usedInterfaces.add(buildInterfaceJson(iface));
                }
            }
        } catch (Exception e) { /* skip */ }

        compObj.add("implemented_interfaces", implementedInterfaces);
        compObj.add("used_interfaces", usedInterfaces);

        return compObj;
    }

    /**
     * Builds JSON for an interface including its exchange items.
     */
    private JsonObject buildInterfaceJson(Interface iface) {
        JsonObject ifaceObj = new JsonObject();
        ifaceObj.addProperty("name", getElementName(iface));
        ifaceObj.addProperty("id", getElementId(iface));

        JsonArray exchangeItems = new JsonArray();
        try {
            List<ExchangeItem> items = iface.getExchangeItems();
            if (items != null) {
                for (ExchangeItem ei : items) {
                    JsonObject eiObj = new JsonObject();
                    eiObj.addProperty("name", getElementName(ei));
                    eiObj.addProperty("id", getElementId(ei));
                    eiObj.addProperty("type", ei.eClass().getName());
                    exchangeItems.add(eiObj);
                }
            }
        } catch (Exception e) { /* skip */ }
        ifaceObj.add("exchange_items", exchangeItems);

        return ifaceObj;
    }
}

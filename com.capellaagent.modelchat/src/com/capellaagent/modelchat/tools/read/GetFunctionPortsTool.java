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
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.FunctionInputPort;
import org.polarsys.capella.core.data.fa.FunctionOutputPort;
import org.polarsys.capella.core.data.fa.FunctionPort;
import org.polarsys.capella.core.data.fa.FunctionalExchange;
import org.polarsys.capella.core.data.information.ExchangeItem;

/**
 * Lists FunctionInputPort and FunctionOutputPort instances on a given function.
 * <p>
 * For each port, returns its name, direction, type, and connected exchanges/exchange items.
 */
public class GetFunctionPortsTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "get_function_ports";
    private static final String DESCRIPTION =
            "Lists input/output ports (FunctionInputPort, FunctionOutputPort) on a function.";

    public GetFunctionPortsTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("function_uuid",
                "UUID of the function whose ports to list"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String functionUuid = getRequiredString(parameters, "function_uuid");

        try {
            EObject element = resolveElementByUuid(functionUuid);
            if (element == null) {
                return ToolResult.error("Function not found: " + functionUuid);
            }

            if (!(element instanceof AbstractFunction)) {
                return ToolResult.error("Element is not a function (type: "
                        + element.eClass().getName() + ")");
            }

            AbstractFunction function = (AbstractFunction) element;
            JsonArray inputPorts = new JsonArray();
            JsonArray outputPorts = new JsonArray();

            // Collect input ports
            for (EObject port : function.getInputs()) {
                if (port instanceof FunctionInputPort) {
                    FunctionInputPort fip = (FunctionInputPort) port;
                    JsonObject portObj = new JsonObject();
                    portObj.addProperty("name", getElementName(fip));
                    portObj.addProperty("id", getElementId(fip));
                    portObj.addProperty("type", "FunctionInputPort");

                    // Incoming exchanges
                    JsonArray incomingExchanges = new JsonArray();
                    List<FunctionalExchange> exchanges = fip.getIncomingFunctionalExchanges();
                    if (exchanges != null) {
                        for (FunctionalExchange fe : exchanges) {
                            JsonObject feObj = new JsonObject();
                            feObj.addProperty("name", getElementName(fe));
                            feObj.addProperty("id", getElementId(fe));
                            incomingExchanges.add(feObj);
                        }
                    }
                    portObj.add("incoming_exchanges", incomingExchanges);

                    // Exchange items on port
                    JsonArray exchangeItems = new JsonArray();
                    List<ExchangeItem> items = fip.getIncomingExchangeItems();
                    if (items != null) {
                        for (ExchangeItem ei : items) {
                            JsonObject eiObj = new JsonObject();
                            eiObj.addProperty("name", getElementName(ei));
                            eiObj.addProperty("id", getElementId(ei));
                            exchangeItems.add(eiObj);
                        }
                    }
                    portObj.add("exchange_items", exchangeItems);

                    inputPorts.add(portObj);
                }
            }

            // Collect output ports
            for (EObject port : function.getOutputs()) {
                if (port instanceof FunctionOutputPort) {
                    FunctionOutputPort fop = (FunctionOutputPort) port;
                    JsonObject portObj = new JsonObject();
                    portObj.addProperty("name", getElementName(fop));
                    portObj.addProperty("id", getElementId(fop));
                    portObj.addProperty("type", "FunctionOutputPort");

                    // Outgoing exchanges
                    JsonArray outgoingExchanges = new JsonArray();
                    List<FunctionalExchange> exchanges = fop.getOutgoingFunctionalExchanges();
                    if (exchanges != null) {
                        for (FunctionalExchange fe : exchanges) {
                            JsonObject feObj = new JsonObject();
                            feObj.addProperty("name", getElementName(fe));
                            feObj.addProperty("id", getElementId(fe));
                            outgoingExchanges.add(feObj);
                        }
                    }
                    portObj.add("outgoing_exchanges", outgoingExchanges);

                    // Exchange items on port
                    JsonArray exchangeItems = new JsonArray();
                    List<ExchangeItem> items = fop.getOutgoingExchangeItems();
                    if (items != null) {
                        for (ExchangeItem ei : items) {
                            JsonObject eiObj = new JsonObject();
                            eiObj.addProperty("name", getElementName(ei));
                            eiObj.addProperty("id", getElementId(ei));
                            exchangeItems.add(eiObj);
                        }
                    }
                    portObj.add("exchange_items", exchangeItems);

                    outputPorts.add(portObj);
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("function_name", getElementName(function));
            response.addProperty("function_id", getElementId(function));
            response.addProperty("function_type", function.eClass().getName());
            response.addProperty("input_port_count", inputPorts.size());
            response.addProperty("output_port_count", outputPorts.size());
            response.add("input_ports", inputPorts);
            response.add("output_ports", outputPorts);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to get function ports: " + e.getMessage());
        }
    }
}

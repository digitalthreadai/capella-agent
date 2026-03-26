package com.capellaagent.modelchat.tools.write;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.capella.CapellaModelService;
import com.capellaagent.core.security.InputValidator;
import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.cs.CsFactory;
import org.polarsys.capella.core.data.cs.PhysicalPort;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.ComponentPort;
import org.polarsys.capella.core.data.fa.FaFactory;
import org.polarsys.capella.core.data.fa.FunctionInputPort;
import org.polarsys.capella.core.data.fa.FunctionOutputPort;
import org.polarsys.capella.core.data.fa.OrientationPortKind;

/**
 * Creates a port on a function or component.
 */
public class CreatePortTool extends AbstractCapellaTool {

    private static final List<String> VALID_PORT_TYPES = List.of(
            "function_input", "function_output", "component", "physical");

    public CreatePortTool() {
        super("create_port",
                "Creates a port on a function or component.",
                ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("element_uuid",
                "UUID of the function or component"));
        params.add(ToolParameter.requiredEnum("port_type",
                "Port type: function_input, function_output, component, physical",
                VALID_PORT_TYPES));
        params.add(ToolParameter.optionalString("name",
                "Name for the port (auto-generated if omitted)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String elementUuid = getRequiredString(parameters, "element_uuid");
        String portType = getRequiredString(parameters, "port_type").toLowerCase();
        String name = getOptionalString(parameters, "name", null);

        try {
            elementUuid = InputValidator.validateUuid(elementUuid);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Invalid UUID: " + e.getMessage());
        }

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            EObject element = resolveElementByUuid(elementUuid);
            if (element == null) {
                return ToolResult.error("Element not found: " + elementUuid);
            }

            // Validate element type vs port type
            if ((portType.startsWith("function_")) && !(element instanceof AbstractFunction)) {
                return ToolResult.error("Function ports require a function element, got: "
                        + element.eClass().getName());
            }
            if (portType.equals("component") && !(element instanceof Component)) {
                return ToolResult.error("Component ports require a component element, got: "
                        + element.eClass().getName());
            }
            if (portType.equals("physical") && !(element instanceof Component)) {
                return ToolResult.error("Physical ports require a component element, got: "
                        + element.eClass().getName());
            }

            String portName = (name != null && !name.isBlank())
                    ? InputValidator.sanitizeName(name)
                    : portType + "_port";

            final String finalName = portName;
            final EObject[] created = new EObject[1];

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Create port '" + portName + "'") {
                @Override
                protected void doExecute() {
                    switch (portType) {
                        case "function_input": {
                            FunctionInputPort port = FaFactory.eINSTANCE.createFunctionInputPort();
                            port.setName(finalName);
                            ((AbstractFunction) element).getInputs().add(port);
                            created[0] = port;
                            break;
                        }
                        case "function_output": {
                            FunctionOutputPort port = FaFactory.eINSTANCE.createFunctionOutputPort();
                            port.setName(finalName);
                            ((AbstractFunction) element).getOutputs().add(port);
                            created[0] = port;
                            break;
                        }
                        case "component": {
                            ComponentPort port = FaFactory.eINSTANCE.createComponentPort();
                            port.setName(finalName);
                            port.setOrientation(OrientationPortKind.INOUT);
                            ((Component) element).getOwnedFeatures().add(port);
                            created[0] = port;
                            break;
                        }
                        case "physical": {
                            PhysicalPort port = CsFactory.eINSTANCE.createPhysicalPort();
                            port.setName(finalName);
                            ((Component) element).getOwnedFeatures().add(port);
                            created[0] = port;
                            break;
                        }
                    }
                }
            });

            if (created[0] == null) {
                return ToolResult.error("Port creation failed");
            }

            modelService.invalidateCache(session);

            JsonObject response = new JsonObject();
            response.addProperty("status", "created");
            response.addProperty("port_name", getElementName(created[0]));
            response.addProperty("port_uuid", getElementId(created[0]));
            response.addProperty("port_type", portType);
            response.addProperty("element_name", getElementName(element));
            response.addProperty("element_uuid", elementUuid);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to create port: " + e.getMessage());
        }
    }
}

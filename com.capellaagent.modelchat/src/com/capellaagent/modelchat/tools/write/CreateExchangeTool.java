package com.capellaagent.modelchat.tools.write;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.RecordingCommand;

// PLACEHOLDER imports for Capella exchange metamodel
// import org.polarsys.capella.core.data.fa.FaFactory;
// import org.polarsys.capella.core.data.fa.FunctionalExchange;
// import org.polarsys.capella.core.data.fa.ComponentExchange;
// import org.polarsys.capella.core.data.fa.AbstractFunction;
// import org.polarsys.capella.core.data.fa.FunctionOutputPort;
// import org.polarsys.capella.core.data.fa.FunctionInputPort;
// import org.polarsys.capella.core.data.cs.Component;
// import org.polarsys.capella.core.data.information.PortAllocation;

/**
 * Creates an exchange (functional or component) between two model elements.
 * <p>
 * For functional exchanges, the source and target must be functions (or function ports).
 * For component exchanges, the source and target must be components (or component ports).
 * The exchange is created within an EMF {@link RecordingCommand} for transactional safety.
 *
 * <h3>Tool Specification</h3>
 * <ul>
 *   <li><b>Name:</b> create_exchange</li>
 *   <li><b>Category:</b> model_write</li>
 *   <li><b>Parameters:</b>
 *     <ul>
 *       <li>{@code layer} (string, required) - Architecture layer: oa, sa, la, pa</li>
 *       <li>{@code type} (string, required) - Exchange type: functional_exchange or component_exchange</li>
 *       <li>{@code source_uuid} (string, required) - UUID of the source element</li>
 *       <li>{@code target_uuid} (string, required) - UUID of the target element</li>
 *       <li>{@code name} (string, optional) - Name for the exchange</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class CreateExchangeTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "create_exchange";
    private static final String DESCRIPTION =
            "Creates a functional exchange or component exchange between two model elements. "
            + "Returns the created exchange details including its UUID.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");
    private static final List<String> VALID_TYPES = List.of("functional_exchange", "component_exchange");

    public CreateExchangeTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("layer",
                "Architecture layer: oa, sa, la, pa"));
        params.add(ToolParameter.requiredString("type",
                "Exchange type: functional_exchange or component_exchange"));
        params.add(ToolParameter.requiredString("source_uuid",
                "UUID of the source element (function or component)"));
        params.add(ToolParameter.requiredString("target_uuid",
                "UUID of the target element (function or component)"));
        params.add(ToolParameter.optionalString("name",
                "Name for the exchange (auto-generated if omitted)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        String type = getRequiredString(parameters, "type").toLowerCase();
        String sourceUuid = getRequiredString(parameters, "source_uuid");
        String targetUuid = getRequiredString(parameters, "target_uuid");
        String name = getOptionalString(parameters, "name", null);

        // Validate layer
        if (!VALID_LAYERS.contains(layer)) {
            return ToolResult.error("Invalid layer '" + layer
                    + "'. Must be one of: " + String.join(", ", VALID_LAYERS));
        }

        // Validate type
        if (!VALID_TYPES.contains(type)) {
            return ToolResult.error("Invalid type '" + type
                    + "'. Must be one of: " + String.join(", ", VALID_TYPES));
        }

        try {
            // Resolve source and target elements
            EObject source = resolveElementByUuid(sourceUuid);
            if (source == null) {
                return ToolResult.error("Source element not found with UUID: " + sourceUuid);
            }

            EObject target = resolveElementByUuid(targetUuid);
            if (target == null) {
                return ToolResult.error("Target element not found with UUID: " + targetUuid);
            }

            // Validate source/target types match exchange type
            // PLACEHOLDER: Type validation
            // if ("functional_exchange".equals(type)) {
            //     if (!(source instanceof AbstractFunction)) {
            //         return ToolResult.error("Source must be a function for functional_exchange");
            //     }
            //     if (!(target instanceof AbstractFunction)) {
            //         return ToolResult.error("Target must be a function for functional_exchange");
            //     }
            // } else {
            //     if (!(source instanceof Component)) {
            //         return ToolResult.error("Source must be a component for component_exchange");
            //     }
            //     if (!(target instanceof Component)) {
            //         return ToolResult.error("Target must be a component for component_exchange");
            //     }
            // }

            // Auto-generate name if not provided
            if (name == null || name.isBlank()) {
                name = "[" + getElementName(source) + "] to [" + getElementName(target) + "]";
            }

            final EObject srcElement = source;
            final EObject tgtElement = target;
            final String exchangeName = name;
            final String exchangeType = type;

            TransactionalEditingDomain domain = getEditingDomain();
            final EObject[] created = new EObject[1];

            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Create " + type + " '" + exchangeName + "'") {
                @Override
                protected void doExecute() {
                    // PLACEHOLDER: Create exchange using Capella factories
                    //
                    // if ("functional_exchange".equals(exchangeType)) {
                    //     AbstractFunction srcFn = (AbstractFunction) srcElement;
                    //     AbstractFunction tgtFn = (AbstractFunction) tgtElement;
                    //
                    //     // Create output port on source
                    //     FunctionOutputPort outPort = FaFactory.eINSTANCE.createFunctionOutputPort();
                    //     outPort.setName(exchangeName + "_out");
                    //     srcFn.getOutputs().add(outPort);
                    //
                    //     // Create input port on target
                    //     FunctionInputPort inPort = FaFactory.eINSTANCE.createFunctionInputPort();
                    //     inPort.setName(exchangeName + "_in");
                    //     tgtFn.getInputs().add(inPort);
                    //
                    //     // Create the functional exchange
                    //     FunctionalExchange fe = FaFactory.eINSTANCE.createFunctionalExchange();
                    //     fe.setName(exchangeName);
                    //     fe.setSource(outPort);
                    //     fe.setTarget(inPort);
                    //
                    //     // Add to the parent function pkg or source function's container
                    //     srcFn.getOwnedFunctionalExchanges().add(fe);
                    //     created[0] = fe;
                    //
                    // } else { // component_exchange
                    //     Component srcComp = (Component) srcElement;
                    //     Component tgtComp = (Component) tgtElement;
                    //
                    //     ComponentExchange ce = FaFactory.eINSTANCE.createComponentExchange();
                    //     ce.setName(exchangeName);
                    //     ce.setSource(srcComp); // or create ComponentPort
                    //     ce.setTarget(tgtComp);
                    //
                    //     // Add to the parent component pkg
                    //     srcComp.getOwnedComponentExchanges().add(ce);
                    //     created[0] = ce;
                    // }

                    created[0] = createExchange(srcElement, tgtElement, exchangeType, exchangeName);
                }
            });

            if (created[0] == null) {
                return ToolResult.error("Exchange creation failed - no exchange produced");
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "created");
            response.addProperty("name", getElementName(created[0]));
            response.addProperty("uuid", getElementId(created[0]));
            response.addProperty("type", created[0].eClass().getName());
            response.addProperty("exchange_type", type);
            response.addProperty("layer", layer);
            response.addProperty("source_name", getElementName(srcElement));
            response.addProperty("source_uuid", sourceUuid);
            response.addProperty("target_name", getElementName(tgtElement));
            response.addProperty("target_uuid", targetUuid);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to create exchange: " + e.getMessage());
        }
    }

    /**
     * Creates the exchange object between source and target elements.
     *
     * @param source       the source element
     * @param target       the target element
     * @param exchangeType the exchange type identifier
     * @param name         the exchange name
     * @return the created exchange EObject
     */
    private EObject createExchange(EObject source, EObject target, String exchangeType, String name) {
        // PLACEHOLDER: Implement using Capella factories (see doExecute comments above)
        throw new UnsupportedOperationException(
                "PLACEHOLDER: Create " + exchangeType + " between elements");
    }
}

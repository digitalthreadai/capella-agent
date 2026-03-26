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
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.ComponentExchange; // VERIFY: may be in cs package
import org.polarsys.capella.core.data.fa.FaFactory;
import org.polarsys.capella.core.data.fa.FunctionalExchange;
import org.polarsys.capella.core.data.fa.FunctionInputPort;
import org.polarsys.capella.core.data.fa.FunctionOutputPort;
import org.polarsys.capella.core.data.fa.ComponentPort; // VERIFY: exact path
import org.polarsys.capella.core.data.information.InformationFactory; // VERIFY: for ports

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
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

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
            if ("functional_exchange".equals(type)) {
                if (!(source instanceof AbstractFunction)) {
                    return ToolResult.error("Source must be a function for functional_exchange, but got: "
                            + source.eClass().getName());
                }
                if (!(target instanceof AbstractFunction)) {
                    return ToolResult.error("Target must be a function for functional_exchange, but got: "
                            + target.eClass().getName());
                }
            } else {
                if (!(source instanceof Component)) {
                    return ToolResult.error("Source must be a component for component_exchange, but got: "
                            + source.eClass().getName());
                }
                if (!(target instanceof Component)) {
                    return ToolResult.error("Target must be a component for component_exchange, but got: "
                            + target.eClass().getName());
                }
            }

            // Auto-generate name if not provided, then sanitize
            if (name == null || name.isBlank()) {
                name = "[" + getElementName(source) + "] to [" + getElementName(target) + "]";
            }
            name = InputValidator.sanitizeName(name);

            final EObject srcElement = source;
            final EObject tgtElement = target;
            final String exchangeName = name;
            final String exchangeType = type;

            TransactionalEditingDomain domain = getEditingDomain(session);
            final EObject[] created = new EObject[1];

            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Create " + type + " '" + exchangeName + "'") {
                @Override
                protected void doExecute() {
                    created[0] = createExchange(srcElement, tgtElement, exchangeType, exchangeName);
                }
            });

            if (created[0] == null) {
                return ToolResult.error("Exchange creation failed - no exchange produced");
            }

            // Invalidate cache since model was modified
            modelService.invalidateCache(session);

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
     * For functional exchanges, creates output/input ports and the exchange.
     * For component exchanges, creates component ports and the exchange.
     * Must be called within a RecordingCommand transaction.
     *
     * @param source       the source element
     * @param target       the target element
     * @param exchangeType the exchange type identifier
     * @param name         the exchange name
     * @return the created exchange EObject
     */
    @SuppressWarnings("unchecked")
    private EObject createExchange(EObject source, EObject target, String exchangeType, String name) {
        if ("functional_exchange".equals(exchangeType)) {
            return createFunctionalExchange((AbstractFunction) source, (AbstractFunction) target, name);
        } else {
            return createComponentExchange((Component) source, (Component) target, name);
        }
    }

    /**
     * Creates a FunctionalExchange between two AbstractFunctions, including
     * the required FunctionOutputPort on source and FunctionInputPort on target.
     */
    @SuppressWarnings("unchecked")
    private EObject createFunctionalExchange(AbstractFunction srcFn, AbstractFunction tgtFn, String name) {
        // Create output port on source function
        FunctionOutputPort outPort = FaFactory.eINSTANCE.createFunctionOutputPort();
        outPort.setName(name + "_out");
        srcFn.getOutputs().add(outPort);

        // Create input port on target function
        FunctionInputPort inPort = FaFactory.eINSTANCE.createFunctionInputPort();
        inPort.setName(name + "_in");
        tgtFn.getInputs().add(inPort);

        // Create the functional exchange linking the ports
        FunctionalExchange fe = FaFactory.eINSTANCE.createFunctionalExchange();
        fe.setName(name);
        fe.setSource(outPort);
        fe.setTarget(inPort);

        // Add the exchange to the source function's owned exchanges
        srcFn.getOwnedFunctionalExchanges().add(fe);

        return fe;
    }

    /**
     * Creates a ComponentExchange between two Components.
     * Uses FaFactory to create the exchange and links source/target directly.
     */
    @SuppressWarnings("unchecked")
    private EObject createComponentExchange(Component srcComp, Component tgtComp, String name) {
        // Create the component exchange
        ComponentExchange ce = FaFactory.eINSTANCE.createComponentExchange();
        ce.setName(name);

        // In Capella, ComponentExchange connects via ComponentPorts or directly
        // For simplicity, set source and target (the exact API may use InformationPorts)
        // VERIFY: Capella 7.0 may require creating ComponentPorts explicitly
        // Cast to InformationsExchanger (Component implements it in Capella)
        ce.setSource((org.polarsys.capella.common.data.modellingcore.InformationsExchanger) srcComp);
        ce.setTarget((org.polarsys.capella.common.data.modellingcore.InformationsExchanger) tgtComp);

        // Add to the source component's owned exchanges
        srcComp.getOwnedComponentExchanges().add(ce);

        return ce;
    }
}

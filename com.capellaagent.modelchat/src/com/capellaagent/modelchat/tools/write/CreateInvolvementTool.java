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
import org.polarsys.capella.core.data.ctx.CapabilityInvolvement;
import org.polarsys.capella.core.data.ctx.CtxFactory;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.interaction.AbstractCapability;
import org.polarsys.capella.core.data.interaction.AbstractFunctionAbstractCapabilityInvolvement;
import org.polarsys.capella.core.data.interaction.InteractionFactory;

/**
 * Creates an involvement link between a capability and a function.
 */
public class CreateInvolvementTool extends AbstractCapellaTool {

    public CreateInvolvementTool() {
        super("create_involvement",
                "Links a function to a capability via involvement.",
                ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("capability_uuid",
                "UUID of the capability"));
        params.add(ToolParameter.requiredString("function_uuid",
                "UUID of the function to involve"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String capabilityUuid = getRequiredString(parameters, "capability_uuid");
        String functionUuid = getRequiredString(parameters, "function_uuid");

        try {
            capabilityUuid = InputValidator.validateUuid(capabilityUuid);
            functionUuid = InputValidator.validateUuid(functionUuid);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Invalid UUID: " + e.getMessage());
        }

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            EObject capObj = resolveElementByUuid(capabilityUuid);
            if (capObj == null) {
                return ToolResult.error("Capability not found: " + capabilityUuid);
            }
            if (!(capObj instanceof AbstractCapability)) {
                return ToolResult.error("Element is not a capability: " + capObj.eClass().getName());
            }

            EObject funcObj = resolveElementByUuid(functionUuid);
            if (funcObj == null) {
                return ToolResult.error("Function not found: " + functionUuid);
            }
            if (!(funcObj instanceof AbstractFunction)) {
                return ToolResult.error("Element is not a function: " + funcObj.eClass().getName());
            }

            AbstractCapability capability = (AbstractCapability) capObj;
            AbstractFunction function = (AbstractFunction) funcObj;

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Involve '" + getElementName(function) + "' in '"
                            + getElementName(capability) + "'") {
                @Override
                protected void doExecute() {
                    AbstractFunctionAbstractCapabilityInvolvement involvement =
                            InteractionFactory.eINSTANCE
                                    .createAbstractFunctionAbstractCapabilityInvolvement();
                    involvement.setInvolved(function);
                    // Involver is set implicitly via containment
                    capability.getOwnedAbstractFunctionAbstractCapabilityInvolvements()
                            .add(involvement);
                }
            });

            modelService.invalidateCache(session);

            JsonObject response = new JsonObject();
            response.addProperty("status", "created");
            response.addProperty("capability_name", getElementName(capability));
            response.addProperty("capability_uuid", capabilityUuid);
            response.addProperty("function_name", getElementName(function));
            response.addProperty("function_uuid", functionUuid);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to create involvement: " + e.getMessage());
        }
    }
}

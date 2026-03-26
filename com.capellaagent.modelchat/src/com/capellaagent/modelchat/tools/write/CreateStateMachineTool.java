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
import org.polarsys.capella.core.data.capellacommon.CapellacommonFactory;
import org.polarsys.capella.core.data.capellacommon.Region;
import org.polarsys.capella.core.data.capellacommon.StateMachine;
import org.polarsys.capella.core.data.cs.Component;

/**
 * Creates a state machine with an initial region on a component.
 */
public class CreateStateMachineTool extends AbstractCapellaTool {

    public CreateStateMachineTool() {
        super("create_state_machine",
                "Creates a state machine with initial region on a component.",
                ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("element_uuid",
                "UUID of the component to add state machine to"));
        params.add(ToolParameter.requiredString("name",
                "Name of the state machine"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String elementUuid = getRequiredString(parameters, "element_uuid");
        String rawName = getRequiredString(parameters, "name");

        String name;
        try {
            elementUuid = InputValidator.validateUuid(elementUuid);
            name = InputValidator.sanitizeName(rawName);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Validation failed: " + e.getMessage());
        }

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            EObject element = resolveElementByUuid(elementUuid);
            if (element == null) {
                return ToolResult.error("Element not found: " + elementUuid);
            }
            if (!(element instanceof Component)) {
                return ToolResult.error("Element is not a component: " + element.eClass().getName());
            }

            Component component = (Component) element;
            final String smName = name;
            final EObject[] created = new EObject[1];

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Create state machine '" + name + "'") {
                @Override
                protected void doExecute() {
                    StateMachine sm = CapellacommonFactory.eINSTANCE.createStateMachine();
                    sm.setName(smName);

                    Region region = CapellacommonFactory.eINSTANCE.createRegion();
                    region.setName("Default Region");
                    sm.getOwnedRegions().add(region);

                    component.getOwnedStateMachines().add(sm);
                    created[0] = sm;
                }
            });

            if (created[0] == null) {
                return ToolResult.error("State machine creation failed");
            }

            modelService.invalidateCache(session);

            JsonObject response = new JsonObject();
            response.addProperty("status", "created");
            response.addProperty("name", getElementName(created[0]));
            response.addProperty("uuid", getElementId(created[0]));
            response.addProperty("type", "StateMachine");
            response.addProperty("component_name", getElementName(component));
            response.addProperty("component_uuid", elementUuid);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to create state machine: " + e.getMessage());
        }
    }
}

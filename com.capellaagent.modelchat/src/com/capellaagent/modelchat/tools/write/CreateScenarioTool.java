package com.capellaagent.modelchat.tools.write;

import java.util.ArrayList;
import java.util.Iterator;
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
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.interaction.AbstractCapability;
import org.polarsys.capella.core.data.interaction.InteractionFactory;
import org.polarsys.capella.core.data.interaction.Scenario;
import org.polarsys.capella.core.data.interaction.ScenarioKind;

/**
 * Creates a new Scenario in the specified layer, optionally under a capability.
 */
public class CreateScenarioTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public CreateScenarioTool() {
        super("create_scenario",
                "Creates a scenario in a layer, optionally under a capability.",
                ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS));
        params.add(ToolParameter.requiredString("name",
                "Name of the scenario"));
        params.add(ToolParameter.optionalString("capability_uuid",
                "UUID of capability to add the scenario to"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        String rawName = getRequiredString(parameters, "name");
        String capabilityUuid = getOptionalString(parameters, "capability_uuid", null);

        String name;
        try {
            name = InputValidator.sanitizeName(rawName);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Invalid name: " + e.getMessage());
        }

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();
            BlockArchitecture arch = modelService.getArchitecture(session, layer);

            // Find the capability to contain the scenario
            EObject container = null;
            if (capabilityUuid != null && !capabilityUuid.isBlank()) {
                container = resolveElementByUuid(capabilityUuid);
                if (container == null) {
                    return ToolResult.error("Capability not found: " + capabilityUuid);
                }
                if (!(container instanceof AbstractCapability)) {
                    return ToolResult.error("Element is not a capability: " + container.eClass().getName());
                }
            } else {
                // Find first capability in the layer
                Iterator<EObject> it = arch.eAllContents();
                while (it.hasNext()) {
                    EObject obj = it.next();
                    if (obj instanceof AbstractCapability) {
                        container = obj;
                        break;
                    }
                }
                if (container == null) {
                    return ToolResult.error("No capability found in layer " + layer
                            + ". Create a capability first.");
                }
            }

            final EObject finalContainer = container;
            final String scenarioName = name;
            final EObject[] created = new EObject[1];

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Create scenario '" + name + "'") {
                @Override
                protected void doExecute() {
                    Scenario scenario = InteractionFactory.eINSTANCE.createScenario();
                    scenario.setName(scenarioName);
                    scenario.setKind(ScenarioKind.FUNCTIONAL);
                    ((AbstractCapability) finalContainer).getOwnedScenarios().add(scenario);
                    created[0] = scenario;
                }
            });

            if (created[0] == null) {
                return ToolResult.error("Scenario creation failed");
            }

            modelService.invalidateCache(session);

            JsonObject response = new JsonObject();
            response.addProperty("status", "created");
            response.addProperty("name", getElementName(created[0]));
            response.addProperty("uuid", getElementId(created[0]));
            response.addProperty("type", created[0].eClass().getName());
            response.addProperty("layer", layer);
            response.addProperty("capability", getElementName(finalContainer));
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to create scenario: " + e.getMessage());
        }
    }
}

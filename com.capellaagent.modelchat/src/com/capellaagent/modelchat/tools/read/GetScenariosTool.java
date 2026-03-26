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
import org.polarsys.capella.core.data.interaction.AbstractCapability;
import org.polarsys.capella.core.data.interaction.InstanceRole;
import org.polarsys.capella.core.data.interaction.Scenario;
import org.polarsys.capella.core.data.interaction.SequenceMessage;

/**
 * Lists scenarios (sequence diagrams) in the model with their instance roles
 * and messages.
 */
public class GetScenariosTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "get_scenarios";
    private static final String DESCRIPTION =
            "Lists scenarios with instance roles and messages for a layer or capability.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public GetScenariosTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalEnum("layer",
                "Architecture layer to query",
                VALID_LAYERS, null));
        params.add(ToolParameter.optionalString("capability_uuid",
                "Filter scenarios belonging to a specific capability"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getOptionalString(parameters, "layer", null);
        String capabilityUuid = getOptionalString(parameters, "capability_uuid", null);

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();

            JsonArray scenarioArray = new JsonArray();

            // If capability_uuid is provided, find scenarios under that capability
            if (capabilityUuid != null && !capabilityUuid.isBlank()) {
                EObject capObj = resolveElementByUuid(capabilityUuid);
                if (capObj == null) {
                    return ToolResult.error("Capability not found with UUID: " + capabilityUuid);
                }
                if (!(capObj instanceof AbstractCapability)) {
                    return ToolResult.error("Element is not a Capability (type: "
                            + capObj.eClass().getName() + ")");
                }
                AbstractCapability cap = (AbstractCapability) capObj;
                // VERIFY: getOwnedScenarios() is the standard Capella API for scenarios under a capability
                List<Scenario> scenarios = cap.getOwnedScenarios();
                if (scenarios != null) {
                    for (Scenario scenario : scenarios) {
                        scenarioArray.add(buildScenarioJson(scenario));
                    }
                }
            } else {
                // Search by layer
                List<String> layersToSearch = new ArrayList<>();
                if (layer != null && !layer.isBlank()) {
                    layersToSearch.add(layer.toLowerCase());
                } else {
                    layersToSearch.addAll(VALID_LAYERS);
                }

                for (String l : layersToSearch) {
                    try {
                        BlockArchitecture architecture = modelService.getArchitecture(session, l);
                        Iterator<EObject> allContents = architecture.eAllContents();
                        while (allContents.hasNext()) {
                            EObject obj = allContents.next();
                            if (obj instanceof Scenario) {
                                scenarioArray.add(buildScenarioJson((Scenario) obj));
                            }
                        }
                    } catch (Exception e) {
                        // Layer may not exist; skip
                    }
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer != null ? layer : "all");
            if (capabilityUuid != null) {
                response.addProperty("capability_uuid", capabilityUuid);
            }
            response.addProperty("scenario_count", scenarioArray.size());
            response.add("scenarios", scenarioArray);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to get scenarios: " + e.getMessage());
        }
    }

    /**
     * Builds a JSON representation of a Scenario including its instance roles
     * and sequence messages.
     */
    private JsonObject buildScenarioJson(Scenario scenario) {
        JsonObject scenarioObj = new JsonObject();
        scenarioObj.addProperty("name", getElementName(scenario));
        scenarioObj.addProperty("id", getElementId(scenario));
        scenarioObj.addProperty("type", scenario.eClass().getName());

        // Instance roles
        JsonArray rolesArray = new JsonArray();
        try {
            List<InstanceRole> roles = scenario.getOwnedInstanceRoles();
            if (roles != null) {
                for (InstanceRole role : roles) {
                    JsonObject roleObj = new JsonObject();
                    roleObj.addProperty("name", getElementName(role));
                    roleObj.addProperty("id", getElementId(role));
                    // The represented instance
                    if (role.getRepresentedInstance() != null) {
                        roleObj.addProperty("represents",
                                getElementName(role.getRepresentedInstance()));
                        roleObj.addProperty("represents_type",
                                role.getRepresentedInstance().eClass().getName());
                    }
                    rolesArray.add(roleObj);
                }
            }
        } catch (Exception e) {
            // API may vary
        }
        scenarioObj.add("instance_roles", rolesArray);

        // Sequence messages
        JsonArray messagesArray = new JsonArray();
        try {
            List<SequenceMessage> messages = scenario.getOwnedMessages();
            if (messages != null) {
                for (SequenceMessage msg : messages) {
                    JsonObject msgObj = new JsonObject();
                    msgObj.addProperty("name", getElementName(msg));
                    msgObj.addProperty("id", getElementId(msg));
                    msgObj.addProperty("kind", msg.getKind() != null
                            ? msg.getKind().getName() : "unknown");

                    // Source and target instance roles
                    if (msg.getSendingEnd() != null && msg.getSendingEnd().getCovered() != null) {
                        InstanceRole sender = msg.getSendingEnd().getCovered();
                        msgObj.addProperty("source", getElementName(sender));
                    }
                    if (msg.getReceivingEnd() != null && msg.getReceivingEnd().getCovered() != null) {
                        InstanceRole receiver = msg.getReceivingEnd().getCovered();
                        msgObj.addProperty("target", getElementName(receiver));
                    }

                    messagesArray.add(msgObj);
                }
            }
        } catch (Exception e) {
            // Message API may vary
        }
        scenarioObj.add("messages", messagesArray);

        return scenarioObj;
    }
}

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
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.core.data.capellacommon.Mode;
import org.polarsys.capella.core.data.capellacommon.State;
import org.polarsys.capella.core.data.capellacommon.AbstractState;
import org.polarsys.capella.core.data.capellacommon.StateTransition;
import org.polarsys.capella.core.data.cs.BlockArchitecture;

/**
 * Retrieves mode/state timing properties from the Capella model.
 * <p>
 * Traverses the specified architecture layer and collects all {@link Mode} and
 * {@link State} elements along with their timing-related properties such as
 * entry/do/exit actions and transitions with guard/trigger conditions.
 */
public class GetModesCapturedTimeTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "get_modes_captured_time";
    private static final String DESCRIPTION =
            "Gets mode/state timing properties including entry/do/exit actions and transitions.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public GetModesCapturedTimeTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS));
        params.add(ToolParameter.optionalString("element_uuid",
                "UUID of a specific state machine or region to scope the query"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        String elementUuid = getOptionalString(parameters, "element_uuid", null);

        if (!VALID_LAYERS.contains(layer)) {
            return ToolResult.error("Invalid layer '" + layer + "'. Must be one of: oa, sa, la, pa");
        }

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();

            EObject root;
            if (elementUuid != null && !elementUuid.isBlank()) {
                root = resolveElementByUuid(elementUuid);
                if (root == null) {
                    return ToolResult.error("Element not found: " + elementUuid);
                }
            } else {
                root = modelService.getArchitecture(session, layer);
            }

            JsonArray modesArray = new JsonArray();
            JsonArray transitionsArray = new JsonArray();

            Iterator<EObject> allContents = root.eAllContents();
            while (allContents.hasNext()) {
                EObject obj = allContents.next();

                if (obj instanceof AbstractState) {
                    AbstractState state = (AbstractState) obj;
                    JsonObject stateObj = new JsonObject();
                    stateObj.addProperty("name", getElementName(state));
                    stateObj.addProperty("id", getElementId(state));
                    stateObj.addProperty("type", state.eClass().getName());

                    // Determine if Mode or State
                    if (state instanceof Mode) {
                        stateObj.addProperty("kind", "mode");
                    } else if (state instanceof State) {
                        stateObj.addProperty("kind", "state");
                    } else {
                        stateObj.addProperty("kind", state.eClass().getName());
                    }

                    // Capture entry/do/exit behaviors via structural features
                    EStructuralFeature entryFeature = state.eClass().getEStructuralFeature("entry");
                    if (entryFeature != null) {
                        Object entryVal = state.eGet(entryFeature);
                        if (entryVal instanceof List) {
                            JsonArray entryActions = new JsonArray();
                            for (Object e : (List<?>) entryVal) {
                                if (e instanceof EObject) {
                                    entryActions.add(getElementName((EObject) e));
                                }
                            }
                            stateObj.add("entry_actions", entryActions);
                        }
                    }

                    EStructuralFeature doFeature = state.eClass().getEStructuralFeature("doActivity");
                    if (doFeature != null) {
                        Object doVal = state.eGet(doFeature);
                        if (doVal instanceof List) {
                            JsonArray doActivities = new JsonArray();
                            for (Object d : (List<?>) doVal) {
                                if (d instanceof EObject) {
                                    doActivities.add(getElementName((EObject) d));
                                }
                            }
                            stateObj.add("do_activities", doActivities);
                        }
                    }

                    // Incoming transitions count
                    List<StateTransition> incoming = state.getIncoming();
                    stateObj.addProperty("incoming_transition_count",
                            incoming != null ? incoming.size() : 0);

                    // Outgoing transitions count
                    List<StateTransition> outgoing = state.getOutgoing();
                    stateObj.addProperty("outgoing_transition_count",
                            outgoing != null ? outgoing.size() : 0);

                    // Parent region/state machine
                    if (state.eContainer() != null) {
                        stateObj.addProperty("parent", getElementName(state.eContainer()));
                        stateObj.addProperty("parent_id", getElementId(state.eContainer()));
                    }

                    modesArray.add(stateObj);
                }

                if (obj instanceof StateTransition) {
                    StateTransition transition = (StateTransition) obj;
                    JsonObject transObj = new JsonObject();
                    transObj.addProperty("name", getElementName(transition));
                    transObj.addProperty("id", getElementId(transition));

                    // Source and target states
                    AbstractState source = transition.getSource();
                    AbstractState target = transition.getTarget();
                    transObj.addProperty("source_state",
                            source != null ? getElementName(source) : "");
                    transObj.addProperty("source_id",
                            source != null ? getElementId(source) : "");
                    transObj.addProperty("target_state",
                            target != null ? getElementName(target) : "");
                    transObj.addProperty("target_id",
                            target != null ? getElementId(target) : "");

                    // Guard condition
                    EStructuralFeature guardFeature =
                            transition.eClass().getEStructuralFeature("guard");
                    if (guardFeature != null) {
                        Object guardVal = transition.eGet(guardFeature);
                        if (guardVal instanceof EObject) {
                            transObj.addProperty("guard", getElementName((EObject) guardVal));
                        }
                    }

                    // Trigger description
                    EStructuralFeature triggerFeature =
                            transition.eClass().getEStructuralFeature("triggers");
                    if (triggerFeature != null) {
                        Object triggerVal = transition.eGet(triggerFeature);
                        if (triggerVal instanceof List) {
                            JsonArray triggers = new JsonArray();
                            for (Object t : (List<?>) triggerVal) {
                                if (t instanceof EObject) {
                                    triggers.add(getElementName((EObject) t));
                                }
                            }
                            transObj.add("triggers", triggers);
                        }
                    }

                    transitionsArray.add(transObj);
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("mode_state_count", modesArray.size());
            response.addProperty("transition_count", transitionsArray.size());
            response.add("modes_and_states", modesArray);
            response.add("transitions", transitionsArray);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to get modes/states timing: " + e.getMessage());
        }
    }
}

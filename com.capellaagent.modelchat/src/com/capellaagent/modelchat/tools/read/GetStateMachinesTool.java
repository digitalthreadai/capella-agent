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
import org.polarsys.capella.core.data.capellacommon.Region;
import org.polarsys.capella.core.data.capellacommon.State;
import org.polarsys.capella.core.data.capellacommon.StateTransition;
import org.polarsys.capella.core.data.capellacommon.StateMachine;
import org.polarsys.capella.core.data.cs.BlockArchitecture;

/**
 * Lists state machines and their states/transitions in a layer or element.
 */
public class GetStateMachinesTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public GetStateMachinesTool() {
        super("get_state_machines",
                "Lists state machines with states and transitions.",
                ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalString("element_uuid",
                "UUID of element to get state machines for"));
        params.add(ToolParameter.optionalEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS, null));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String elementUuid = getOptionalString(parameters, "element_uuid", null);
        String layer = getOptionalString(parameters, "layer", null);

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            List<StateMachine> machines = new ArrayList<>();

            if (elementUuid != null && !elementUuid.isBlank()) {
                EObject element = resolveElementByUuid(elementUuid);
                if (element == null) {
                    return ToolResult.error("Element not found: " + elementUuid);
                }
                collectStateMachines(element, machines);
            } else if (layer != null && !layer.isBlank()) {
                BlockArchitecture arch = modelService.getArchitecture(session, layer.toLowerCase());
                collectStateMachines(arch, machines);
            } else {
                // Search all layers
                for (String l : VALID_LAYERS) {
                    try {
                        BlockArchitecture arch = modelService.getArchitecture(session, l);
                        collectStateMachines(arch, machines);
                    } catch (Exception e) { /* layer may not exist */ }
                }
            }

            JsonArray results = new JsonArray();
            for (StateMachine sm : machines) {
                JsonObject smObj = new JsonObject();
                smObj.addProperty("name", getElementName(sm));
                smObj.addProperty("id", getElementId(sm));

                JsonArray statesArray = new JsonArray();
                JsonArray transitionsArray = new JsonArray();

                for (Region region : sm.getOwnedRegions()) {
                    for (EObject child : region.getOwnedStates()) {
                        if (child instanceof State) {
                            State state = (State) child;
                            JsonObject stateObj = new JsonObject();
                            stateObj.addProperty("name", getElementName(state));
                            stateObj.addProperty("id", getElementId(state));
                            stateObj.addProperty("kind", state.eClass().getName());
                            statesArray.add(stateObj);
                        }
                    }
                    for (StateTransition transition : region.getOwnedTransitions()) {
                        JsonObject transObj = new JsonObject();
                        transObj.addProperty("name", getElementName(transition));
                        transObj.addProperty("source",
                                transition.getSource() != null ? getElementName(transition.getSource()) : "");
                        transObj.addProperty("target",
                                transition.getTarget() != null ? getElementName(transition.getTarget()) : "");
                        transObj.addProperty("trigger", "");
                        transitionsArray.add(transObj);
                    }
                }

                smObj.add("states", statesArray);
                smObj.add("transitions", transitionsArray);
                results.add(smObj);
            }

            JsonObject response = new JsonObject();
            response.addProperty("count", results.size());
            response.add("state_machines", results);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to get state machines: " + e.getMessage());
        }
    }

    private void collectStateMachines(EObject root, List<StateMachine> machines) {
        Iterator<EObject> it = root.eAllContents();
        while (it.hasNext()) {
            EObject obj = it.next();
            if (obj instanceof StateMachine) {
                machines.add((StateMachine) obj);
            }
        }
    }
}

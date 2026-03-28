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
import org.polarsys.capella.core.data.capellacommon.CapellacommonFactory;
import org.polarsys.capella.core.data.capellacommon.Mode;
import org.polarsys.capella.core.data.capellacommon.Region;
import org.polarsys.capella.core.data.capellacommon.State;
import org.polarsys.capella.core.data.capellacommon.StateMachine;

/**
 * Creates a Mode or State in a state machine region.
 * <p>
 * The parent must be a {@link Region} (inside a {@link StateMachine}).
 * Supports creating both Modes and States, which are the two primary
 * behavioral state types in ARCADIA/Capella.
 */
public class CreateModeTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "create_mode";
    private static final String DESCRIPTION =
            "Creates a Mode or State in a state machine region.";

    private static final List<String> VALID_KINDS = List.of("mode", "state");

    public CreateModeTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("region_uuid",
                "UUID of the Region to create the mode/state in"));
        params.add(ToolParameter.requiredString("name",
                "Name of the new mode or state"));
        params.add(ToolParameter.optionalEnum("kind",
                "Kind of state element: mode or state (default: mode)",
                VALID_KINDS, "mode"));
        params.add(ToolParameter.optionalString("description",
                "Description text for the mode/state"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String regionUuid = getRequiredString(parameters, "region_uuid");
        String rawName = getRequiredString(parameters, "name");
        String kind = getOptionalString(parameters, "kind", "mode").toLowerCase();
        String rawDescription = getOptionalString(parameters, "description", null);

        // Sanitize inputs
        String name;
        String description;
        try {
            name = InputValidator.sanitizeName(rawName);
            description = rawDescription != null ? InputValidator.sanitizeDescription(rawDescription) : null;
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Input validation failed: " + e.getMessage());
        }

        if (!VALID_KINDS.contains(kind)) {
            return ToolResult.error("Invalid kind '" + kind + "'. Must be: mode or state");
        }

        try {
            Session session = getActiveSession();

            EObject regionObj = resolveElementByUuid(regionUuid);
            if (regionObj == null) {
                return ToolResult.error("Region not found: " + regionUuid);
            }

            if (!(regionObj instanceof Region)) {
                return ToolResult.error("Element is not a Region (type: "
                        + regionObj.eClass().getName()
                        + "). Modes and states must be created inside a Region.");
            }

            Region region = (Region) regionObj;
            final String elementName = name;
            final String elementDesc = description;
            final EObject[] created = new EObject[1];

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Create " + kind + " '" + name + "'") {
                @Override
                protected void doExecute() {
                    if ("mode".equals(kind)) {
                        Mode mode = CapellacommonFactory.eINSTANCE.createMode();
                        mode.setName(elementName);
                        if (elementDesc != null) {
                            mode.setDescription(elementDesc);
                        }
                        region.getOwnedStates().add(mode);
                        created[0] = mode;
                    } else {
                        State state = CapellacommonFactory.eINSTANCE.createState();
                        state.setName(elementName);
                        if (elementDesc != null) {
                            state.setDescription(elementDesc);
                        }
                        region.getOwnedStates().add(state);
                        created[0] = state;
                    }
                }
            });

            if (created[0] == null) {
                return ToolResult.error("Failed to create " + kind);
            }

            getModelService().invalidateCache(session);

            JsonObject response = new JsonObject();
            response.addProperty("status", "created");
            response.addProperty("name", getElementName(created[0]));
            response.addProperty("uuid", getElementId(created[0]));
            response.addProperty("type", created[0].eClass().getName());
            response.addProperty("kind", kind);
            response.addProperty("region_name", getElementName(region));
            response.addProperty("region_uuid", getElementId(region));

            // Include state machine name
            EObject smContainer = region.eContainer();
            if (smContainer instanceof StateMachine) {
                response.addProperty("state_machine", getElementName(smContainer));
            }

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to create " + kind + ": " + e.getMessage());
        }
    }
}

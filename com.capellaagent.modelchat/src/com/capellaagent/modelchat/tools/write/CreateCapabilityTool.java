package com.capellaagent.modelchat.tools.write;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.RecordingCommand;

// PLACEHOLDER imports for Capella capability metamodel
// import org.polarsys.capella.core.data.oa.OaFactory;
// import org.polarsys.capella.core.data.oa.OperationalCapability;
// import org.polarsys.capella.core.data.ctx.CtxFactory;
// import org.polarsys.capella.core.data.ctx.Capability;
// import org.polarsys.capella.core.data.la.LaFactory;
// import org.polarsys.capella.core.data.la.CapabilityRealization;
// import org.polarsys.capella.core.data.interaction.AbstractCapability;
// import org.polarsys.capella.core.data.interaction.AbstractFunctionAbstractCapabilityInvolvement;

/**
 * Creates a capability in the specified ARCADIA layer, optionally linking functions to it.
 * <p>
 * Capabilities represent the system's ability to perform specific behavior. They can
 * involve (link to) functions that contribute to the capability. The correct metamodel
 * class is used depending on the layer (OperationalCapability, Capability, CapabilityRealization).
 *
 * <h3>Tool Specification</h3>
 * <ul>
 *   <li><b>Name:</b> create_capability</li>
 *   <li><b>Category:</b> model_write</li>
 *   <li><b>Parameters:</b>
 *     <ul>
 *       <li>{@code layer} (string, required) - Architecture layer: oa, sa, la, pa</li>
 *       <li>{@code name} (string, required) - Capability name</li>
 *       <li>{@code involved_function_uuids} (array of strings, optional) - UUIDs of functions to involve</li>
 *       <li>{@code description} (string, optional) - Capability description</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class CreateCapabilityTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "create_capability";
    private static final String DESCRIPTION =
            "Creates a capability in the specified ARCADIA layer and optionally links "
            + "involved functions. Returns the created capability details.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public CreateCapabilityTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("layer",
                "Architecture layer: oa, sa, la, pa"));
        params.add(ToolParameter.requiredString("name",
                "Name of the capability"));
        params.add(ToolParameter.optionalStringArray("involved_function_uuids",
                "Array of UUIDs for functions to involve in this capability"));
        params.add(ToolParameter.optionalString("description",
                "Description of the capability"));
        return params;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        String name = getRequiredString(parameters, "name");
        List<String> involvedFunctionUuids = (List<String>) parameters.getOrDefault(
                "involved_function_uuids", new ArrayList<>());
        String description = getOptionalString(parameters, "description", null);

        if (!VALID_LAYERS.contains(layer)) {
            return ToolResult.error("Invalid layer '" + layer
                    + "'. Must be one of: " + String.join(", ", VALID_LAYERS));
        }

        if (name.isBlank()) {
            return ToolResult.error("Parameter 'name' must not be empty");
        }

        try {
            // Resolve involved functions
            List<EObject> involvedFunctions = new ArrayList<>();
            for (String fnUuid : involvedFunctionUuids) {
                EObject fn = resolveElementByUuid(fnUuid);
                if (fn == null) {
                    return ToolResult.error("Function not found with UUID: " + fnUuid);
                }
                involvedFunctions.add(fn);
            }

            // PLACEHOLDER: Get the capability package for the layer
            // Session session = getActiveSession();
            // BlockArchitecture arch = getArchitecture(session, layer);
            // AbstractCapabilityPkg capPkg = BlockArchitectureExt.getAbstractCapabilityPkg(arch);

            final String capName = name;
            final String capDescription = description;
            final String capLayer = layer;
            final List<EObject> functions = involvedFunctions;

            TransactionalEditingDomain domain = getEditingDomain();
            final EObject[] created = new EObject[1];
            final List<String> linkedFunctionNames = new ArrayList<>();

            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Create capability '" + name + "'") {
                @Override
                protected void doExecute() {
                    // PLACEHOLDER: Create capability using layer-specific factory
                    //
                    // EObject capability = switch (capLayer) {
                    //     case "oa" -> {
                    //         OperationalCapability oc = OaFactory.eINSTANCE.createOperationalCapability();
                    //         oc.setName(capName);
                    //         if (capDescription != null) oc.setDescription(capDescription);
                    //         // Add to OA capability pkg
                    //         yield oc;
                    //     }
                    //     case "sa" -> {
                    //         Capability cap = CtxFactory.eINSTANCE.createCapability();
                    //         cap.setName(capName);
                    //         if (capDescription != null) cap.setDescription(capDescription);
                    //         // Add to SA capability pkg
                    //         yield cap;
                    //     }
                    //     case "la", "pa" -> {
                    //         CapabilityRealization cr = LaFactory.eINSTANCE.createCapabilityRealization();
                    //         cr.setName(capName);
                    //         if (capDescription != null) cr.setDescription(capDescription);
                    //         // Add to LA/PA capability realization pkg
                    //         yield cr;
                    //     }
                    //     default -> throw new IllegalStateException("Unsupported layer: " + capLayer);
                    // };
                    //
                    // // Link involved functions
                    // AbstractCapability absCap = (AbstractCapability) capability;
                    // for (EObject fn : functions) {
                    //     AbstractFunctionAbstractCapabilityInvolvement involvement =
                    //         InteractionFactory.eINSTANCE.createAbstractFunctionAbstractCapabilityInvolvement();
                    //     involvement.setInvolver(absCap);
                    //     involvement.setInvolved((AbstractFunction) fn);
                    //     absCap.getOwnedAbstractFunctionAbstractCapabilityInvolvements().add(involvement);
                    //     linkedFunctionNames.add(getElementName(fn));
                    // }
                    //
                    // created[0] = capability;

                    created[0] = createCapability(capLayer, capName, capDescription, functions,
                            linkedFunctionNames);
                }
            });

            if (created[0] == null) {
                return ToolResult.error("Capability creation failed - no capability produced");
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "created");
            response.addProperty("name", getElementName(created[0]));
            response.addProperty("uuid", getElementId(created[0]));
            response.addProperty("type", created[0].eClass().getName());
            response.addProperty("layer", layer);

            if (!linkedFunctionNames.isEmpty()) {
                JsonArray linkedArray = new JsonArray();
                for (int i = 0; i < involvedFunctionUuids.size(); i++) {
                    JsonObject fnObj = new JsonObject();
                    fnObj.addProperty("uuid", involvedFunctionUuids.get(i));
                    if (i < linkedFunctionNames.size()) {
                        fnObj.addProperty("name", linkedFunctionNames.get(i));
                    }
                    linkedArray.add(fnObj);
                }
                response.add("involved_functions", linkedArray);
            }

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to create capability: " + e.getMessage());
        }
    }

    /**
     * Creates a capability in the specified layer and links functions.
     *
     * @param layer               the ARCADIA layer
     * @param name                the capability name
     * @param description         optional description
     * @param functions           list of function EObjects to involve
     * @param linkedFunctionNames output list to populate with linked function names
     * @return the created capability EObject
     */
    private EObject createCapability(String layer, String name, String description,
                                      List<EObject> functions, List<String> linkedFunctionNames) {
        // PLACEHOLDER: Implement using Capella factories (see doExecute comments above)
        throw new UnsupportedOperationException(
                "PLACEHOLDER: Create capability in " + layer + " layer");
    }
}

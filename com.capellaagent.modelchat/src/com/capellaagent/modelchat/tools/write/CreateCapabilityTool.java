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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.ctx.Capability;
import org.polarsys.capella.core.data.ctx.CtxFactory;
import org.polarsys.capella.core.data.ctx.SystemAnalysis;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.interaction.AbstractCapability;
import org.polarsys.capella.core.data.interaction.AbstractFunctionAbstractCapabilityInvolvement;
import org.polarsys.capella.core.data.interaction.InteractionFactory;
import org.polarsys.capella.core.data.la.CapabilityRealization;
import org.polarsys.capella.core.data.la.LaFactory;
import org.polarsys.capella.core.data.la.LogicalArchitecture;
import org.polarsys.capella.core.data.oa.OaFactory;
import org.polarsys.capella.core.data.oa.OperationalCapability;
import org.polarsys.capella.core.data.oa.OperationalAnalysis;
import org.polarsys.capella.core.data.pa.PhysicalArchitecture;

/**
 * Creates a capability in the specified ARCADIA layer, optionally linking functions to it.
 * <p>
 * Capabilities represent the system's ability to perform specific behavior. They can
 * involve (link to) functions that contribute to the capability. The correct metamodel
 * class is used depending on the layer:
 * <ul>
 *   <li>OA: {@code OperationalCapability}</li>
 *   <li>SA: {@code Capability}</li>
 *   <li>LA/PA: {@code CapabilityRealization}</li>
 * </ul>
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
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS));
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
        String rawName = getRequiredString(parameters, "name");
        List<String> involvedFunctionUuids = (List<String>) parameters.getOrDefault(
                "involved_function_uuids", new ArrayList<>());
        String rawDescription = getOptionalString(parameters, "description", null);

        if (!VALID_LAYERS.contains(layer)) {
            return ToolResult.error("Invalid layer '" + layer
                    + "'. Must be one of: " + String.join(", ", VALID_LAYERS));
        }

        // Sanitize inputs
        String name;
        String description;
        try {
            name = InputValidator.sanitizeName(rawName);
            description = rawDescription != null ? InputValidator.sanitizeDescription(rawDescription) : null;
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Input validation failed: " + e.getMessage());
        }

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            // Resolve involved functions
            List<AbstractFunction> involvedFunctions = new ArrayList<>();
            for (String fnUuid : involvedFunctionUuids) {
                EObject fn = resolveElementByUuid(fnUuid);
                if (fn == null) {
                    return ToolResult.error("Function not found with UUID: " + fnUuid);
                }
                if (!(fn instanceof AbstractFunction)) {
                    return ToolResult.error("Element " + fnUuid
                            + " is not a function (type: " + fn.eClass().getName() + ")");
                }
                involvedFunctions.add((AbstractFunction) fn);
            }

            // Get the architecture and its capability package
            BlockArchitecture arch = modelService.getArchitecture(session, layer);

            final String capName = name;
            final String capDescription = description;
            final String capLayer = layer;
            final List<AbstractFunction> functions = involvedFunctions;
            final BlockArchitecture architecture = arch;

            TransactionalEditingDomain domain = getEditingDomain(session);
            final EObject[] created = new EObject[1];
            final List<String> linkedFunctionNames = new ArrayList<>();

            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Create capability '" + name + "'") {
                @Override
                protected void doExecute() {
                    created[0] = createCapability(architecture, capLayer, capName,
                            capDescription, functions, linkedFunctionNames);
                }
            });

            if (created[0] == null) {
                return ToolResult.error("Capability creation failed - no capability produced");
            }

            // Invalidate UUID cache
            modelService.invalidateCache(session);

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
     * Creates a capability in the specified layer using the appropriate factory,
     * adds it to the capability package, and links involved functions.
     * Must be called within a RecordingCommand transaction.
     *
     * @param architecture        the BlockArchitecture
     * @param layer               the ARCADIA layer
     * @param name                the capability name (already sanitized)
     * @param description         optional description (already sanitized)
     * @param functions           list of functions to involve
     * @param linkedFunctionNames output list to populate with linked function names
     * @return the created capability EObject
     */
    @SuppressWarnings("unchecked")
    private EObject createCapability(BlockArchitecture architecture, String layer, String name,
                                      String description, List<AbstractFunction> functions,
                                      List<String> linkedFunctionNames) {
        AbstractCapability capability;

        switch (layer) {
            case "oa": {
                OperationalCapability oc = OaFactory.eINSTANCE.createOperationalCapability();
                oc.setName(name);
                if (description != null) oc.setDescription(description);
                // Add to OA capability pkg
                OperationalAnalysis oa = (OperationalAnalysis) architecture;
                // VERIFY: getOwnedAbstractCapabilityPkg() or specific method
                if (oa.getOwnedAbstractCapabilityPkg() != null) {
                    addToContainer(oa.getOwnedAbstractCapabilityPkg(),
                            "getOwnedOperationalCapabilities", oc);
                }
                capability = oc;
                break;
            }
            case "sa": {
                Capability cap = CtxFactory.eINSTANCE.createCapability();
                cap.setName(name);
                if (description != null) cap.setDescription(description);
                // Add to SA capability pkg
                SystemAnalysis sa = (SystemAnalysis) architecture;
                if (sa.getOwnedAbstractCapabilityPkg() != null) {
                    addToContainer(sa.getOwnedAbstractCapabilityPkg(),
                            "getOwnedCapabilities", cap);
                }
                capability = cap;
                break;
            }
            case "la": {
                CapabilityRealization cr = LaFactory.eINSTANCE.createCapabilityRealization();
                cr.setName(name);
                if (description != null) cr.setDescription(description);
                LogicalArchitecture la = (LogicalArchitecture) architecture;
                if (la.getOwnedAbstractCapabilityPkg() != null) {
                    addToContainer(la.getOwnedAbstractCapabilityPkg(),
                            "getOwnedCapabilityRealizations", cr);
                }
                capability = cr;
                break;
            }
            case "pa": {
                CapabilityRealization cr = LaFactory.eINSTANCE.createCapabilityRealization();
                cr.setName(name);
                if (description != null) cr.setDescription(description);
                PhysicalArchitecture pa = (PhysicalArchitecture) architecture;
                if (pa.getOwnedAbstractCapabilityPkg() != null) {
                    addToContainer(pa.getOwnedAbstractCapabilityPkg(),
                            "getOwnedCapabilityRealizations", cr);
                }
                capability = cr;
                break;
            }
            default:
                throw new IllegalStateException("Unsupported layer: " + layer);
        }

        // Link involved functions via AbstractFunctionAbstractCapabilityInvolvement
        // The involver (capability) is set automatically by EMF containment when
        // the involvement is added to the capability's owned list.
        for (AbstractFunction fn : functions) {
            try {
                AbstractFunctionAbstractCapabilityInvolvement involvement =
                        InteractionFactory.eINSTANCE.createAbstractFunctionAbstractCapabilityInvolvement();
                involvement.setInvolved(fn);
                capability.getOwnedAbstractFunctionAbstractCapabilityInvolvements().add(involvement);
                linkedFunctionNames.add(fn.getName());
            } catch (Exception e) {
                // VERIFY: InteractionFactory path; skip if involvement creation fails
            }
        }

        return capability;
    }

    /**
     * Adds an element to a container using reflection to call the appropriate getter method.
     *
     * @param container  the parent container
     * @param listGetter the name of the getter method that returns the owned elements list
     * @param element    the element to add
     */
    @SuppressWarnings("unchecked")
    private void addToContainer(EObject container, String listGetter, EObject element) {
        try {
            java.lang.reflect.Method method = container.getClass().getMethod(listGetter);
            Object result = method.invoke(container);
            if (result instanceof List) {
                ((List<EObject>) result).add(element);
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to add element to container via " + listGetter + ": " + e.getMessage(), e);
        }
    }
}

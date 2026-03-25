package com.capellaagent.modelchat.tools.write;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.RecordingCommand;

// PLACEHOLDER imports for Capella metamodel factories
// import org.polarsys.capella.core.data.oa.OaFactory;
// import org.polarsys.capella.core.data.oa.OperationalActivity;
// import org.polarsys.capella.core.data.oa.Entity;
// import org.polarsys.capella.core.data.ctx.CtxFactory;
// import org.polarsys.capella.core.data.ctx.SystemFunction;
// import org.polarsys.capella.core.data.ctx.SystemComponent;
// import org.polarsys.capella.core.data.la.LaFactory;
// import org.polarsys.capella.core.data.la.LogicalFunction;
// import org.polarsys.capella.core.data.la.LogicalComponent;
// import org.polarsys.capella.core.data.pa.PaFactory;
// import org.polarsys.capella.core.data.pa.PhysicalFunction;
// import org.polarsys.capella.core.data.pa.PhysicalComponent;

/**
 * Creates a new model element in the specified ARCADIA architecture layer.
 * <p>
 * Supports creating functions, components, and actors. The element is created
 * within the correct package of the specified layer, or under a provided parent
 * element. All model mutations are wrapped in an EMF {@link RecordingCommand}
 * for proper transactional handling and undo support.
 *
 * <h3>Tool Specification</h3>
 * <ul>
 *   <li><b>Name:</b> create_element</li>
 *   <li><b>Category:</b> model_write</li>
 *   <li><b>Parameters:</b>
 *     <ul>
 *       <li>{@code layer} (string, required) - Architecture layer: oa, sa, la, pa</li>
 *       <li>{@code type} (string, required) - Element type: function, component, actor</li>
 *       <li>{@code name} (string, required) - Name of the new element</li>
 *       <li>{@code parent_uuid} (string, optional) - UUID of the parent container</li>
 *       <li>{@code description} (string, optional) - Description text for the element</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class CreateElementTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "create_element";
    private static final String DESCRIPTION =
            "Creates a new model element (function, component, or actor) in the specified "
            + "ARCADIA layer. Returns the created element's details including its UUID.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");
    private static final List<String> VALID_TYPES = List.of("function", "component", "actor");

    public CreateElementTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("layer",
                "Architecture layer: oa, sa, la, pa"));
        params.add(ToolParameter.requiredString("type",
                "Element type to create: function, component, actor"));
        params.add(ToolParameter.requiredString("name",
                "Name of the new element"));
        params.add(ToolParameter.optionalString("parent_uuid",
                "UUID of the parent container element. If omitted, uses the default package for the layer."));
        params.add(ToolParameter.optionalString("description",
                "Description text for the element"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        String type = getRequiredString(parameters, "type").toLowerCase();
        String name = getRequiredString(parameters, "name");
        String parentUuid = getOptionalString(parameters, "parent_uuid", null);
        String description = getOptionalString(parameters, "description", null);

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

        if (name.isBlank()) {
            return ToolResult.error("Parameter 'name' must not be empty");
        }

        try {
            // Resolve parent container if specified
            EObject parentContainer = null;
            if (parentUuid != null && !parentUuid.isBlank()) {
                parentContainer = resolveElementByUuid(parentUuid);
                if (parentContainer == null) {
                    return ToolResult.error("Parent element not found with UUID: " + parentUuid);
                }
            }

            // PLACEHOLDER: Get the default container if no parent specified
            // if (parentContainer == null) {
            //     Session session = getActiveSession();
            //     BlockArchitecture arch = getArchitecture(session, layer);
            //     parentContainer = switch (type) {
            //         case "function" -> BlockArchitectureExt.getFunctionPkg(arch);
            //         case "component", "actor" -> BlockArchitectureExt.getComponentPkg(arch);
            //         default -> throw new IllegalArgumentException("Unknown type: " + type);
            //     };
            // }

            final EObject container = parentContainer != null ? parentContainer : getDefaultContainer(layer, type);
            final String elementName = name;
            final String elementDescription = description;
            final String elementType = type;
            final String elementLayer = layer;

            // Execute creation within a RecordingCommand for undo support
            TransactionalEditingDomain domain = getEditingDomain();
            final EObject[] created = new EObject[1];

            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Create " + type + " '" + name + "'") {
                @Override
                protected void doExecute() {
                    // PLACEHOLDER: Create element using Capella factories
                    //
                    // EObject newElement = switch (elementLayer + ":" + elementType) {
                    //     case "oa:function" -> {
                    //         OperationalActivity activity = OaFactory.eINSTANCE.createOperationalActivity();
                    //         activity.setName(elementName);
                    //         if (elementDescription != null) activity.setDescription(elementDescription);
                    //         ((OperationalActivityPkg) container).getOwnedOperationalActivities().add(activity);
                    //         yield activity;
                    //     }
                    //     case "oa:component", "oa:actor" -> {
                    //         Entity entity = OaFactory.eINSTANCE.createEntity();
                    //         entity.setName(elementName);
                    //         entity.setActor("actor".equals(elementType));
                    //         if (elementDescription != null) entity.setDescription(elementDescription);
                    //         ((EntityPkg) container).getOwnedEntities().add(entity);
                    //         yield entity;
                    //     }
                    //     case "sa:function" -> {
                    //         SystemFunction fn = CtxFactory.eINSTANCE.createSystemFunction();
                    //         fn.setName(elementName);
                    //         if (elementDescription != null) fn.setDescription(elementDescription);
                    //         ((SystemFunctionPkg) container).getOwnedSystemFunctions().add(fn);
                    //         yield fn;
                    //     }
                    //     case "sa:component", "sa:actor" -> {
                    //         SystemComponent comp = CtxFactory.eINSTANCE.createSystemComponent();
                    //         comp.setName(elementName);
                    //         comp.setActor("actor".equals(elementType));
                    //         if (elementDescription != null) comp.setDescription(elementDescription);
                    //         ((SystemComponentPkg) container).getOwnedSystemComponents().add(comp);
                    //         yield comp;
                    //     }
                    //     case "la:function" -> {
                    //         LogicalFunction fn = LaFactory.eINSTANCE.createLogicalFunction();
                    //         fn.setName(elementName);
                    //         if (elementDescription != null) fn.setDescription(elementDescription);
                    //         ((LogicalFunctionPkg) container).getOwnedLogicalFunctions().add(fn);
                    //         yield fn;
                    //     }
                    //     case "la:component", "la:actor" -> {
                    //         LogicalComponent comp = LaFactory.eINSTANCE.createLogicalComponent();
                    //         comp.setName(elementName);
                    //         comp.setActor("actor".equals(elementType));
                    //         if (elementDescription != null) comp.setDescription(elementDescription);
                    //         ((LogicalComponentPkg) container).getOwnedLogicalComponents().add(comp);
                    //         yield comp;
                    //     }
                    //     case "pa:function" -> {
                    //         PhysicalFunction fn = PaFactory.eINSTANCE.createPhysicalFunction();
                    //         fn.setName(elementName);
                    //         if (elementDescription != null) fn.setDescription(elementDescription);
                    //         ((PhysicalFunctionPkg) container).getOwnedPhysicalFunctions().add(fn);
                    //         yield fn;
                    //     }
                    //     case "pa:component", "pa:actor" -> {
                    //         PhysicalComponent comp = PaFactory.eINSTANCE.createPhysicalComponent();
                    //         comp.setName(elementName);
                    //         comp.setActor("actor".equals(elementType));
                    //         if (elementDescription != null) comp.setDescription(elementDescription);
                    //         ((PhysicalComponentPkg) container).getOwnedPhysicalComponents().add(comp);
                    //         yield comp;
                    //     }
                    //     default -> throw new IllegalStateException("Unsupported: " + elementLayer + ":" + elementType);
                    // };
                    // created[0] = newElement;

                    created[0] = createCapellaElement(container, elementLayer, elementType,
                            elementName, elementDescription);
                }
            });

            if (created[0] == null) {
                return ToolResult.error("Element creation failed - no element produced");
            }

            // Build response with created element details
            JsonObject response = new JsonObject();
            response.addProperty("status", "created");
            response.addProperty("name", getElementName(created[0]));
            response.addProperty("uuid", getElementId(created[0]));
            response.addProperty("type", created[0].eClass().getName());
            response.addProperty("layer", layer);
            response.addProperty("parent_uuid", getElementId(container));
            response.addProperty("parent_name", getElementName(container));

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to create element: " + e.getMessage());
        }
    }

    /**
     * Gets the default container package for the given layer and element type.
     *
     * @param layer the ARCADIA layer
     * @param type  the element type
     * @return the default container EObject
     */
    private EObject getDefaultContainer(String layer, String type) {
        // PLACEHOLDER: Navigate to the correct default package
        // This should resolve the FunctionPkg or ComponentPkg for the given layer
        throw new UnsupportedOperationException(
                "PLACEHOLDER: Resolve default container for " + layer + ":" + type);
    }

    /**
     * Creates a Capella element using the appropriate factory for the layer and type.
     *
     * @param container   the parent container
     * @param layer       the ARCADIA layer
     * @param type        the element type
     * @param name        the element name
     * @param description optional description
     * @return the created EObject
     */
    private EObject createCapellaElement(EObject container, String layer, String type,
                                          String name, String description) {
        // PLACEHOLDER: Implement using Capella EMF factories (see doExecute comments above)
        throw new UnsupportedOperationException(
                "PLACEHOLDER: Create " + type + " in " + layer + " layer");
    }
}

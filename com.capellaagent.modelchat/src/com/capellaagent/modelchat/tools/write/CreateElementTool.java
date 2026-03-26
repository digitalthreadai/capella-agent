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
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.ctx.CtxFactory;
import org.polarsys.capella.core.data.ctx.SystemAnalysis;
import org.polarsys.capella.core.data.ctx.SystemComponent;
import org.polarsys.capella.core.data.ctx.SystemFunction;
import org.polarsys.capella.core.data.la.LaFactory;
import org.polarsys.capella.core.data.la.LogicalArchitecture;
import org.polarsys.capella.core.data.la.LogicalComponent;
import org.polarsys.capella.core.data.la.LogicalFunction;
import org.polarsys.capella.core.data.oa.Entity;
import org.polarsys.capella.core.data.oa.OaFactory;
import org.polarsys.capella.core.data.oa.OperationalActivity;
import org.polarsys.capella.core.data.oa.OperationalAnalysis;
import org.polarsys.capella.core.data.pa.PaFactory;
import org.polarsys.capella.core.data.pa.PhysicalArchitecture;
import org.polarsys.capella.core.data.pa.PhysicalComponent;
import org.polarsys.capella.core.data.pa.PhysicalFunction;
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;

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
        String rawName = getRequiredString(parameters, "name");
        String parentUuid = getOptionalString(parameters, "parent_uuid", null);
        String rawDescription = getOptionalString(parameters, "description", null);

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
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();

            // Resolve parent container if specified
            EObject parentContainer = null;
            if (parentUuid != null && !parentUuid.isBlank()) {
                parentContainer = resolveElementByUuid(parentUuid);
                if (parentContainer == null) {
                    return ToolResult.error("Parent element not found with UUID: " + parentUuid);
                }
            }

            // Get the default container if no parent specified
            if (parentContainer == null) {
                BlockArchitecture arch = modelService.getArchitecture(session, layer);
                parentContainer = getDefaultContainer(arch, layer, type);
            }

            final EObject container = parentContainer;
            final String elementName = name;
            final String elementDescription = description;
            final String elementType = type;
            final String elementLayer = layer;

            // Execute creation within a RecordingCommand for undo support
            TransactionalEditingDomain domain = getEditingDomain(session);
            final EObject[] created = new EObject[1];

            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Create " + type + " '" + name + "'") {
                @Override
                protected void doExecute() {
                    created[0] = createCapellaElement(container, elementLayer, elementType,
                            elementName, elementDescription);
                }
            });

            if (created[0] == null) {
                return ToolResult.error("Element creation failed - no element produced");
            }

            // Invalidate UUID cache since we added a new element
            modelService.invalidateCache(session);

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
     * Gets the default container package for the given architecture, layer, and element type.
     * Functions go into the function package; components/actors go into the component package.
     *
     * @param arch  the BlockArchitecture
     * @param layer the ARCADIA layer
     * @param type  the element type
     * @return the default container EObject
     */
    private EObject getDefaultContainer(BlockArchitecture arch, String layer, String type) {
        // Navigate to the correct function or component package based on layer
        switch (layer) {
            case "oa": {
                OperationalAnalysis oa = (OperationalAnalysis) arch;
                if ("function".equals(type)) {
                    return oa.getOwnedFunctionPkg();
                } else {
                    return oa.getOwnedEntityPkg();
                }
            }
            case "sa": {
                SystemAnalysis sa = (SystemAnalysis) arch;
                if ("function".equals(type)) {
                    return sa.getOwnedFunctionPkg();
                } else {
                    return sa.getOwnedSystemComponentPkg();
                }
            }
            case "la": {
                LogicalArchitecture la = (LogicalArchitecture) arch;
                if ("function".equals(type)) {
                    return la.getOwnedFunctionPkg();
                } else {
                    return la.getOwnedLogicalComponentPkg();
                }
            }
            case "pa": {
                PhysicalArchitecture pa = (PhysicalArchitecture) arch;
                if ("function".equals(type)) {
                    return pa.getOwnedFunctionPkg();
                } else {
                    return pa.getOwnedPhysicalComponentPkg();
                }
            }
            default:
                throw new IllegalArgumentException(
                        "Cannot resolve default container for layer: " + layer);
        }
    }

    /**
     * Creates a Capella element using the appropriate factory for the layer and type.
     * Must be called within a RecordingCommand transaction.
     *
     * @param container   the parent container
     * @param layer       the ARCADIA layer
     * @param type        the element type
     * @param name        the element name (already sanitized)
     * @param description optional description (already sanitized)
     * @return the created EObject
     */
    @SuppressWarnings("unchecked")
    private EObject createCapellaElement(EObject container, String layer, String type,
                                          String name, String description) {
        String key = layer + ":" + type;

        switch (key) {
            case "oa:function": {
                OperationalActivity activity = OaFactory.eINSTANCE.createOperationalActivity();
                activity.setName(name);
                if (description != null) activity.setDescription(description);
                // Add to the container's owned activities
                addToContainer(container, "getOwnedOperationalActivities", activity);
                return activity;
            }
            case "oa:component":
            case "oa:actor": {
                Entity entity = OaFactory.eINSTANCE.createEntity();
                entity.setName(name);
                entity.setActor("actor".equals(type));
                if (description != null) entity.setDescription(description);
                addToContainer(container, "getOwnedEntities", entity);
                return entity;
            }
            case "sa:function": {
                SystemFunction fn = CtxFactory.eINSTANCE.createSystemFunction();
                fn.setName(name);
                if (description != null) fn.setDescription(description);
                addToContainer(container, "getOwnedSystemFunctions", fn);
                return fn;
            }
            case "sa:component":
            case "sa:actor": {
                SystemComponent comp = CtxFactory.eINSTANCE.createSystemComponent();
                comp.setName(name);
                comp.setActor("actor".equals(type));
                if (description != null) comp.setDescription(description);
                addToContainer(container, "getOwnedSystemComponents", comp);
                return comp;
            }
            case "la:function": {
                LogicalFunction fn = LaFactory.eINSTANCE.createLogicalFunction();
                fn.setName(name);
                if (description != null) fn.setDescription(description);
                addToContainer(container, "getOwnedLogicalFunctions", fn);
                return fn;
            }
            case "la:component":
            case "la:actor": {
                LogicalComponent comp = LaFactory.eINSTANCE.createLogicalComponent();
                comp.setName(name);
                comp.setActor("actor".equals(type));
                if (description != null) comp.setDescription(description);
                addToContainer(container, "getOwnedLogicalComponents", comp);
                return comp;
            }
            case "pa:function": {
                PhysicalFunction fn = PaFactory.eINSTANCE.createPhysicalFunction();
                fn.setName(name);
                if (description != null) fn.setDescription(description);
                addToContainer(container, "getOwnedPhysicalFunctions", fn);
                return fn;
            }
            case "pa:component":
            case "pa:actor": {
                PhysicalComponent comp = PaFactory.eINSTANCE.createPhysicalComponent();
                comp.setName(name);
                comp.setActor("actor".equals(type));
                if (description != null) comp.setDescription(description);
                addToContainer(container, "getOwnedPhysicalComponents", comp);
                return comp;
            }
            default:
                throw new IllegalStateException("Unsupported layer:type combination: " + key);
        }
    }

    /**
     * Adds an element to a container using reflection to call the appropriate getter method.
     * This handles the various package types (FunctionPkg, ComponentPkg, EntityPkg, etc.)
     * which each have different list accessor names.
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

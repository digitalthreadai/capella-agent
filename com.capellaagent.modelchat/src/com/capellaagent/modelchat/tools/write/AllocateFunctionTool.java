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

// PLACEHOLDER imports for Capella allocation metamodel
// import org.polarsys.capella.core.data.fa.AbstractFunction;
// import org.polarsys.capella.core.data.fa.ComponentFunctionalAllocation;
// import org.polarsys.capella.core.data.fa.FaFactory;
// import org.polarsys.capella.core.data.cs.Component;

/**
 * Allocates a function to a component in the Capella model.
 * <p>
 * Creates a {@code ComponentFunctionalAllocation} link between a function and a
 * component within the same ARCADIA layer. Both the function and component must
 * exist and reside in the same architecture layer.
 *
 * <h3>Tool Specification</h3>
 * <ul>
 *   <li><b>Name:</b> allocate_function</li>
 *   <li><b>Category:</b> model_write</li>
 *   <li><b>Parameters:</b>
 *     <ul>
 *       <li>{@code function_uuid} (string, required) - UUID of the function to allocate</li>
 *       <li>{@code component_uuid} (string, required) - UUID of the component to allocate to</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class AllocateFunctionTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "allocate_function";
    private static final String DESCRIPTION =
            "Allocates a function to a component, creating a ComponentFunctionalAllocation link. "
            + "Both elements must be in the same ARCADIA layer.";

    public AllocateFunctionTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("function_uuid",
                "UUID of the function to allocate"));
        params.add(ToolParameter.requiredString("component_uuid",
                "UUID of the component to allocate the function to"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String functionUuid = getRequiredString(parameters, "function_uuid");
        String componentUuid = getRequiredString(parameters, "component_uuid");

        if (functionUuid.isBlank()) {
            return ToolResult.error("Parameter 'function_uuid' must not be empty");
        }
        if (componentUuid.isBlank()) {
            return ToolResult.error("Parameter 'component_uuid' must not be empty");
        }

        try {
            // Resolve both elements
            EObject functionObj = resolveElementByUuid(functionUuid);
            if (functionObj == null) {
                return ToolResult.error("Function not found with UUID: " + functionUuid);
            }

            EObject componentObj = resolveElementByUuid(componentUuid);
            if (componentObj == null) {
                return ToolResult.error("Component not found with UUID: " + componentUuid);
            }

            // PLACEHOLDER: Validate types
            // if (!(functionObj instanceof AbstractFunction)) {
            //     return ToolResult.error("Element " + functionUuid
            //             + " is not a function (type: " + functionObj.eClass().getName() + ")");
            // }
            // if (!(componentObj instanceof Component)) {
            //     return ToolResult.error("Element " + componentUuid
            //             + " is not a component (type: " + componentObj.eClass().getName() + ")");
            // }

            // PLACEHOLDER: Validate same layer
            // String fnLayer = detectLayer(functionObj);
            // String compLayer = detectLayer(componentObj);
            // if (!fnLayer.equals(compLayer)) {
            //     return ToolResult.error("Function (layer: " + fnLayer
            //             + ") and component (layer: " + compLayer + ") must be in the same layer");
            // }

            // PLACEHOLDER: Check if allocation already exists
            // Component comp = (Component) componentObj;
            // for (ComponentFunctionalAllocation existing : comp.getFunctionalAllocations()) {
            //     if (existing.getFunction() == functionObj) {
            //         return ToolResult.error("Function '" + getElementName(functionObj)
            //                 + "' is already allocated to component '" + getElementName(componentObj) + "'");
            //     }
            // }

            final EObject fn = functionObj;
            final EObject comp = componentObj;

            TransactionalEditingDomain domain = getEditingDomain();
            final EObject[] allocationLink = new EObject[1];

            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Allocate function '" + getElementName(fn) + "' to '" + getElementName(comp) + "'") {
                @Override
                protected void doExecute() {
                    // PLACEHOLDER: Create ComponentFunctionalAllocation
                    //
                    // ComponentFunctionalAllocation allocation =
                    //         FaFactory.eINSTANCE.createComponentFunctionalAllocation();
                    // allocation.setSourceElement((Component) comp);
                    // allocation.setTargetElement((AbstractFunction) fn);
                    //
                    // ((Component) comp).getOwnedFunctionalAllocation().add(allocation);
                    // allocationLink[0] = allocation;

                    allocationLink[0] = performAllocation(fn, comp);
                }
            });

            JsonObject response = new JsonObject();
            response.addProperty("status", "allocated");
            response.addProperty("function_name", getElementName(fn));
            response.addProperty("function_uuid", functionUuid);
            response.addProperty("function_type", fn.eClass().getName());
            response.addProperty("component_name", getElementName(comp));
            response.addProperty("component_uuid", componentUuid);
            response.addProperty("component_type", comp.eClass().getName());
            if (allocationLink[0] != null) {
                response.addProperty("allocation_uuid", getElementId(allocationLink[0]));
            }

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to allocate function: " + e.getMessage());
        }
    }

    /**
     * Performs the allocation of a function to a component.
     *
     * @param function  the function EObject
     * @param component the component EObject
     * @return the created allocation link EObject
     */
    private EObject performAllocation(EObject function, EObject component) {
        // PLACEHOLDER: Implement using Capella factories
        throw new UnsupportedOperationException(
                "PLACEHOLDER: Create ComponentFunctionalAllocation link");
    }
}

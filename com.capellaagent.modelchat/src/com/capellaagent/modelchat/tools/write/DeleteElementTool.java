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
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.RecordingCommand;

// PLACEHOLDER imports for Capella metamodel
// import org.polarsys.capella.core.data.capellacore.NamedElement;
// import org.polarsys.capella.core.platform.sirius.ui.commands.CapellaDeleteCommand;

/**
 * Deletes a model element from the Capella model.
 * <p>
 * This is a destructive operation that requires explicit confirmation via the
 * {@code confirm} parameter set to {@code true}. The tool captures the deleted
 * element's details before removal and returns them in the response. The operation
 * is wrapped in a {@link RecordingCommand} for undo support.
 *
 * <h3>Tool Specification</h3>
 * <ul>
 *   <li><b>Name:</b> delete_element</li>
 *   <li><b>Category:</b> model_write</li>
 *   <li><b>Parameters:</b>
 *     <ul>
 *       <li>{@code uuid} (string, required) - UUID of the element to delete</li>
 *       <li>{@code confirm} (boolean, required) - Must be true to proceed with deletion</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class DeleteElementTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "delete_element";
    private static final String DESCRIPTION =
            "Deletes a model element. Requires explicit confirmation (confirm=true). "
            + "Returns the deleted element's details. This operation can be undone.";

    public DeleteElementTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("uuid",
                "UUID of the element to delete"));
        params.add(ToolParameter.requiredBoolean("confirm",
                "Must be set to true to confirm the deletion"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String uuid = getRequiredString(parameters, "uuid");
        boolean confirm = getRequiredBoolean(parameters, "confirm");

        if (uuid.isBlank()) {
            return ToolResult.error("Parameter 'uuid' must not be empty");
        }

        if (!confirm) {
            return ToolResult.error(
                    "Deletion requires 'confirm' to be true. "
                    + "Set confirm=true to proceed with deleting the element.");
        }

        try {
            EObject element = resolveElementByUuid(uuid);
            if (element == null) {
                return ToolResult.error("Element not found with UUID: " + uuid);
            }

            // Capture element details before deletion
            String elementName = getElementName(element);
            String elementType = element.eClass().getName();
            String elementDescription = getElementDescription(element);

            // Count children that will also be deleted
            int childCount = countAllDescendants(element);

            // Capture direct references that will be affected
            JsonArray affectedReferences = captureAffectedReferences(element);

            // Capture parent info
            EObject parent = element.eContainer();
            String parentName = parent != null ? getElementName(parent) : null;
            String parentUuid = parent != null ? getElementId(parent) : null;

            final EObject targetElement = element;

            TransactionalEditingDomain domain = getEditingDomain();

            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Delete " + elementType + " '" + elementName + "'") {
                @Override
                protected void doExecute() {
                    // PLACEHOLDER: Use Capella's delete command for proper cleanup
                    // CapellaDeleteCommand provides semantic cleanup beyond simple
                    // EcoreUtil.delete(), handling diagram representations, traces, etc.
                    //
                    // Preferred approach:
                    // CapellaDeleteCommand deleteCmd = new CapellaDeleteCommand(
                    //     domain, Collections.singleton(targetElement), true, false, true);
                    // if (deleteCmd.canExecute()) {
                    //     deleteCmd.execute();
                    // }
                    //
                    // Fallback approach using EcoreUtil:
                    EcoreUtil.delete(targetElement, true);
                }
            });

            // Build response with deleted element details
            JsonObject response = new JsonObject();
            response.addProperty("status", "deleted");
            response.addProperty("name", elementName);
            response.addProperty("uuid", uuid);
            response.addProperty("type", elementType);
            response.addProperty("description", truncate(elementDescription, 200));
            response.addProperty("children_deleted", childCount);

            if (parentName != null) {
                JsonObject parentObj = new JsonObject();
                parentObj.addProperty("name", parentName);
                parentObj.addProperty("uuid", parentUuid);
                response.add("parent", parentObj);
            }

            if (affectedReferences.size() > 0) {
                response.add("affected_references", affectedReferences);
            }

            response.addProperty("undoable", true);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to delete element: " + e.getMessage());
        }
    }

    /**
     * Counts all descendants of an element recursively.
     *
     * @param element the root element
     * @return the total number of descendant elements
     */
    private int countAllDescendants(EObject element) {
        int count = 0;
        for (EObject child : element.eContents()) {
            count += 1 + countAllDescendants(child);
        }
        return count;
    }

    /**
     * Captures references from other elements that point to the element being deleted.
     * This helps inform the user about potential side effects of the deletion.
     *
     * @param element the element about to be deleted
     * @return a JsonArray of affected reference summaries
     */
    private JsonArray captureAffectedReferences(EObject element) {
        JsonArray affected = new JsonArray();
        // PLACEHOLDER: Use ECrossReferenceAdapter to find incoming references
        // ECrossReferenceAdapter adapter = ECrossReferenceAdapter.getCrossReferenceAdapter(element);
        // if (adapter != null) {
        //     for (Setting setting : adapter.getInverseReferences(element)) {
        //         EObject referencing = setting.getEObject();
        //         JsonObject refObj = new JsonObject();
        //         refObj.addProperty("referencing_element", getElementName(referencing));
        //         refObj.addProperty("referencing_uuid", getElementId(referencing));
        //         refObj.addProperty("reference_type", setting.getEStructuralFeature().getName());
        //         affected.add(refObj);
        //     }
        // }
        return affected;
    }
}

package com.capellaagent.modelchat.tools.write;

import java.util.ArrayList;
import java.util.Collection;
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
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.sirius.business.api.session.Session;

/**
 * Deletes a model element from the Capella model.
 * <p>
 * This is a destructive operation. The {@code confirm} parameter is always
 * required and must be {@code true}. The human-in-the-loop system prompt
 * ensures the LLM asks the user before calling this tool. The operation is
 * wrapped in a {@link RecordingCommand} for undo support.
 * <p>
 * Before deletion, the tool captures the element's details (name, type,
 * parent, child count, affected references) and returns them in the response
 * for audit purposes.
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

        // Validate UUID
        try {
            uuid = InputValidator.validateUuid(uuid);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Invalid UUID: " + e.getMessage());
        }

        // Confirm parameter must always be true - this is a safety gate
        if (!confirm) {
            return ToolResult.error(
                    "Deletion requires 'confirm' to be true. "
                    + "Set confirm=true to proceed with deleting the element.");
        }

        try {
            Session session = getActiveSession();
            EObject element = resolveElementByUuid(uuid);
            if (element == null) {
                return ToolResult.error("Element not found with UUID: " + uuid);
            }

            // Capture element details before deletion for audit trail
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

            TransactionalEditingDomain domain = getEditingDomain(session);

            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Delete " + elementType + " '" + elementName + "'") {
                @Override
                protected void doExecute() {
                    // Use EcoreUtil.delete with resolve=true to clean up cross-references
                    // VERIFY: CapellaDeleteCommand would be preferred for full cleanup
                    // including diagram representations, but it requires additional
                    // UI-level dependencies not available in this bundle.
                    EcoreUtil.delete(targetElement, true);
                }
            });

            // Invalidate UUID cache since we removed an element
            getModelService().invalidateCache(session);

            // Build response with deleted element details for audit
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
     * Uses the element's cross-references to identify elements that will be affected.
     *
     * @param element the element about to be deleted
     * @return a JsonArray of affected reference summaries
     */
    private JsonArray captureAffectedReferences(EObject element) {
        JsonArray affected = new JsonArray();
        int count = 0;

        try {
            // Use EcoreUtil.UsageCrossReferencer to find inverse references
            Collection<EStructuralFeature.Setting> usages =
                    EcoreUtil.UsageCrossReferencer.find(element, element.eResource());
            if (usages != null) {
                for (EStructuralFeature.Setting setting : usages) {
                    if (count >= 20) break; // Limit to avoid oversized response
                    EObject referencing = setting.getEObject();
                    if (referencing != null && setting.getEStructuralFeature() instanceof EReference) {
                        EReference ref = (EReference) setting.getEStructuralFeature();
                        if (!ref.isContainment()) {
                            JsonObject refObj = new JsonObject();
                            refObj.addProperty("referencing_element", getElementName(referencing));
                            refObj.addProperty("referencing_uuid", getElementId(referencing));
                            refObj.addProperty("reference_type", ref.getName());
                            affected.add(refObj);
                            count++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Cross-reference lookup may fail; return what we have
        }

        return affected;
    }
}

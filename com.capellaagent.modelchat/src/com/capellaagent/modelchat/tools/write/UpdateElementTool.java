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
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;
import org.polarsys.capella.core.data.capellacore.CapellaElement; // VERIFY: exact package

/**
 * Updates the properties of an existing model element.
 * <p>
 * Currently supports updating the element's name and description. The operation is
 * wrapped in an EMF {@link RecordingCommand} for transactional safety and undo support.
 *
 * <h3>Tool Specification</h3>
 * <ul>
 *   <li><b>Name:</b> update_element</li>
 *   <li><b>Category:</b> model_write</li>
 *   <li><b>Parameters:</b>
 *     <ul>
 *       <li>{@code uuid} (string, required) - UUID of the element to update</li>
 *       <li>{@code name} (string, optional) - New name for the element</li>
 *       <li>{@code description} (string, optional) - New description for the element</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class UpdateElementTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "update_element";
    private static final String DESCRIPTION =
            "Updates the name and/or description of an existing model element. "
            + "At least one of name or description must be provided.";

    public UpdateElementTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("uuid",
                "UUID of the element to update"));
        params.add(ToolParameter.optionalString("name",
                "New name for the element"));
        params.add(ToolParameter.optionalString("description",
                "New description for the element"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String uuid = getRequiredString(parameters, "uuid");
        String newName = getOptionalString(parameters, "name", null);
        String newDescription = getOptionalString(parameters, "description", null);

        if (uuid.isBlank()) {
            return ToolResult.error("Parameter 'uuid' must not be empty");
        }

        if ((newName == null || newName.isBlank()) && newDescription == null) {
            return ToolResult.error("At least one of 'name' or 'description' must be provided");
        }

        // Sanitize inputs
        try {
            if (newName != null && !newName.isBlank()) {
                newName = InputValidator.sanitizeName(newName);
            }
            if (newDescription != null) {
                newDescription = InputValidator.sanitizeDescription(newDescription);
            }
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Input validation failed: " + e.getMessage());
        }

        try {
            Session session = getActiveSession();
            EObject element = resolveElementByUuid(uuid);
            if (element == null) {
                return ToolResult.error("Element not found with UUID: " + uuid);
            }

            // Verify element is a NamedElement that supports name/description
            if (!(element instanceof AbstractNamedElement)) {
                return ToolResult.error("Element of type '" + element.eClass().getName()
                        + "' does not support name/description updates");
            }

            // Capture old values for the response
            String oldName = getElementName(element);
            String oldDescription = getElementDescription(element);

            final EObject targetElement = element;
            final String nameToSet = newName;
            final String descToSet = newDescription;

            TransactionalEditingDomain domain = getEditingDomain(session);

            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Update element '" + oldName + "'") {
                @Override
                protected void doExecute() {
                    updateElementProperties(targetElement, nameToSet, descToSet);
                }
            });

            // Invalidate cache if name changed (affects search results)
            if (nameToSet != null && !nameToSet.isBlank()) {
                getModelService().invalidateCache(session);
            }

            // Build response with before/after values
            JsonObject response = new JsonObject();
            response.addProperty("status", "updated");
            response.addProperty("uuid", uuid);
            response.addProperty("type", element.eClass().getName());

            JsonObject changes = new JsonObject();
            if (newName != null && !newName.isBlank()) {
                JsonObject nameChange = new JsonObject();
                nameChange.addProperty("old", oldName);
                nameChange.addProperty("new", newName);
                changes.add("name", nameChange);
            }
            if (newDescription != null) {
                JsonObject descChange = new JsonObject();
                descChange.addProperty("old", truncate(oldDescription, 200));
                descChange.addProperty("new", truncate(newDescription, 200));
                changes.add("description", descChange);
            }
            response.add("changes", changes);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to update element: " + e.getMessage());
        }
    }

    /**
     * Updates the name and/or description of the target element using
     * the Capella metamodel setters. Must be called within a RecordingCommand.
     *
     * @param element     the element to update
     * @param name        the new name, or null to skip
     * @param description the new description, or null to skip
     */
    private void updateElementProperties(EObject element, String name, String description) {
        // Use AbstractNamedElement for name
        if (element instanceof AbstractNamedElement) {
            AbstractNamedElement named = (AbstractNamedElement) element;
            if (name != null && !name.isBlank()) {
                named.setName(name);
            }
        }

        // Use reflection-based approach for description since the exact interface varies
        // In Capella, CapellaElement has setDescription()
        if (description != null) {
            try {
                java.lang.reflect.Method setDesc = element.getClass().getMethod(
                        "setDescription", String.class);
                setDesc.invoke(element, description);
            } catch (NoSuchMethodException e) {
                // Element does not support description; try via EStructuralFeature
                org.eclipse.emf.ecore.EStructuralFeature descFeature =
                        element.eClass().getEStructuralFeature("description");
                if (descFeature != null) {
                    element.eSet(descFeature, description);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to set description: " + e.getMessage(), e);
            }
        }
    }
}

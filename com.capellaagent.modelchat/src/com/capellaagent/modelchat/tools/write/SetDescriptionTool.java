package com.capellaagent.modelchat.tools.write;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.security.InputValidator;
import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.sirius.business.api.session.Session;

/**
 * Sets or updates the description of a model element.
 * <p>
 * Works on any element that has a "description" EStructuralFeature, which
 * includes virtually all Capella NamedElements. Supports both setting a new
 * description and appending to an existing one.
 */
public class SetDescriptionTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "set_description";
    private static final String DESCRIPTION =
            "Sets or updates the description text of a model element.";

    private static final List<String> VALID_MODES = List.of("replace", "append", "prepend");

    public SetDescriptionTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("uuid",
                "UUID of the element whose description to set"));
        params.add(ToolParameter.requiredString("description",
                "The description text to set"));
        params.add(ToolParameter.optionalEnum("mode",
                "How to apply: replace (default), append, prepend",
                VALID_MODES, "replace"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String uuid = getRequiredString(parameters, "uuid");
        String rawDescription = getRequiredString(parameters, "description");
        String mode = getOptionalString(parameters, "mode", "replace").toLowerCase();

        // Sanitize
        String newDescription;
        try {
            newDescription = InputValidator.sanitizeDescription(rawDescription);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Input validation failed: " + e.getMessage());
        }

        if (!VALID_MODES.contains(mode)) {
            return ToolResult.error("Invalid mode '" + mode + "'. Must be: replace, append, prepend");
        }

        try {
            Session session = getActiveSession();

            EObject element = resolveElementByUuid(uuid);
            if (element == null) {
                return ToolResult.error("Element not found: " + uuid);
            }

            // Check if element has a description feature
            EStructuralFeature descFeature = element.eClass().getEStructuralFeature("description");
            if (descFeature == null) {
                return ToolResult.error("Element type '" + element.eClass().getName()
                        + "' does not have a description field");
            }

            // Capture old description
            Object oldValue = element.eGet(descFeature);
            String oldDescription = oldValue != null ? oldValue.toString() : "";

            // Compute final description
            final String finalDescription;
            switch (mode) {
                case "append":
                    finalDescription = oldDescription.isEmpty()
                            ? newDescription
                            : oldDescription + "\n" + newDescription;
                    break;
                case "prepend":
                    finalDescription = oldDescription.isEmpty()
                            ? newDescription
                            : newDescription + "\n" + oldDescription;
                    break;
                case "replace":
                default:
                    finalDescription = newDescription;
                    break;
            }

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Set description on '" + getElementName(element) + "'") {
                @Override
                protected void doExecute() {
                    element.eSet(descFeature, finalDescription);
                }
            });

            JsonObject response = new JsonObject();
            response.addProperty("status", "updated");
            response.addProperty("element_name", getElementName(element));
            response.addProperty("element_id", getElementId(element));
            response.addProperty("element_type", element.eClass().getName());
            response.addProperty("mode", mode);
            response.addProperty("old_description_preview", truncate(oldDescription, 200));
            response.addProperty("new_description_preview", truncate(finalDescription, 200));

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to set description: " + e.getMessage());
        }
    }
}

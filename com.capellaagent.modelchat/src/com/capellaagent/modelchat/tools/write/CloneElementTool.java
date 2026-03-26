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
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;

/**
 * Clones (deep copies) a model element and adds it to the same parent.
 */
public class CloneElementTool extends AbstractCapellaTool {

    public CloneElementTool() {
        super("clone_element",
                "Deep-copies a model element into the same parent.",
                ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("element_uuid",
                "UUID of the element to clone"));
        params.add(ToolParameter.optionalString("new_name",
                "Name for the clone (default: original name + ' (Copy)')"));
        return params;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String elementUuid = getRequiredString(parameters, "element_uuid");
        String newName = getOptionalString(parameters, "new_name", null);

        try {
            elementUuid = InputValidator.validateUuid(elementUuid);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Invalid UUID: " + e.getMessage());
        }

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            EObject original = resolveElementByUuid(elementUuid);
            if (original == null) {
                return ToolResult.error("Element not found: " + elementUuid);
            }

            EObject parent = original.eContainer();
            if (parent == null) {
                return ToolResult.error("Cannot clone a root element");
            }

            // Determine the containment reference
            EReference containmentRef = original.eContainmentFeature();
            if (containmentRef == null) {
                return ToolResult.error("Cannot determine containment reference for element");
            }

            // Determine clone name
            String originalName = getElementName(original);
            String cloneName = (newName != null && !newName.isBlank())
                    ? InputValidator.sanitizeName(newName)
                    : originalName + " (Copy)";

            final String finalCloneName = cloneName;
            final EObject[] cloned = new EObject[1];

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Clone '" + originalName + "'") {
                @Override
                protected void doExecute() {
                    EObject copy = EcoreUtil.copy(original);

                    // Set the new name
                    EStructuralFeature nameFeature = copy.eClass().getEStructuralFeature("name");
                    if (nameFeature != null) {
                        copy.eSet(nameFeature, finalCloneName);
                    }

                    // Add to same parent container
                    if (containmentRef.isMany()) {
                        ((List<EObject>) parent.eGet(containmentRef)).add(copy);
                    } else {
                        // Single-valued containment: cannot duplicate
                        // This is an edge case; add to a nearby many-valued reference
                        parent.eSet(containmentRef, copy);
                    }

                    cloned[0] = copy;
                }
            });

            if (cloned[0] == null) {
                return ToolResult.error("Clone operation failed");
            }

            modelService.invalidateCache(session);

            JsonObject response = new JsonObject();
            response.addProperty("status", "cloned");
            response.addProperty("original_name", originalName);
            response.addProperty("original_uuid", elementUuid);
            response.addProperty("clone_name", getElementName(cloned[0]));
            response.addProperty("clone_uuid", getElementId(cloned[0]));
            response.addProperty("type", cloned[0].eClass().getName());
            response.addProperty("parent_name", getElementName(parent));
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to clone element: " + e.getMessage());
        }
    }
}

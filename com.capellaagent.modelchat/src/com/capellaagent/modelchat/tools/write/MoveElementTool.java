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

/**
 * Moves a model element from its current container to a new parent container.
 */
public class MoveElementTool extends AbstractCapellaTool {

    public MoveElementTool() {
        super("move_element",
                "Moves a model element to a different parent container.",
                ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("element_uuid",
                "UUID of the element to move"));
        params.add(ToolParameter.requiredString("target_parent_uuid",
                "UUID of the new parent container"));
        return params;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String elementUuid = getRequiredString(parameters, "element_uuid");
        String targetUuid = getRequiredString(parameters, "target_parent_uuid");

        try {
            elementUuid = InputValidator.validateUuid(elementUuid);
            targetUuid = InputValidator.validateUuid(targetUuid);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Invalid UUID: " + e.getMessage());
        }

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            EObject element = resolveElementByUuid(elementUuid);
            if (element == null) {
                return ToolResult.error("Element not found: " + elementUuid);
            }

            EObject targetParent = resolveElementByUuid(targetUuid);
            if (targetParent == null) {
                return ToolResult.error("Target parent not found: " + targetUuid);
            }

            EObject oldParent = element.eContainer();
            if (oldParent == null) {
                return ToolResult.error("Element has no container (is a root element)");
            }

            // Find a compatible containment reference in the target
            EReference containmentRef = findCompatibleContainment(targetParent, element);
            if (containmentRef == null) {
                return ToolResult.error("Target '" + getElementName(targetParent)
                        + "' (" + targetParent.eClass().getName()
                        + ") cannot contain element of type " + element.eClass().getName());
            }

            String oldParentName = getElementName(oldParent);
            String oldParentId = getElementId(oldParent);

            final EReference ref = containmentRef;
            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Move element '" + getElementName(element) + "'") {
                @Override
                protected void doExecute() {
                    // Remove from old parent (EMF handles this automatically on add)
                    if (ref.isMany()) {
                        ((List<EObject>) targetParent.eGet(ref)).add(element);
                    } else {
                        targetParent.eSet(ref, element);
                    }
                }
            });

            modelService.invalidateCache(session);

            JsonObject response = new JsonObject();
            response.addProperty("status", "moved");
            response.addProperty("element_name", getElementName(element));
            response.addProperty("element_uuid", elementUuid);
            response.addProperty("old_parent_name", oldParentName);
            response.addProperty("old_parent_uuid", oldParentId);
            response.addProperty("new_parent_name", getElementName(targetParent));
            response.addProperty("new_parent_uuid", targetUuid);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to move element: " + e.getMessage());
        }
    }

    private EReference findCompatibleContainment(EObject target, EObject element) {
        for (EReference ref : target.eClass().getEAllContainments()) {
            if (ref.getEReferenceType().isSuperTypeOf(element.eClass())) {
                return ref;
            }
        }
        return null;
    }
}

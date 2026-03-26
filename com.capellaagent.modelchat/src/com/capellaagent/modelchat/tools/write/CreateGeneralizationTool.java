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
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.core.data.capellacore.CapellacoreFactory;
import org.polarsys.capella.core.data.capellacore.Generalization;
import org.polarsys.capella.core.data.capellacore.GeneralizableElement;

/**
 * Creates a generalization (inheritance) link between two elements.
 */
public class CreateGeneralizationTool extends AbstractCapellaTool {

    public CreateGeneralizationTool() {
        super("create_generalization",
                "Creates a generalization link (sub extends super).",
                ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("sub_uuid",
                "UUID of the sub-type (child)"));
        params.add(ToolParameter.requiredString("super_uuid",
                "UUID of the super-type (parent)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String subUuid = getRequiredString(parameters, "sub_uuid");
        String superUuid = getRequiredString(parameters, "super_uuid");

        try {
            subUuid = InputValidator.validateUuid(subUuid);
            superUuid = InputValidator.validateUuid(superUuid);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Invalid UUID: " + e.getMessage());
        }

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            EObject subObj = resolveElementByUuid(subUuid);
            if (subObj == null) {
                return ToolResult.error("Sub-type not found: " + subUuid);
            }
            if (!(subObj instanceof GeneralizableElement)) {
                return ToolResult.error("Sub-type is not generalizable: " + subObj.eClass().getName());
            }

            EObject superObj = resolveElementByUuid(superUuid);
            if (superObj == null) {
                return ToolResult.error("Super-type not found: " + superUuid);
            }
            if (!(superObj instanceof GeneralizableElement)) {
                return ToolResult.error("Super-type is not generalizable: " + superObj.eClass().getName());
            }

            GeneralizableElement subElement = (GeneralizableElement) subObj;
            GeneralizableElement superElement = (GeneralizableElement) superObj;

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Create generalization") {
                @Override
                protected void doExecute() {
                    Generalization gen = CapellacoreFactory.eINSTANCE.createGeneralization();
                    gen.setSub(subElement);
                    gen.setSuper(superElement);
                    subElement.getOwnedGeneralizations().add(gen);
                }
            });

            modelService.invalidateCache(session);

            JsonObject response = new JsonObject();
            response.addProperty("status", "created");
            response.addProperty("sub_name", getElementName(subObj));
            response.addProperty("sub_uuid", subUuid);
            response.addProperty("super_name", getElementName(superObj));
            response.addProperty("super_uuid", superUuid);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to create generalization: " + e.getMessage());
        }
    }
}

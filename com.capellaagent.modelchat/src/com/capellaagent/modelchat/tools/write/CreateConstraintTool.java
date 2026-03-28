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
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.core.data.capellacore.CapellacoreFactory;
import org.polarsys.capella.core.data.capellacore.Constraint;
import org.polarsys.capella.core.data.information.datavalue.DatavalueFactory;
import org.polarsys.capella.core.data.information.datavalue.OpaqueExpression;
import org.polarsys.capella.core.data.capellacore.CapellaElement;

/**
 * Creates an OpaqueExpression constraint on a model element.
 * <p>
 * Constraints in Capella use {@link OpaqueExpression} to hold
 * the constraint body in a specified language (e.g., OCL, English, LinkedText).
 * The constraint is attached to the target element's ownedConstraints list.
 */
public class CreateConstraintTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "create_constraint";
    private static final String DESCRIPTION =
            "Creates an OpaqueExpression constraint on a model element.";

    public CreateConstraintTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("element_uuid",
                "UUID of the element to attach the constraint to"));
        params.add(ToolParameter.requiredString("body",
                "The constraint expression body text"));
        params.add(ToolParameter.optionalString("name",
                "Optional name for the constraint"));
        params.add(ToolParameter.optionalString("language",
                "Expression language (default: English). Common values: English, OCL, LinkedText"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String elementUuid = getRequiredString(parameters, "element_uuid");
        String body = getRequiredString(parameters, "body");
        String rawName = getOptionalString(parameters, "name", null);
        String language = getOptionalString(parameters, "language", "English");

        // Sanitize
        String name = null;
        try {
            if (rawName != null) {
                name = InputValidator.sanitizeName(rawName);
            }
            body = InputValidator.sanitizeDescription(body);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Input validation failed: " + e.getMessage());
        }

        try {
            Session session = getActiveSession();

            EObject element = resolveElementByUuid(elementUuid);
            if (element == null) {
                return ToolResult.error("Element not found: " + elementUuid);
            }

            if (!(element instanceof CapellaElement)) {
                return ToolResult.error("Element does not support constraints (type: "
                        + element.eClass().getName() + ")");
            }

            CapellaElement capellaElement = (CapellaElement) element;
            final String constraintName = name;
            final String constraintBody = body;
            final String constraintLanguage = language;
            final Constraint[] created = new Constraint[1];

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Create constraint on '" + getElementName(element) + "'") {
                @Override
                protected void doExecute() {
                    // Create the Constraint
                    Constraint constraint = CapellacoreFactory.eINSTANCE.createConstraint();
                    if (constraintName != null) {
                        constraint.setName(constraintName);
                    }

                    // Create the OpaqueExpression as the constraint's value specification
                    OpaqueExpression opaqueExpr = DatavalueFactory.eINSTANCE.createOpaqueExpression();
                    opaqueExpr.getLanguages().add(constraintLanguage);
                    opaqueExpr.getBodies().add(constraintBody);

                    constraint.setOwnedSpecification(opaqueExpr);

                    // Attach to the element
                    capellaElement.getOwnedConstraints().add(constraint);

                    created[0] = constraint;
                }
            });

            if (created[0] == null) {
                return ToolResult.error("Failed to create constraint");
            }

            getModelService().invalidateCache(session);

            JsonObject response = new JsonObject();
            response.addProperty("status", "created");
            response.addProperty("constraint_id", getElementId(created[0]));
            response.addProperty("constraint_name",
                    constraintName != null ? constraintName : "(unnamed)");
            response.addProperty("body", constraintBody);
            response.addProperty("language", constraintLanguage);
            response.addProperty("attached_to_name", getElementName(element));
            response.addProperty("attached_to_id", getElementId(element));
            response.addProperty("attached_to_type", element.eClass().getName());

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to create constraint: " + e.getMessage());
        }
    }
}

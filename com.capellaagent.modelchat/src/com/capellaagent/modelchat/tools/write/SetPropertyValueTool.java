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
import org.polarsys.capella.core.data.capellacore.AbstractPropertyValue;
import org.polarsys.capella.core.data.capellacore.CapellacoreFactory;
import org.polarsys.capella.core.data.capellacore.StringPropertyValue;

/**
 * Sets or creates a StringPropertyValue on a model element.
 */
public class SetPropertyValueTool extends AbstractCapellaTool {

    public SetPropertyValueTool() {
        super("set_property_value",
                "Sets a string property value on a model element.",
                ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("element_uuid",
                "UUID of the element"));
        params.add(ToolParameter.requiredString("property_name",
                "Name of the property value"));
        params.add(ToolParameter.requiredString("property_value",
                "Value to set"));
        return params;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String elementUuid = getRequiredString(parameters, "element_uuid");
        String propertyName = getRequiredString(parameters, "property_name");
        String propertyValue = getRequiredString(parameters, "property_value");

        try {
            elementUuid = InputValidator.validateUuid(elementUuid);
            propertyName = InputValidator.sanitizeName(propertyName);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Validation failed: " + e.getMessage());
        }

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            EObject element = resolveElementByUuid(elementUuid);
            if (element == null) {
                return ToolResult.error("Element not found: " + elementUuid);
            }

            final String pvName = propertyName;
            final String pvValue = propertyValue;
            final boolean[] wasExisting = {false};

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Set property '" + propertyName + "'") {
                @Override
                protected void doExecute() {
                    // Try to find existing property value
                    try {
                        List<AbstractPropertyValue> ownedPVs = (List<AbstractPropertyValue>)
                                element.getClass().getMethod("getOwnedPropertyValues").invoke(element);
                        for (AbstractPropertyValue pv : ownedPVs) {
                            if (pv instanceof StringPropertyValue && pvName.equals(pv.getName())) {
                                ((StringPropertyValue) pv).setValue(pvValue);
                                wasExisting[0] = true;
                                return;
                            }
                        }
                        // Create new StringPropertyValue
                        StringPropertyValue newPV = CapellacoreFactory.eINSTANCE.createStringPropertyValue();
                        newPV.setName(pvName);
                        newPV.setValue(pvValue);
                        ownedPVs.add(newPV);
                    } catch (Exception e) {
                        throw new RuntimeException("Cannot set property value: " + e.getMessage(), e);
                    }
                }
            });

            modelService.invalidateCache(session);

            JsonObject response = new JsonObject();
            response.addProperty("status", wasExisting[0] ? "updated" : "created");
            response.addProperty("element_name", getElementName(element));
            response.addProperty("property_name", pvName);
            response.addProperty("property_value", pvValue);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to set property value: " + e.getMessage());
        }
    }
}

package com.capellaagent.modelchat.tools.read;

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
import org.polarsys.capella.core.data.capellacore.AbstractPropertyValue;
import org.polarsys.capella.core.data.capellacore.BooleanPropertyValue;
import org.polarsys.capella.core.data.capellacore.EnumerationPropertyValue;
import org.polarsys.capella.core.data.capellacore.FloatPropertyValue;
import org.polarsys.capella.core.data.capellacore.IntegerPropertyValue;
import org.polarsys.capella.core.data.capellacore.PropertyValueGroup;
import org.polarsys.capella.core.data.capellacore.PropertyValuePkg;
import org.polarsys.capella.core.data.capellacore.StringPropertyValue;
import org.polarsys.capella.common.data.modellingcore.ModelElement;

/**
 * Retrieves property values applied to a specific model element.
 */
public class GetPropertyValuesTool extends AbstractCapellaTool {

    public GetPropertyValuesTool() {
        super("get_property_values",
                "Gets property values and groups for a model element.",
                ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("element_uuid",
                "UUID of the element to get property values for"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String uuid = getRequiredString(parameters, "element_uuid");

        try {
            EObject element = resolveElementByUuid(uuid);
            if (element == null) {
                return ToolResult.error("Element not found: " + uuid);
            }

            JsonArray pvArray = new JsonArray();

            // Check owned property values via reflection (CapellaElement has getOwnedPropertyValues)
            try {
                @SuppressWarnings("unchecked")
                List<AbstractPropertyValue> ownedPVs = (List<AbstractPropertyValue>)
                        element.getClass().getMethod("getOwnedPropertyValues").invoke(element);
                for (AbstractPropertyValue pv : ownedPVs) {
                    pvArray.add(serializePropertyValue(pv));
                }
            } catch (Exception e) { /* element may not support property values */ }

            // Check applied property values
            try {
                @SuppressWarnings("unchecked")
                List<AbstractPropertyValue> appliedPVs = (List<AbstractPropertyValue>)
                        element.getClass().getMethod("getAppliedPropertyValues").invoke(element);
                for (AbstractPropertyValue pv : appliedPVs) {
                    pvArray.add(serializePropertyValue(pv));
                }
            } catch (Exception e) { /* skip */ }

            // Check property value groups
            JsonArray groupsArray = new JsonArray();
            try {
                @SuppressWarnings("unchecked")
                List<PropertyValueGroup> groups = (List<PropertyValueGroup>)
                        element.getClass().getMethod("getOwnedPropertyValueGroups").invoke(element);
                for (PropertyValueGroup group : groups) {
                    JsonObject groupObj = new JsonObject();
                    groupObj.addProperty("name", getElementName(group));
                    groupObj.addProperty("id", getElementId(group));
                    JsonArray groupPVs = new JsonArray();
                    for (AbstractPropertyValue pv : group.getOwnedPropertyValues()) {
                        groupPVs.add(serializePropertyValue(pv));
                    }
                    groupObj.add("property_values", groupPVs);
                    groupsArray.add(groupObj);
                }
            } catch (Exception e) { /* skip */ }

            JsonObject response = new JsonObject();
            response.addProperty("element_name", getElementName(element));
            response.addProperty("element_id", getElementId(element));
            response.add("property_values", pvArray);
            response.add("property_value_groups", groupsArray);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to get property values: " + e.getMessage());
        }
    }

    private JsonObject serializePropertyValue(AbstractPropertyValue pv) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", getElementName(pv));
        obj.addProperty("id", getElementId(pv));

        if (pv instanceof StringPropertyValue) {
            obj.addProperty("type", "string");
            obj.addProperty("value", ((StringPropertyValue) pv).getValue());
        } else if (pv instanceof IntegerPropertyValue) {
            obj.addProperty("type", "integer");
            obj.addProperty("value", ((IntegerPropertyValue) pv).getValue());
        } else if (pv instanceof FloatPropertyValue) {
            obj.addProperty("type", "float");
            obj.addProperty("value", ((FloatPropertyValue) pv).getValue());
        } else if (pv instanceof BooleanPropertyValue) {
            obj.addProperty("type", "boolean");
            obj.addProperty("value", ((BooleanPropertyValue) pv).isValue());
        } else if (pv instanceof EnumerationPropertyValue) {
            obj.addProperty("type", "enumeration");
            EnumerationPropertyValue epv = (EnumerationPropertyValue) pv;
            obj.addProperty("value", epv.getValue() != null ? getElementName(epv.getValue()) : "");
        } else {
            obj.addProperty("type", pv.eClass().getName());
            obj.addProperty("value", "");
        }

        return obj;
    }
}

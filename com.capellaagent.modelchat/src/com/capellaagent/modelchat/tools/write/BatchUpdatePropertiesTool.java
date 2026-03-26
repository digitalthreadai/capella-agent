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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.core.data.capellacore.AbstractPropertyValue;
import org.polarsys.capella.core.data.capellacore.CapellacoreFactory;
import org.polarsys.capella.core.data.capellacore.StringPropertyValue;

/**
 * Updates a property on multiple elements in a single transaction.
 */
public class BatchUpdatePropertiesTool extends AbstractCapellaTool {

    public BatchUpdatePropertiesTool() {
        super("batch_update_properties",
                "Sets a property on multiple elements in one transaction.",
                ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("element_uuids",
                "Comma-separated UUIDs of elements to update"));
        params.add(ToolParameter.requiredString("property_name",
                "Property name to set (e.g. 'name', 'description', or a custom PV name)"));
        params.add(ToolParameter.requiredString("property_value",
                "Value to set"));
        return params;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String uuidsStr = getRequiredString(parameters, "element_uuids");
        String propertyName = getRequiredString(parameters, "property_name");
        String propertyValue = getRequiredString(parameters, "property_value");

        String[] uuids = uuidsStr.split(",");
        if (uuids.length == 0) {
            return ToolResult.error("No UUIDs provided");
        }

        try {
            propertyName = InputValidator.sanitizeName(propertyName);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Invalid property name: " + e.getMessage());
        }

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            // Resolve all elements first
            List<EObject> elements = new ArrayList<>();
            JsonArray errors = new JsonArray();
            for (String uuid : uuids) {
                String trimmed = uuid.trim();
                if (trimmed.isEmpty()) continue;
                EObject el = resolveElementByUuid(trimmed);
                if (el == null) {
                    errors.add("Not found: " + trimmed);
                } else {
                    elements.add(el);
                }
            }

            if (elements.isEmpty()) {
                return ToolResult.error("No valid elements found");
            }

            final String propName = propertyName;
            final String propValue = propertyValue;
            final int[] updatedCount = {0};

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Batch update '" + propertyName + "'") {
                @Override
                protected void doExecute() {
                    for (EObject el : elements) {
                        boolean set = false;
                        // Try structural feature first (name, description)
                        EStructuralFeature feature = el.eClass().getEStructuralFeature(propName);
                        if (feature != null && !feature.isMany()) {
                            try {
                                el.eSet(feature, propValue);
                                set = true;
                            } catch (Exception e) { /* type mismatch */ }
                        }

                        // Fall back to property values
                        if (!set) {
                            try {
                                List<AbstractPropertyValue> pvs = (List<AbstractPropertyValue>)
                                        el.getClass().getMethod("getOwnedPropertyValues").invoke(el);
                                boolean found = false;
                                for (AbstractPropertyValue pv : pvs) {
                                    if (pv instanceof StringPropertyValue && propName.equals(pv.getName())) {
                                        ((StringPropertyValue) pv).setValue(propValue);
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found) {
                                    StringPropertyValue newPV = CapellacoreFactory.eINSTANCE
                                            .createStringPropertyValue();
                                    newPV.setName(propName);
                                    newPV.setValue(propValue);
                                    pvs.add(newPV);
                                }
                                set = true;
                            } catch (Exception e) { /* skip */ }
                        }

                        if (set) {
                            updatedCount[0]++;
                        }
                    }
                }
            });

            modelService.invalidateCache(session);

            JsonObject response = new JsonObject();
            response.addProperty("status", "updated");
            response.addProperty("updated_count", updatedCount[0]);
            response.addProperty("total_requested", elements.size());
            response.addProperty("property_name", propName);
            response.addProperty("property_value", propValue);
            if (errors.size() > 0) {
                response.add("errors", errors);
            }
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to batch update: " + e.getMessage());
        }
    }
}

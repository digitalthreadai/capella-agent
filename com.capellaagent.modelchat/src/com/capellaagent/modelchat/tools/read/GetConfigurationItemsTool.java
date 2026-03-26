package com.capellaagent.modelchat.tools.read;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.capella.CapellaModelService;
import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.core.data.epbs.ConfigurationItem;
import org.polarsys.capella.core.data.epbs.EPBSArchitecture;

/**
 * Lists configuration items from the EPBS architecture layer.
 */
public class GetConfigurationItemsTool extends AbstractCapellaTool {

    public GetConfigurationItemsTool() {
        super("get_configuration_items",
                "Lists EPBS configuration items.",
                ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        return List.of();
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            // EPBS is a special architecture; try to get it
            EObject epbs = null;
            try {
                var se = modelService.getSystemEngineering(session);
                for (EObject arch : se.getOwnedArchitectures()) {
                    if (arch instanceof EPBSArchitecture) {
                        epbs = arch;
                        break;
                    }
                }
            } catch (Exception e) {
                return ToolResult.error("Cannot access EPBS layer: " + e.getMessage());
            }

            if (epbs == null) {
                return ToolResult.error("No EPBS architecture found in this model");
            }

            JsonArray results = new JsonArray();
            Iterator<EObject> it = epbs.eAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();
                if (obj instanceof ConfigurationItem) {
                    ConfigurationItem ci = (ConfigurationItem) obj;
                    JsonObject item = new JsonObject();
                    item.addProperty("name", getElementName(ci));
                    item.addProperty("id", getElementId(ci));
                    item.addProperty("kind", ci.getItemIdentifier() != null
                            ? ci.getItemIdentifier() : "");
                    item.addProperty("type", ci.eClass().getName());
                    results.add(item);
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("count", results.size());
            response.add("configuration_items", results);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to get configuration items: " + e.getMessage());
        }
    }
}

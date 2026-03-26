package com.capellaagent.modelchat.tools.export_;

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

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;

/**
 * Generates a summary of the model's current state for change tracking.
 * Since real diff requires version comparison, this provides a snapshot
 * of element counts and recent modifications based on resource state.
 */
public class GenerateDiffReportTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public GenerateDiffReportTool() {
        super("generate_diff_report",
                "Generates a model state summary for change tracking.",
                ToolCategory.EXPORT);
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

            JsonArray resourceStates = new JsonArray();
            int totalElements = 0;
            int modifiedResources = 0;

            for (Resource resource : session.getSemanticResources()) {
                JsonObject resObj = new JsonObject();
                resObj.addProperty("uri", resource.getURI().toString());
                resObj.addProperty("is_modified", resource.isModified());
                resObj.addProperty("is_loaded", resource.isLoaded());

                int elemCount = 0;
                Iterator<EObject> it = resource.getAllContents();
                while (it.hasNext()) {
                    it.next();
                    elemCount++;
                }
                resObj.addProperty("element_count", elemCount);
                totalElements += elemCount;

                if (resource.isModified()) {
                    modifiedResources++;
                }

                resourceStates.add(resObj);
            }

            // Count elements per layer
            JsonArray layerCounts = new JsonArray();
            for (String layer : VALID_LAYERS) {
                try {
                    var arch = modelService.getArchitecture(session, layer);
                    int count = 0;
                    Iterator<EObject> it = arch.eAllContents();
                    while (it.hasNext()) {
                        EObject obj = it.next();
                        if (obj instanceof AbstractNamedElement) {
                            count++;
                        }
                    }
                    JsonObject lc = new JsonObject();
                    lc.addProperty("layer", layer);
                    lc.addProperty("named_element_count", count);
                    layerCounts.add(lc);
                } catch (Exception e) { /* skip */ }
            }

            // Check command stack for undo history
            TransactionalEditingDomain domain = session.getTransactionalEditingDomain();
            boolean canUndo = domain != null && domain.getCommandStack().canUndo();
            boolean canRedo = domain != null && domain.getCommandStack().canRedo();

            JsonObject response = new JsonObject();
            response.addProperty("total_elements", totalElements);
            response.addProperty("resource_count", resourceStates.size());
            response.addProperty("modified_resources", modifiedResources);
            response.addProperty("can_undo", canUndo);
            response.addProperty("can_redo", canRedo);
            response.add("resources", resourceStates);
            response.add("layer_counts", layerCounts);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to generate diff report: " + e.getMessage());
        }
    }
}

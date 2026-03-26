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
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.pa.PhysicalComponent;
import org.polarsys.capella.core.data.pa.PhysicalComponentNature;
import org.polarsys.capella.core.data.pa.deployment.PartDeploymentLink;

/**
 * Shows deployment mapping of behavior components to node components in PA.
 */
public class GetDeploymentMappingTool extends AbstractCapellaTool {

    public GetDeploymentMappingTool() {
        super("get_deployment_mapping",
                "Shows PA behavior-to-node deployment mapping.",
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
            BlockArchitecture arch = modelService.getArchitecture(session, "pa");

            JsonArray nodeComponents = new JsonArray();
            JsonArray behaviorComponents = new JsonArray();
            JsonArray deployments = new JsonArray();

            Iterator<EObject> it = arch.eAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();
                if (obj instanceof PhysicalComponent) {
                    PhysicalComponent pc = (PhysicalComponent) obj;
                    JsonObject item = new JsonObject();
                    item.addProperty("name", getElementName(pc));
                    item.addProperty("id", getElementId(pc));
                    item.addProperty("nature", pc.getNature() != null
                            ? pc.getNature().getName() : "UNSET");

                    if (pc.getNature() == PhysicalComponentNature.NODE) {
                        nodeComponents.add(item);
                    } else if (pc.getNature() == PhysicalComponentNature.BEHAVIOR) {
                        behaviorComponents.add(item);
                    }
                }
                if (obj instanceof PartDeploymentLink) {
                    PartDeploymentLink link = (PartDeploymentLink) obj;
                    JsonObject dep = new JsonObject();
                    dep.addProperty("deployed",
                            link.getDeployedElement() != null
                                    ? getElementName(link.getDeployedElement()) : "");
                    dep.addProperty("location",
                            link.getLocation() != null
                                    ? getElementName(link.getLocation()) : "");
                    deployments.add(dep);
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("node_count", nodeComponents.size());
            response.addProperty("behavior_count", behaviorComponents.size());
            response.addProperty("deployment_count", deployments.size());
            response.add("node_components", nodeComponents);
            response.add("behavior_components", behaviorComponents);
            response.add("deployments", deployments);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to get deployment mapping: " + e.getMessage());
        }
    }
}

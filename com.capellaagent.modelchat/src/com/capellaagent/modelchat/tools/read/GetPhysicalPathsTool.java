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
import org.polarsys.capella.core.data.cs.PhysicalLink;
import org.polarsys.capella.core.data.cs.PhysicalPath;
import org.polarsys.capella.core.data.cs.PhysicalPathInvolvement;

/**
 * Lists physical paths and their involved links in a layer.
 */
public class GetPhysicalPathsTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public GetPhysicalPathsTool() {
        super("get_physical_paths",
                "Lists physical paths with involved links.",
                ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalEnum("layer",
                "Architecture layer (default: pa)",
                VALID_LAYERS, "pa"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getOptionalString(parameters, "layer", "pa").toLowerCase();

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();
            BlockArchitecture arch = modelService.getArchitecture(session, layer);

            JsonArray results = new JsonArray();
            Iterator<EObject> it = arch.eAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();
                if (obj instanceof PhysicalPath) {
                    PhysicalPath path = (PhysicalPath) obj;
                    JsonObject pathObj = new JsonObject();
                    pathObj.addProperty("name", getElementName(path));
                    pathObj.addProperty("id", getElementId(path));

                    JsonArray linksArray = new JsonArray();
                    for (PhysicalPathInvolvement involvement : path.getOwnedPhysicalPathInvolvements()) {
                        EObject involved = involvement.getInvolved();
                        if (involved instanceof PhysicalLink) {
                            PhysicalLink link = (PhysicalLink) involved;
                            JsonObject linkObj = new JsonObject();
                            linkObj.addProperty("name", getElementName(link));
                            String source = link.getLinkEnds().size() > 0
                                    ? getElementName(link.getLinkEnds().get(0).eContainer()) : "";
                            String target = link.getLinkEnds().size() > 1
                                    ? getElementName(link.getLinkEnds().get(1).eContainer()) : "";
                            linkObj.addProperty("source", source);
                            linkObj.addProperty("target", target);
                            linksArray.add(linkObj);
                        }
                    }
                    pathObj.add("involved_links", linksArray);
                    results.add(pathObj);
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("count", results.size());
            response.add("physical_paths", results);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to get physical paths: " + e.getMessage());
        }
    }
}

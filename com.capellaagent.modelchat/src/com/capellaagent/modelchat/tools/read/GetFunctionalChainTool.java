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
import org.polarsys.capella.core.data.fa.FunctionalChain;
import org.polarsys.capella.core.data.fa.FunctionalChainInvolvement;
import org.polarsys.capella.core.data.fa.FunctionalChainInvolvementFunction;
import org.polarsys.capella.core.data.fa.FunctionalChainInvolvementLink;
import org.polarsys.capella.common.data.modellingcore.ModelElement;

/**
 * Retrieves functional chain details by UUID or name search.
 * <p>
 * Resolves a FunctionalChain element and returns its involvements (functions
 * and exchanges) in sequence order.
 */
public class GetFunctionalChainTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "get_functional_chain";
    private static final String DESCRIPTION =
            "Gets functional chain details including involved functions and exchanges.";

    public GetFunctionalChainTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalString("uuid",
                "UUID of the functional chain to retrieve"));
        params.add(ToolParameter.optionalString("name",
                "Name to search for (partial match). Used if uuid is not provided."));
        params.add(ToolParameter.optionalEnum("layer",
                "Architecture layer to search in (required if using name search)",
                List.of("oa", "sa", "la", "pa"), null));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String uuid = getOptionalString(parameters, "uuid", null);
        String name = getOptionalString(parameters, "name", null);
        String layer = getOptionalString(parameters, "layer", null);

        if ((uuid == null || uuid.isBlank()) && (name == null || name.isBlank())) {
            return ToolResult.error("Either 'uuid' or 'name' parameter must be provided");
        }

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();

            FunctionalChain chain = null;

            // Strategy 1: Direct UUID lookup
            if (uuid != null && !uuid.isBlank()) {
                EObject element = resolveElementByUuid(uuid);
                if (element == null) {
                    return ToolResult.error("Element not found with UUID: " + uuid);
                }
                if (!(element instanceof FunctionalChain)) {
                    return ToolResult.error("Element is not a FunctionalChain (type: "
                            + element.eClass().getName() + ")");
                }
                chain = (FunctionalChain) element;
            }

            // Strategy 2: Name search within a layer
            if (chain == null && name != null && !name.isBlank()) {
                if (layer == null || layer.isBlank()) {
                    // Search all layers
                    for (String l : List.of("oa", "sa", "la", "pa")) {
                        chain = findChainByName(session, modelService, l, name);
                        if (chain != null) break;
                    }
                } else {
                    chain = findChainByName(session, modelService, layer.toLowerCase(), name);
                }

                if (chain == null) {
                    return ToolResult.error("No FunctionalChain found matching name: " + name);
                }
            }

            // Build response
            JsonObject response = new JsonObject();
            response.addProperty("chain_name", getElementName(chain));
            response.addProperty("chain_id", getElementId(chain));
            response.addProperty("layer", modelService.detectLayer(chain));
            response.addProperty("description", truncate(getElementDescription(chain), 500));

            // Get involvements
            JsonArray steps = new JsonArray();
            List<FunctionalChainInvolvement> involvements = chain.getOwnedFunctionalChainInvolvements();
            if (involvements != null) {
                for (FunctionalChainInvolvement involvement : involvements) {
                    JsonObject step = new JsonObject();

                    if (involvement instanceof FunctionalChainInvolvementFunction) {
                        FunctionalChainInvolvementFunction fcif =
                                (FunctionalChainInvolvementFunction) involvement;
                        EObject involved = fcif.getInvolved();
                        step.addProperty("type", "function");
                        if (involved != null) {
                            step.addProperty("name", getElementName(involved));
                            step.addProperty("id", getElementId(involved));
                            step.addProperty("element_type", involved.eClass().getName());
                        }
                    } else if (involvement instanceof FunctionalChainInvolvementLink) {
                        FunctionalChainInvolvementLink fcil =
                                (FunctionalChainInvolvementLink) involvement;
                        EObject involved = fcil.getInvolved();
                        step.addProperty("type", "exchange");
                        if (involved != null) {
                            step.addProperty("name", getElementName(involved));
                            step.addProperty("id", getElementId(involved));
                            step.addProperty("element_type", involved.eClass().getName());
                        }
                    } else {
                        // Generic involvement
                        EObject involved = involvement.getInvolved();
                        step.addProperty("type", involvement.eClass().getName());
                        if (involved != null) {
                            step.addProperty("name", getElementName(involved));
                            step.addProperty("id", getElementId(involved));
                        }
                    }

                    steps.add(step);
                }
            }

            response.addProperty("step_count", steps.size());
            response.add("steps", steps);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to get functional chain: " + e.getMessage());
        }
    }

    /**
     * Searches for a FunctionalChain by name within a specific layer.
     */
    private FunctionalChain findChainByName(Session session, CapellaModelService modelService,
                                              String layer, String name) {
        try {
            var architecture = modelService.getArchitecture(session, layer);
            String lowerName = name.toLowerCase();
            Iterator<EObject> allContents = architecture.eAllContents();
            while (allContents.hasNext()) {
                EObject obj = allContents.next();
                if (obj instanceof FunctionalChain) {
                    String chainName = getElementName(obj);
                    if (chainName != null && chainName.toLowerCase().contains(lowerName)) {
                        return (FunctionalChain) obj;
                    }
                }
            }
        } catch (Exception e) {
            // Layer may not exist; skip
        }
        return null;
    }
}

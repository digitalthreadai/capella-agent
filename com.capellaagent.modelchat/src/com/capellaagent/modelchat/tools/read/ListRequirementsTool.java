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

// PLACEHOLDER imports for Capella Requirements Viewpoint (VP)
// import org.polarsys.capella.vp.requirements.CapellaRequirements.CapellaOutgoingRelation;
// import org.polarsys.capella.vp.requirements.CapellaRequirements.CapellaIncomingRelation;
// import org.polarsys.kitalpha.vp.requirements.Requirements.Requirement;
// import org.polarsys.kitalpha.vp.requirements.Requirements.RequirementsPackage;

/**
 * Lists requirements from the Capella Requirements viewpoint.
 * <p>
 * Can list all requirements in the model or filter to those linked to a specific
 * model element. Returns requirement name, ID, text preview, and linked elements.
 *
 * <h3>Tool Specification</h3>
 * <ul>
 *   <li><b>Name:</b> list_requirements</li>
 *   <li><b>Category:</b> model_read</li>
 *   <li><b>Parameters:</b>
 *     <ul>
 *       <li>{@code linked_to_uuid} (string, optional) - If specified, returns only requirements
 *           linked to the element with this UUID</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class ListRequirementsTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "list_requirements";
    private static final String DESCRIPTION =
            "Lists requirements from the Requirements viewpoint. "
            + "Optionally filters by linked model element. "
            + "Returns requirement name, ID, text, and linked element references.";

    private static final int MAX_REQUIREMENTS = 200;

    public ListRequirementsTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalString("linked_to_uuid",
                "Filter to requirements linked to the element with this UUID"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String linkedToUuid = getOptionalString(parameters, "linked_to_uuid", null);

        try {
            // PLACEHOLDER: Capella Requirements VP API
            //
            // Requirements viewpoint access approach:
            //
            // 1. Find all Requirement instances in the model:
            //    Session session = getActiveSession();
            //    for (Resource res : session.getSemanticResources()) {
            //        TreeIterator<EObject> it = res.getAllContents();
            //        while (it.hasNext()) {
            //            EObject obj = it.next();
            //            if (obj instanceof Requirement req) {
            //                allRequirements.add(req);
            //            }
            //        }
            //    }
            //
            // 2. If linkedToUuid is specified, filter to requirements that have a
            //    relation (CapellaOutgoingRelation or CapellaIncomingRelation) pointing
            //    to the element with the given UUID:
            //    EObject targetElement = resolveByUuid(linkedToUuid);
            //    for (Requirement req : allRequirements) {
            //        for (AbstractRelation rel : req.getOwnedRelations()) {
            //            if (rel instanceof CapellaOutgoingRelation outRel) {
            //                if (outRel.getTarget() == targetElement) {
            //                    filteredReqs.add(req);
            //                }
            //            }
            //        }
            //    }
            //
            // 3. For each requirement, extract:
            //    - req.getReqIFIdentifier() or req.getId() for the requirement ID
            //    - req.getReqIFText() or req.getReqIFName() for the text
            //    - req.getOwnedRelations() for linked elements

            EObject linkedElement = null;
            if (linkedToUuid != null && !linkedToUuid.isBlank()) {
                linkedElement = resolveElementByUuid(linkedToUuid);
                if (linkedElement == null) {
                    return ToolResult.error("Linked element not found with UUID: " + linkedToUuid);
                }
            }

            List<EObject> requirements = queryRequirements(linkedElement);

            JsonArray requirementsArray = new JsonArray();
            int count = 0;
            for (EObject reqObj : requirements) {
                if (count >= MAX_REQUIREMENTS) {
                    break;
                }

                JsonObject reqJson = new JsonObject();
                reqJson.addProperty("name", getElementName(reqObj));
                reqJson.addProperty("uuid", getElementId(reqObj));

                // PLACEHOLDER: Extract requirement-specific fields
                // Requirement req = (Requirement) reqObj;
                // reqJson.addProperty("requirement_id", req.getReqIFIdentifier());
                // reqJson.addProperty("text", truncate(req.getReqIFText(), 500));
                reqJson.addProperty("requirement_id", "");
                reqJson.addProperty("text", truncate(getElementDescription(reqObj), 500));

                // Build linked elements array
                JsonArray linkedElements = buildLinkedElements(reqObj);
                reqJson.add("linked_elements", linkedElements);

                requirementsArray.add(reqJson);
                count++;
            }

            JsonObject response = new JsonObject();
            response.addProperty("count", requirementsArray.size());
            response.addProperty("truncated", requirements.size() > MAX_REQUIREMENTS);
            if (linkedToUuid != null) {
                response.addProperty("linked_to_uuid", linkedToUuid);
                if (linkedElement != null) {
                    response.addProperty("linked_to_name", getElementName(linkedElement));
                }
            }
            response.add("requirements", requirementsArray);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to list requirements: " + e.getMessage());
        }
    }

    /**
     * Queries requirements from the model, optionally filtering by linked element.
     *
     * @param linkedElement if non-null, only return requirements linked to this element
     * @return list of requirement EObjects
     */
    private List<EObject> queryRequirements(EObject linkedElement) {
        // PLACEHOLDER: Implement actual Requirements VP query
        return new ArrayList<>();
    }

    /**
     * Builds the array of elements linked to a requirement via relations.
     *
     * @param requirement the requirement EObject
     * @return a JsonArray of linked element summaries
     */
    private JsonArray buildLinkedElements(EObject requirement) {
        JsonArray linked = new JsonArray();
        // PLACEHOLDER: Navigate CapellaOutgoingRelation / CapellaIncomingRelation
        // Requirement req = (Requirement) requirement;
        // for (AbstractRelation rel : req.getOwnedRelations()) {
        //     if (rel instanceof CapellaOutgoingRelation outRel && outRel.getTarget() != null) {
        //         CapellaElement target = outRel.getTarget();
        //         JsonObject linkObj = new JsonObject();
        //         linkObj.addProperty("name", target.getName());
        //         linkObj.addProperty("uuid", target.getId());
        //         linkObj.addProperty("type", target.eClass().getName());
        //         linkObj.addProperty("relation_type", "outgoing");
        //         linked.add(linkObj);
        //     }
        // }
        return linked;
    }
}

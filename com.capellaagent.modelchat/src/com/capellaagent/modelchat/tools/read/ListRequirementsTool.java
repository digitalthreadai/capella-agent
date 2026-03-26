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
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.common.data.modellingcore.ModelElement;

/**
 * Lists requirements from the Capella model.
 * <p>
 * Uses a generic traversal approach that works with or without the Requirements
 * Viewpoint (VP) add-on. Identifies requirement elements by checking if the
 * eClass name contains "Requirement". When the Requirements VP is installed,
 * this catches Requirement, SystemUserRequirement, SystemFunctionalRequirement,
 * etc. When it is not installed, this tool returns an empty list with a note.
 * <p>
 * For each requirement found, extracts available attributes (name, text/description,
 * ReqIF identifier) and linked model elements via outgoing relations.
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
            // Thread safety: read operations should ideally use read-exclusive context
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();

            EObject linkedElement = null;
            if (linkedToUuid != null && !linkedToUuid.isBlank()) {
                linkedElement = resolveElementByUuid(linkedToUuid);
                if (linkedElement == null) {
                    return ToolResult.error("Linked element not found with UUID: " + linkedToUuid);
                }
            }

            List<EObject> requirements = queryRequirements(session, linkedElement);

            JsonArray requirementsArray = new JsonArray();
            int count = 0;
            for (EObject reqObj : requirements) {
                if (count >= MAX_REQUIREMENTS) {
                    break;
                }

                JsonObject reqJson = new JsonObject();
                reqJson.addProperty("name", getElementName(reqObj));
                reqJson.addProperty("uuid", getElementId(reqObj));
                reqJson.addProperty("type", reqObj.eClass().getName());

                // Extract requirement-specific fields via reflection
                // The Requirements VP defines getReqIFIdentifier() and getReqIFText()
                String reqId = getFeatureAsString(reqObj, "ReqIFIdentifier");
                if (reqId == null || reqId.isEmpty()) {
                    reqId = getFeatureAsString(reqObj, "id");
                }
                reqJson.addProperty("requirement_id", reqId != null ? reqId : "");

                String reqText = getFeatureAsString(reqObj, "ReqIFText");
                if (reqText == null || reqText.isEmpty()) {
                    reqText = getElementDescription(reqObj);
                }
                reqJson.addProperty("text", truncate(reqText, 500));

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

            if (requirementsArray.size() == 0 && linkedToUuid == null) {
                response.addProperty("note",
                        "No requirements found. The Requirements Viewpoint add-on may not be "
                        + "installed or the model may not contain requirements.");
            }

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to list requirements: " + e.getMessage());
        }
    }

    /**
     * Queries requirements from the model by traversing all semantic resources
     * and looking for elements whose eClass name contains "Requirement".
     * <p>
     * If {@code linkedElement} is non-null, filters to requirements that have
     * an outgoing relation targeting that element.
     *
     * @param session       the Sirius session
     * @param linkedElement if non-null, only return requirements linked to this element
     * @return list of requirement EObjects
     */
    private List<EObject> queryRequirements(Session session, EObject linkedElement) {
        List<EObject> allRequirements = new ArrayList<>();
        int maxCollect = MAX_REQUIREMENTS + 1;

        for (Resource resource : session.getSemanticResources()) {
            Iterator<EObject> allContents = resource.getAllContents();
            while (allContents.hasNext() && allRequirements.size() < maxCollect) {
                EObject obj = allContents.next();
                String eClassName = obj.eClass().getName();

                // Match requirement elements by eClass name pattern
                if (eClassName.contains("Requirement")
                        && !eClassName.contains("Pkg")
                        && !eClassName.contains("Package")) {

                    if (linkedElement == null) {
                        allRequirements.add(obj);
                    } else {
                        // Check if this requirement has a relation to the linked element
                        if (isLinkedTo(obj, linkedElement)) {
                            allRequirements.add(obj);
                        }
                    }
                }
            }
            if (allRequirements.size() >= maxCollect) break;
        }

        return allRequirements;
    }

    /**
     * Checks if a requirement has any outgoing relation that targets the given element.
     * Examines all non-containment EReferences for a match.
     *
     * @param requirement   the requirement to check
     * @param targetElement the target element to look for
     * @return true if a link exists
     */
    private boolean isLinkedTo(EObject requirement, EObject targetElement) {
        for (EReference ref : requirement.eClass().getEAllReferences()) {
            if (ref.isContainment()) continue;
            try {
                Object value = requirement.eGet(ref);
                if (value == targetElement) return true;
                if (value instanceof List<?>) {
                    for (Object item : (List<?>) value) {
                        if (item == targetElement) return true;
                    }
                }
            } catch (Exception e) {
                // Skip inaccessible references
            }
        }

        // Also check owned relations (children that are relation objects)
        for (EObject child : requirement.eContents()) {
            String childClassName = child.eClass().getName();
            if (childClassName.contains("Relation") || childClassName.contains("Link")) {
                for (EReference ref : child.eClass().getEAllReferences()) {
                    if (ref.isContainment()) continue;
                    try {
                        Object value = child.eGet(ref);
                        if (value == targetElement) return true;
                    } catch (Exception e) {
                        // Skip
                    }
                }
            }
        }

        return false;
    }

    /**
     * Builds the array of elements linked to a requirement via outgoing relations.
     * Traverses child relation objects looking for non-containment references to
     * model elements.
     *
     * @param requirement the requirement EObject
     * @return a JsonArray of linked element summaries
     */
    private JsonArray buildLinkedElements(EObject requirement) {
        JsonArray linked = new JsonArray();
        int count = 0;

        // Check owned children for relation objects
        for (EObject child : requirement.eContents()) {
            if (count >= 20) break; // Limit linked elements per requirement
            String childClassName = child.eClass().getName();

            if (childClassName.contains("Relation") || childClassName.contains("Link")) {
                // Look for target references in the relation
                for (EReference ref : child.eClass().getEAllReferences()) {
                    if (ref.isContainment()) continue;
                    try {
                        Object value = child.eGet(ref);
                        if (value instanceof EObject) {
                            EObject target = (EObject) value;
                            // Avoid self-references back to the requirement
                            if (target != requirement && target != child) {
                                JsonObject linkObj = new JsonObject();
                                linkObj.addProperty("name", getElementName(target));
                                linkObj.addProperty("uuid", getElementId(target));
                                linkObj.addProperty("type", target.eClass().getName());
                                linkObj.addProperty("relation_type",
                                        childClassName.contains("Outgoing") ? "outgoing" : "incoming");
                                linked.add(linkObj);
                                count++;
                            }
                        }
                    } catch (Exception e) {
                        // Skip inaccessible references
                    }
                }
            }
        }

        return linked;
    }

    /**
     * Gets a feature value as a string by feature name, using the EMF reflective API.
     * Returns null if the feature does not exist or has no value.
     *
     * @param element     the EObject
     * @param featureName the EStructuralFeature name
     * @return the value as a string, or null
     */
    private String getFeatureAsString(EObject element, String featureName) {
        EStructuralFeature feature = element.eClass().getEStructuralFeature(featureName);
        if (feature != null) {
            Object value = element.eGet(feature);
            return value != null ? value.toString() : null;
        }
        return null;
    }
}

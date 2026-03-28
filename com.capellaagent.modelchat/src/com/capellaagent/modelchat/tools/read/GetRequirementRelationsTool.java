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
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.common.data.modellingcore.AbstractTrace;
import org.polarsys.capella.common.data.modellingcore.TraceableElement;
import org.polarsys.capella.core.data.capellacore.CapellaElement;

/**
 * Gets requirement-to-element trace links from the Capella model.
 * <p>
 * Traverses the model looking for Requirement elements and their
 * AbstractTrace links (incoming/outgoing) which represent requirement
 * allocations, refinements, and traceability relationships.
 * <p>
 * Supports both Capella built-in requirements and the Requirements VP
 * (Viewpoint) extension when available.
 */
public class GetRequirementRelationsTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "get_requirement_relations";
    private static final String DESCRIPTION =
            "Gets requirement-to-element trace links (allocations, refinements, coverage).";

    public GetRequirementRelationsTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalString("element_uuid",
                "UUID of a specific element to get its requirement traces. "
                + "If omitted, returns all requirement relations in the project."));
        params.add(ToolParameter.optionalInteger("max_results",
                "Maximum number of relations to return (default: 200, max: 500)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String elementUuid = getOptionalString(parameters, "element_uuid", null);
        int maxResults = getOptionalInt(parameters, "max_results", 200);
        maxResults = Math.max(1, Math.min(maxResults, 500));

        try {
            Session session = getActiveSession();

            JsonArray relationsArray = new JsonArray();

            if (elementUuid != null && !elementUuid.isBlank()) {
                // Get relations for a specific element
                EObject element = resolveElementByUuid(elementUuid);
                if (element == null) {
                    return ToolResult.error("Element not found: " + elementUuid);
                }

                collectTracesForElement(element, relationsArray, maxResults);
            } else {
                // Scan all layers for requirement traces
                CapellaModelService modelService = getModelService();
                for (String layer : List.of("oa", "sa", "la", "pa")) {
                    try {
                        EObject arch = modelService.getArchitecture(session, layer);
                        Iterator<EObject> allContents = arch.eAllContents();
                        while (allContents.hasNext() && relationsArray.size() < maxResults) {
                            EObject obj = allContents.next();
                            // Check if the element is a requirement (by class name pattern)
                            String className = obj.eClass().getName();
                            if (className.contains("Requirement")) {
                                collectTracesForElement(obj, relationsArray, maxResults);
                            }
                        }
                    } catch (Exception e) {
                        // Layer might not exist, skip it
                    }
                }

                // Also scan the top-level project for requirement modules
                // Requirements may live outside architecture layers
                for (EObject root : session.getSemanticResources().iterator().next().getContents()) {
                    Iterator<EObject> allContents = root.eAllContents();
                    while (allContents.hasNext() && relationsArray.size() < maxResults) {
                        EObject obj = allContents.next();
                        String className = obj.eClass().getName();
                        if (className.contains("Requirement")) {
                            collectTracesForElement(obj, relationsArray, maxResults);
                        }
                    }
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("relation_count", relationsArray.size());
            response.addProperty("truncated", relationsArray.size() >= maxResults);
            if (elementUuid != null) {
                response.addProperty("scoped_to_element", elementUuid);
            }
            response.add("relations", relationsArray);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to get requirement relations: " + e.getMessage());
        }
    }

    /**
     * Collects incoming and outgoing trace links for a given element.
     */
    private void collectTracesForElement(EObject element, JsonArray relationsArray, int maxResults) {
        if (!(element instanceof TraceableElement)) {
            return;
        }

        TraceableElement traceable = (TraceableElement) element;

        // Outgoing traces (this element -> target)
        List<AbstractTrace> outgoing = traceable.getOutgoingTraces();
        if (outgoing != null) {
            for (AbstractTrace trace : outgoing) {
                if (relationsArray.size() >= maxResults) return;

                TraceableElement target = trace.getTargetElement();
                if (target == null) continue;

                JsonObject rel = new JsonObject();
                rel.addProperty("trace_type", trace.eClass().getName());
                rel.addProperty("trace_id", getElementId(trace));
                rel.addProperty("direction", "outgoing");

                // Source (this element)
                JsonObject sourceObj = new JsonObject();
                sourceObj.addProperty("name", getElementName(element));
                sourceObj.addProperty("id", getElementId(element));
                sourceObj.addProperty("type", element.eClass().getName());
                rel.add("source", sourceObj);

                // Target
                JsonObject targetObj = new JsonObject();
                targetObj.addProperty("name", getElementName((EObject) target));
                targetObj.addProperty("id", getElementId((EObject) target));
                targetObj.addProperty("type", ((EObject) target).eClass().getName());
                rel.add("target", targetObj);

                relationsArray.add(rel);
            }
        }

        // Incoming traces (source -> this element)
        List<AbstractTrace> incoming = traceable.getIncomingTraces();
        if (incoming != null) {
            for (AbstractTrace trace : incoming) {
                if (relationsArray.size() >= maxResults) return;

                TraceableElement source = trace.getSourceElement();
                if (source == null) continue;

                JsonObject rel = new JsonObject();
                rel.addProperty("trace_type", trace.eClass().getName());
                rel.addProperty("trace_id", getElementId(trace));
                rel.addProperty("direction", "incoming");

                // Source
                JsonObject sourceObj = new JsonObject();
                sourceObj.addProperty("name", getElementName((EObject) source));
                sourceObj.addProperty("id", getElementId((EObject) source));
                sourceObj.addProperty("type", ((EObject) source).eClass().getName());
                rel.add("source", sourceObj);

                // Target (this element)
                JsonObject targetObj = new JsonObject();
                targetObj.addProperty("name", getElementName(element));
                targetObj.addProperty("id", getElementId(element));
                targetObj.addProperty("type", element.eClass().getName());
                rel.add("target", targetObj);

                relationsArray.add(rel);
            }
        }
    }
}

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
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;

// PLACEHOLDER imports for Capella metamodel
// import org.polarsys.capella.core.data.capellacore.NamedElement;
// import org.polarsys.capella.core.data.fa.AbstractFunction;
// import org.polarsys.capella.core.data.fa.ComponentFunctionalAllocation;
// import org.polarsys.capella.core.data.cs.Component;

/**
 * Retrieves comprehensive details for a single model element by its UUID.
 * <p>
 * Returns the element's full name, type, description, all properties, incoming and
 * outgoing relationships, allocated functions (if the element is a component),
 * parent container, and direct children.
 *
 * <h3>Tool Specification</h3>
 * <ul>
 *   <li><b>Name:</b> get_element_details</li>
 *   <li><b>Category:</b> model_read</li>
 *   <li><b>Parameters:</b>
 *     <ul>
 *       <li>{@code uuid} (string, required) - The unique identifier of the element</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class GetElementDetailsTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "get_element_details";
    private static final String DESCRIPTION =
            "Retrieves full details for a model element by UUID, including name, type, "
            + "description, properties, relationships, allocated functions, parent, and children.";

    public GetElementDetailsTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("uuid",
                "The unique identifier (UUID) of the model element to retrieve"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String uuid = getRequiredString(parameters, "uuid");

        if (uuid.isBlank()) {
            return ToolResult.error("Parameter 'uuid' must not be empty");
        }

        try {
            // PLACEHOLDER: Resolve element by UUID from the Capella model
            // EObject element = resolveByUuid(uuid);
            //
            // Resolution approach:
            //   Session session = getActiveSession();
            //   for (Resource resource : session.getSemanticResources()) {
            //       TreeIterator<EObject> it = resource.getAllContents();
            //       while (it.hasNext()) {
            //           EObject obj = it.next();
            //           if (obj instanceof NamedElement ne && uuid.equals(ne.getId())) {
            //               return ne;
            //           }
            //       }
            //   }

            EObject element = resolveElementByUuid(uuid);
            if (element == null) {
                return ToolResult.error("Element not found with UUID: " + uuid);
            }

            JsonObject details = new JsonObject();

            // Basic properties
            details.addProperty("name", getElementName(element));
            details.addProperty("uuid", uuid);
            details.addProperty("type", element.eClass().getName());
            details.addProperty("description", getElementDescription(element));

            // All EAttribute values as a properties map
            JsonObject properties = new JsonObject();
            for (EStructuralFeature feature : element.eClass().getEAllStructuralFeatures()) {
                if (feature instanceof EReference) {
                    continue; // References handled separately
                }
                Object value = element.eGet(feature);
                if (value != null) {
                    properties.addProperty(feature.getName(), value.toString());
                }
            }
            details.add("properties", properties);

            // Incoming relationships (cross-references pointing to this element)
            // PLACEHOLDER: Use ECrossReferenceAdapter for incoming references
            // ECrossReferenceAdapter adapter = ECrossReferenceAdapter.getCrossReferenceAdapter(element);
            // Collection<Setting> inverseRefs = adapter.getInverseReferences(element);
            JsonArray incoming = buildIncomingRelationships(element);
            details.add("incoming_relationships", incoming);

            // Outgoing relationships (references from this element)
            JsonArray outgoing = buildOutgoingRelationships(element);
            details.add("outgoing_relationships", outgoing);

            // Allocated functions (if element is a Component)
            // PLACEHOLDER: Check instanceof Component and get allocated functions
            // if (element instanceof Component comp) {
            //     JsonArray allocatedFunctions = new JsonArray();
            //     for (ComponentFunctionalAllocation alloc : comp.getFunctionalAllocations()) {
            //         AbstractFunction fn = alloc.getFunction();
            //         if (fn != null) {
            //             JsonObject fnObj = new JsonObject();
            //             fnObj.addProperty("name", fn.getName());
            //             fnObj.addProperty("uuid", fn.getId());
            //             allocatedFunctions.add(fnObj);
            //         }
            //     }
            //     details.add("allocated_functions", allocatedFunctions);
            // }
            JsonArray allocatedFunctions = buildAllocatedFunctions(element);
            if (allocatedFunctions.size() > 0) {
                details.add("allocated_functions", allocatedFunctions);
            }

            // Parent container
            EObject parent = element.eContainer();
            if (parent != null) {
                JsonObject parentObj = new JsonObject();
                parentObj.addProperty("name", getElementName(parent));
                parentObj.addProperty("uuid", getElementId(parent));
                parentObj.addProperty("type", parent.eClass().getName());
                details.add("parent", parentObj);
            }

            // Direct children
            JsonArray children = new JsonArray();
            for (EObject child : element.eContents()) {
                JsonObject childObj = new JsonObject();
                childObj.addProperty("name", getElementName(child));
                childObj.addProperty("uuid", getElementId(child));
                childObj.addProperty("type", child.eClass().getName());
                children.add(childObj);
            }
            details.add("children", children);

            return ToolResult.success(details);

        } catch (Exception e) {
            return ToolResult.error("Failed to get element details: " + e.getMessage());
        }
    }

    /**
     * Builds the list of incoming relationships (elements that reference this element).
     *
     * @param element the target element
     * @return a JsonArray of relationship summaries
     */
    private JsonArray buildIncomingRelationships(EObject element) {
        JsonArray incoming = new JsonArray();
        // PLACEHOLDER: Use ECrossReferenceAdapter to find inverse references
        // Each entry should have: source_name, source_uuid, source_type, relationship_type
        return incoming;
    }

    /**
     * Builds the list of outgoing relationships (elements referenced by this element).
     *
     * @param element the source element
     * @return a JsonArray of relationship summaries
     */
    private JsonArray buildOutgoingRelationships(EObject element) {
        JsonArray outgoing = new JsonArray();
        for (EReference ref : element.eClass().getEAllReferences()) {
            if (ref.isContainment()) {
                continue; // Skip containment refs, those are in children
            }
            Object value = element.eGet(ref);
            if (value instanceof EObject target) {
                JsonObject relObj = new JsonObject();
                relObj.addProperty("relationship_type", ref.getName());
                relObj.addProperty("target_name", getElementName(target));
                relObj.addProperty("target_uuid", getElementId(target));
                relObj.addProperty("target_type", target.eClass().getName());
                outgoing.add(relObj);
            } else if (value instanceof List<?> targets) {
                for (Object item : targets) {
                    if (item instanceof EObject target) {
                        JsonObject relObj = new JsonObject();
                        relObj.addProperty("relationship_type", ref.getName());
                        relObj.addProperty("target_name", getElementName(target));
                        relObj.addProperty("target_uuid", getElementId(target));
                        relObj.addProperty("target_type", target.eClass().getName());
                        outgoing.add(relObj);
                    }
                }
            }
        }
        return outgoing;
    }

    /**
     * Builds the list of functions allocated to this element if it is a component.
     *
     * @param element the element to inspect
     * @return a JsonArray of allocated function summaries, empty if not a component
     */
    private JsonArray buildAllocatedFunctions(EObject element) {
        JsonArray functions = new JsonArray();
        // PLACEHOLDER: Capella-specific allocation navigation
        // if (element instanceof Component comp) {
        //     for (ComponentFunctionalAllocation alloc : comp.getFunctionalAllocations()) {
        //         AbstractFunction fn = alloc.getFunction();
        //         if (fn != null) {
        //             JsonObject fnObj = new JsonObject();
        //             fnObj.addProperty("name", fn.getName());
        //             fnObj.addProperty("uuid", fn.getId());
        //             fnObj.addProperty("type", fn.eClass().getName());
        //             functions.add(fnObj);
        //         }
        //     }
        // }
        return functions;
    }
}

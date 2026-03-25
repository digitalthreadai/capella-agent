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

// PLACEHOLDER imports - exact packages depend on Capella version
// import org.polarsys.capella.core.data.oa.OaPackage;
// import org.polarsys.capella.core.data.ctx.CtxPackage;
// import org.polarsys.capella.core.data.la.LaPackage;
// import org.polarsys.capella.core.data.pa.PaPackage;
// import org.polarsys.capella.core.model.helpers.BlockArchitectureExt;

import org.eclipse.emf.ecore.EObject;

/**
 * Lists elements within a specific ARCADIA architecture layer.
 * <p>
 * Supports filtering by element type (functions, components, actors, exchanges,
 * capabilities) and limits result count to avoid overwhelming the LLM context window.
 *
 * <h3>Tool Specification</h3>
 * <ul>
 *   <li><b>Name:</b> list_elements</li>
 *   <li><b>Category:</b> model_read</li>
 *   <li><b>Parameters:</b>
 *     <ul>
 *       <li>{@code layer} (string, required) - Architecture layer: oa, sa, la, or pa</li>
 *       <li>{@code element_type} (string, optional, default "all") - Type filter:
 *           functions, components, actors, exchanges, capabilities, or all</li>
 *       <li>{@code max_results} (integer, optional, default 100) - Maximum elements to return</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class ListElementsTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "list_elements";
    private static final String DESCRIPTION =
            "Lists model elements within a specific ARCADIA architecture layer. "
            + "Returns an array of elements with their name, UUID, type, and description preview.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");
    private static final List<String> VALID_TYPES = List.of(
            "functions", "components", "actors", "exchanges", "capabilities", "all");

    public ListElementsTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("layer",
                "Architecture layer to query: oa (Operational Analysis), "
                + "sa (System Analysis), la (Logical Architecture), pa (Physical Architecture)"));
        params.add(ToolParameter.optionalString("element_type",
                "Type of elements to list: functions, components, actors, exchanges, "
                + "capabilities, or all (default: all)"));
        params.add(ToolParameter.optionalInteger("max_results",
                "Maximum number of elements to return (default: 100, max: 500)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        String elementType = getOptionalString(parameters, "element_type", "all").toLowerCase();
        int maxResults = getOptionalInt(parameters, "max_results", 100);

        // Validate layer parameter
        if (!VALID_LAYERS.contains(layer)) {
            return ToolResult.error("Invalid layer '" + layer
                    + "'. Must be one of: " + String.join(", ", VALID_LAYERS));
        }

        // Validate element_type parameter
        if (!VALID_TYPES.contains(elementType)) {
            return ToolResult.error("Invalid element_type '" + elementType
                    + "'. Must be one of: " + String.join(", ", VALID_TYPES));
        }

        // Clamp max_results
        maxResults = Math.max(1, Math.min(maxResults, 500));

        try {
            // PLACEHOLDER: Capella EMF navigation
            // The actual implementation navigates the Capella model using EMF:
            //
            // 1. Get the active Capella session:
            //    Session session = SessionManager.INSTANCE.getSessions().iterator().next();
            //    Resource semanticResource = session.getSemanticResources().iterator().next();
            //
            // 2. Navigate to the correct architecture layer:
            //    Project project = (Project) semanticResource.getContents().get(0);
            //    SystemEngineering se = ProjectExt.getSystemEngineering(project);
            //    BlockArchitecture architecture = switch (layer) {
            //        case "oa" -> BlockArchitectureExt.getOperationalAnalysis(se);
            //        case "sa" -> BlockArchitectureExt.getSystemAnalysis(se);
            //        case "la" -> BlockArchitectureExt.getLogicalArchitecture(se);
            //        case "pa" -> BlockArchitectureExt.getPhysicalArchitecture(se);
            //    };
            //
            // 3. Collect elements by type using EcoreUtil.getAllContents():
            //    TreeIterator<EObject> allContents = architecture.eAllContents();
            //    while (allContents.hasNext()) {
            //        EObject obj = allContents.next();
            //        if (matchesType(obj, elementType)) {
            //            results.add(toJsonSummary(obj));
            //        }
            //    }
            //
            // 4. Type matching uses instanceof checks against Capella metamodel classes:
            //    - "functions"    -> AbstractFunction (OperationalActivity, SystemFunction, etc.)
            //    - "components"   -> Component (Entity, SystemComponent, LogicalComponent, etc.)
            //    - "actors"       -> Component where isActor() == true
            //    - "exchanges"    -> AbstractExchange (FunctionalExchange, ComponentExchange)
            //    - "capabilities" -> AbstractCapability

            List<EObject> elements = queryElements(layer, elementType, maxResults);

            JsonArray resultsArray = new JsonArray();
            int count = 0;
            for (EObject element : elements) {
                if (count >= maxResults) {
                    break;
                }
                resultsArray.add(buildElementSummary(element));
                count++;
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("element_type", elementType);
            response.addProperty("count", resultsArray.size());
            response.addProperty("truncated", elements.size() > maxResults);
            response.add("elements", resultsArray);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to list elements: " + e.getMessage());
        }
    }

    /**
     * Queries elements from the Capella model for the given layer and type.
     *
     * @param layer       the ARCADIA layer identifier
     * @param elementType the type filter
     * @param maxResults  the maximum number of elements to collect
     * @return the list of matching EObjects
     */
    private List<EObject> queryElements(String layer, String elementType, int maxResults) {
        // PLACEHOLDER: Replace with actual Capella EMF navigation
        // This method should:
        // 1. Get the BlockArchitecture for the specified layer
        // 2. Iterate all contents with eAllContents()
        // 3. Filter by elementType using metamodel class checks
        // 4. Collect up to maxResults matching elements
        return new ArrayList<>();
    }

    /**
     * Builds a JSON summary object for a single model element.
     *
     * @param element the Capella model element
     * @return a JsonObject with name, uuid, type, and description_preview fields
     */
    private JsonObject buildElementSummary(EObject element) {
        JsonObject summary = new JsonObject();

        // PLACEHOLDER: Extract element properties using Capella metamodel accessors
        // NamedElement named = (NamedElement) element;
        // summary.addProperty("name", named.getName());
        // summary.addProperty("uuid", named.getId());
        // summary.addProperty("type", element.eClass().getName());
        // String desc = named.getDescription();
        // if (desc != null && desc.length() > 200) {
        //     desc = desc.substring(0, 200) + "...";
        // }
        // summary.addProperty("description_preview", desc != null ? desc : "");

        summary.addProperty("name", getElementName(element));
        summary.addProperty("uuid", getElementId(element));
        summary.addProperty("type", element.eClass().getName());
        summary.addProperty("description_preview", truncate(getElementDescription(element), 200));

        return summary;
    }
}

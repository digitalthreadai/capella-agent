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
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;
import org.polarsys.capella.common.data.modellingcore.ModelElement;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.FunctionalExchange;
import org.polarsys.capella.core.data.fa.ComponentExchange; // VERIFY: may be in cs package
import org.polarsys.capella.core.data.interaction.AbstractCapability;

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
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer to query: oa (Operational Analysis), "
                + "sa (System Analysis), la (Logical Architecture), pa (Physical Architecture)",
                VALID_LAYERS));
        params.add(ToolParameter.optionalEnum("element_type",
                "Type of elements to list: functions, components, actors, exchanges, "
                + "capabilities, or all (default: all)",
                VALID_TYPES, "all"));
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
            // Thread safety: the model query below should ideally be wrapped in a
            // read-exclusive transaction via TransactionalEditingDomain.runExclusive()
            // to prevent concurrent modification during traversal. Currently safe because
            // the ChatJob orchestration loop is single-threaded per conversation.
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();
            BlockArchitecture architecture = modelService.getArchitecture(session, layer);

            List<EObject> elements = queryElements(architecture, elementType, maxResults);

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
     * Queries elements from the Capella model for the given architecture and type.
     *
     * @param architecture the BlockArchitecture to traverse
     * @param elementType  the type filter
     * @param maxResults   the maximum number of elements to collect
     * @return the list of matching EObjects
     */
    private List<EObject> queryElements(BlockArchitecture architecture, String elementType, int maxResults) {
        List<EObject> results = new ArrayList<>();

        Iterator<EObject> allContents = architecture.eAllContents();
        while (allContents.hasNext() && results.size() < maxResults + 1) {
            EObject obj = allContents.next();
            if (matchesType(obj, elementType)) {
                results.add(obj);
            }
        }

        return results;
    }

    /**
     * Checks whether an EObject matches the requested element type filter.
     *
     * @param obj         the EObject to check
     * @param elementType the type filter string
     * @return true if the element matches the filter
     */
    private boolean matchesType(EObject obj, String elementType) {
        switch (elementType) {
            case "functions":
                return obj instanceof AbstractFunction;
            case "components":
                return obj instanceof Component;
            case "actors":
                return obj instanceof Component && ((Component) obj).isActor();
            case "exchanges":
                return obj instanceof FunctionalExchange || obj instanceof ComponentExchange;
            case "capabilities":
                return obj instanceof AbstractCapability;
            case "all":
                return obj instanceof AbstractNamedElement;
            default:
                return false;
        }
    }

    /**
     * Builds a JSON summary object for a single model element.
     *
     * @param element the Capella model element
     * @return a JsonObject with name, uuid, type, and description_preview fields
     */
    private JsonObject buildElementSummary(EObject element) {
        JsonObject summary = new JsonObject();

        summary.addProperty("name", getElementName(element));
        summary.addProperty("uuid", getElementId(element));
        summary.addProperty("type", element.eClass().getName());
        summary.addProperty("description_preview", truncate(getElementDescription(element), 200));

        return summary;
    }
}

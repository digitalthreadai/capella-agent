package com.capellaagent.modelchat.tools.read;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;

// PLACEHOLDER imports for Capella metamodel
// import org.polarsys.capella.core.data.capellacore.NamedElement;

/**
 * Searches for model elements by name pattern across the Capella model.
 * <p>
 * Supports both substring matching and regular expression patterns. Results can be
 * filtered by element type and architecture layer.
 *
 * <h3>Tool Specification</h3>
 * <ul>
 *   <li><b>Name:</b> search_elements</li>
 *   <li><b>Category:</b> model_read</li>
 *   <li><b>Parameters:</b>
 *     <ul>
 *       <li>{@code query} (string, required) - Name pattern to search for (substring or regex)</li>
 *       <li>{@code element_type} (string, optional) - Filter by type: functions, components,
 *           actors, exchanges, capabilities</li>
 *       <li>{@code layer} (string, optional) - Filter by layer: oa, sa, la, pa</li>
 *       <li>{@code case_sensitive} (boolean, optional, default false) - Whether matching is case-sensitive</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class SearchElementsTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "search_elements";
    private static final String DESCRIPTION =
            "Searches for model elements by name pattern (substring or regex). "
            + "Optionally filters by element type and architecture layer.";

    private static final int MAX_SEARCH_RESULTS = 200;

    public SearchElementsTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("query",
                "Name pattern to search for. Supports substring match or Java regex syntax."));
        params.add(ToolParameter.optionalString("element_type",
                "Filter by type: functions, components, actors, exchanges, capabilities"));
        params.add(ToolParameter.optionalString("layer",
                "Filter by architecture layer: oa, sa, la, pa"));
        params.add(ToolParameter.optionalBoolean("case_sensitive",
                "Whether the search is case-sensitive (default: false)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String query = getRequiredString(parameters, "query");
        String elementType = getOptionalString(parameters, "element_type", null);
        String layer = getOptionalString(parameters, "layer", null);
        boolean caseSensitive = getOptionalBoolean(parameters, "case_sensitive", false);

        if (query.isBlank()) {
            return ToolResult.error("Parameter 'query' must not be empty");
        }

        // Compile the search pattern
        Pattern pattern;
        try {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            pattern = Pattern.compile(query, flags);
        } catch (PatternSyntaxException e) {
            // Fall back to literal substring matching if the query is not valid regex
            int flags = caseSensitive ? Pattern.LITERAL : (Pattern.LITERAL | Pattern.CASE_INSENSITIVE);
            pattern = Pattern.compile(query, flags);
        }

        try {
            // PLACEHOLDER: Search across the Capella model
            // 1. If layer is specified, scope search to that BlockArchitecture
            // 2. Otherwise, search all architectures
            // 3. For each NamedElement, check if name matches the pattern
            // 4. If elementType is specified, additionally filter by metamodel class
            //
            // Session session = getActiveSession();
            // List<EObject> searchScope = determineScope(session, layer);
            // for (EObject root : searchScope) {
            //     TreeIterator<EObject> it = root.eAllContents();
            //     while (it.hasNext()) {
            //         EObject obj = it.next();
            //         if (obj instanceof NamedElement ne) {
            //             if (matchesType(obj, elementType) && pattern.matcher(ne.getName()).find()) {
            //                 results.add(obj);
            //             }
            //         }
            //     }
            // }

            List<EObject> matches = performSearch(pattern, elementType, layer);

            JsonArray resultsArray = new JsonArray();
            int count = 0;
            for (EObject match : matches) {
                if (count >= MAX_SEARCH_RESULTS) {
                    break;
                }
                JsonObject item = new JsonObject();
                item.addProperty("name", getElementName(match));
                item.addProperty("uuid", getElementId(match));
                item.addProperty("type", match.eClass().getName());
                item.addProperty("description_preview", truncate(getElementDescription(match), 150));
                item.addProperty("layer", detectLayer(match));
                resultsArray.add(item);
                count++;
            }

            JsonObject response = new JsonObject();
            response.addProperty("query", query);
            response.addProperty("total_matches", matches.size());
            response.addProperty("returned", resultsArray.size());
            response.addProperty("truncated", matches.size() > MAX_SEARCH_RESULTS);
            response.add("results", resultsArray);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Search failed: " + e.getMessage());
        }
    }

    /**
     * Performs the search across the Capella model using the compiled pattern.
     *
     * @param pattern     the compiled search pattern
     * @param elementType optional element type filter
     * @param layer       optional architecture layer filter
     * @return list of matching EObjects
     */
    private List<EObject> performSearch(Pattern pattern, String elementType, String layer) {
        // PLACEHOLDER: Implement actual model traversal and pattern matching
        return new ArrayList<>();
    }

    /**
     * Detects which ARCADIA layer an element belongs to by walking up the containment hierarchy.
     *
     * @param element the model element
     * @return the layer identifier (oa, sa, la, pa) or "unknown"
     */
    private String detectLayer(EObject element) {
        // PLACEHOLDER: Walk up containment to find the BlockArchitecture ancestor
        // EObject current = element;
        // while (current != null) {
        //     if (current instanceof OperationalAnalysis) return "oa";
        //     if (current instanceof SystemAnalysis) return "sa";
        //     if (current instanceof LogicalArchitecture) return "la";
        //     if (current instanceof PhysicalArchitecture) return "pa";
        //     current = current.eContainer();
        // }
        return "unknown";
    }
}

package com.capellaagent.modelchat.tools.read;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.capellaagent.core.capella.CapellaModelService;
import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.ctx.SystemAnalysis;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.FunctionalExchange;
import org.polarsys.capella.core.data.fa.ComponentExchange; // VERIFY: may be in cs package
import org.polarsys.capella.core.data.interaction.AbstractCapability;
import org.polarsys.capella.core.data.la.LogicalArchitecture;
import org.polarsys.capella.core.data.oa.OperationalAnalysis;
import org.polarsys.capella.core.data.pa.PhysicalArchitecture;

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
        params.add(ToolParameter.optionalEnum("element_type",
                "Filter by type: functions, components, actors, exchanges, capabilities",
                List.of("functions", "components", "actors", "exchanges", "capabilities"), null));
        params.add(ToolParameter.optionalEnum("layer",
                "Filter by architecture layer: oa, sa, la, pa",
                List.of("oa", "sa", "la", "pa"), null));
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
            // Thread safety: the model search below should ideally be wrapped in a
            // read-exclusive transaction via TransactionalEditingDomain.runExclusive()
            // to prevent concurrent modification during traversal. Currently safe because
            // the ChatJob orchestration loop is single-threaded per conversation.
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();

            List<EObject> matches = performSearch(session, modelService, pattern, elementType, layer);

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
                item.addProperty("layer", modelService.detectLayer(match));
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
     * If a layer is specified, scopes search to that architecture only.
     * Otherwise, searches all semantic resources.
     */
    private List<EObject> performSearch(Session session, CapellaModelService modelService,
                                         Pattern pattern, String elementType, String layer) {
        List<EObject> results = new ArrayList<>();
        int maxCollect = MAX_SEARCH_RESULTS + 1; // Collect one extra to detect truncation

        if (layer != null && !layer.isBlank()) {
            // Search within a specific architecture layer
            BlockArchitecture architecture = modelService.getArchitecture(session, layer.toLowerCase());
            searchContents(architecture.eAllContents(), pattern, elementType, results, maxCollect);
        } else {
            // Search all semantic resources
            for (Resource resource : session.getSemanticResources()) {
                Iterator<EObject> allContents = resource.getAllContents();
                searchContents(allContents, pattern, elementType, results, maxCollect);
                if (results.size() >= maxCollect) break;
            }
        }

        return results;
    }

    /**
     * Iterates through contents, matching names against the pattern and optional type filter.
     */
    private void searchContents(Iterator<EObject> contents, Pattern pattern,
                                  String elementType, List<EObject> results, int maxCollect) {
        while (contents.hasNext() && results.size() < maxCollect) {
            EObject obj = contents.next();

            // Must be a named element
            if (!(obj instanceof AbstractNamedElement)) {
                continue;
            }

            // Check type filter
            if (elementType != null && !matchesType(obj, elementType.toLowerCase())) {
                continue;
            }

            // Check name pattern
            String name = getElementName(obj);
            if (name != null && !name.isEmpty() && pattern.matcher(name).find()) {
                results.add(obj);
            }
        }
    }

    /**
     * Checks whether an EObject matches the requested element type filter.
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
            default:
                return true; // No type filter
        }
    }
}

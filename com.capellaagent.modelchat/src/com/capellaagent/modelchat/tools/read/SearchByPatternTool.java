package com.capellaagent.modelchat.tools.read;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
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
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;
import org.polarsys.capella.core.data.cs.BlockArchitecture;

/**
 * Performs regex or wildcard pattern search across all element names and
 * descriptions in the Capella model.
 * <p>
 * Unlike {@link SearchElementsTool} which only searches names, this tool
 * searches both names and descriptions and supports glob-style wildcards
 * (converted to regex internally).
 */
public class SearchByPatternTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "search_by_pattern";
    private static final String DESCRIPTION =
            "Regex/wildcard search across all element names and descriptions. "
            + "Supports Java regex or glob patterns (*, ?).";

    private static final int MAX_RESULTS = 200;
    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public SearchByPatternTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("pattern",
                "Search pattern. Use Java regex (e.g. 'Sys.*Control') or "
                + "glob wildcards (* matches any chars, ? matches one char)."));
        params.add(ToolParameter.optionalBoolean("search_descriptions",
                "Also search in element descriptions (default: true)"));
        params.add(ToolParameter.optionalBoolean("case_sensitive",
                "Whether search is case-sensitive (default: false)"));
        params.add(ToolParameter.optionalEnum("layer",
                "Filter by architecture layer: oa, sa, la, pa",
                VALID_LAYERS, null));
        params.add(ToolParameter.optionalBoolean("use_glob",
                "Interpret pattern as glob instead of regex (default: false)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String patternStr = getRequiredString(parameters, "pattern");
        boolean searchDescriptions = getOptionalBoolean(parameters, "search_descriptions", true);
        boolean caseSensitive = getOptionalBoolean(parameters, "case_sensitive", false);
        String layer = getOptionalString(parameters, "layer", null);
        boolean useGlob = getOptionalBoolean(parameters, "use_glob", false);

        // Convert glob to regex if needed
        if (useGlob) {
            patternStr = globToRegex(patternStr);
        }

        // Compile pattern
        Pattern pattern;
        try {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            pattern = Pattern.compile(patternStr, flags);
        } catch (PatternSyntaxException e) {
            // Fall back to literal match if invalid regex
            int flags = caseSensitive ? Pattern.LITERAL
                    : (Pattern.LITERAL | Pattern.CASE_INSENSITIVE);
            pattern = Pattern.compile(patternStr, flags);
        }

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();

            List<JsonObject> results = new ArrayList<>();
            int totalScanned = 0;

            if (layer != null && !layer.isBlank()) {
                // Search within a specific layer
                BlockArchitecture arch = modelService.getArchitecture(session, layer.toLowerCase());
                Iterator<EObject> it = arch.eAllContents();
                totalScanned = searchIterator(it, pattern, searchDescriptions, modelService, results);
            } else {
                // Search all semantic resources
                for (Resource resource : session.getSemanticResources()) {
                    Iterator<EObject> it = resource.getAllContents();
                    totalScanned += searchIterator(it, pattern, searchDescriptions, modelService, results);
                    if (results.size() >= MAX_RESULTS) break;
                }
            }

            JsonArray resultsArray = new JsonArray();
            int returned = Math.min(results.size(), MAX_RESULTS);
            for (int i = 0; i < returned; i++) {
                resultsArray.add(results.get(i));
            }

            JsonObject response = new JsonObject();
            response.addProperty("pattern", patternStr);
            response.addProperty("elements_scanned", totalScanned);
            response.addProperty("total_matches", results.size());
            response.addProperty("returned", returned);
            response.addProperty("truncated", results.size() > MAX_RESULTS);
            response.add("results", resultsArray);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Pattern search failed: " + e.getMessage());
        }
    }

    /**
     * Iterates through all contents, matching names and optionally descriptions.
     */
    private int searchIterator(Iterator<EObject> it, Pattern pattern,
                                boolean searchDescriptions, CapellaModelService modelService,
                                List<JsonObject> results) {
        int scanned = 0;
        while (it.hasNext() && results.size() < MAX_RESULTS + 1) {
            EObject obj = it.next();
            scanned++;

            if (!(obj instanceof AbstractNamedElement)) continue;

            String name = getElementName(obj);
            String desc = searchDescriptions ? getElementDescription(obj) : null;

            boolean nameMatch = name != null && !name.isEmpty() && pattern.matcher(name).find();
            boolean descMatch = desc != null && !desc.isEmpty() && pattern.matcher(desc).find();

            if (nameMatch || descMatch) {
                JsonObject item = new JsonObject();
                item.addProperty("name", name != null ? name : "");
                item.addProperty("uuid", getElementId(obj));
                item.addProperty("type", obj.eClass().getName());
                item.addProperty("layer", modelService.detectLayer(obj));
                item.addProperty("matched_in_name", nameMatch);
                item.addProperty("matched_in_description", descMatch);

                if (descMatch && desc != null) {
                    // Show the matching portion of the description
                    Matcher m = pattern.matcher(desc);
                    if (m.find()) {
                        int start = Math.max(0, m.start() - 30);
                        int end = Math.min(desc.length(), m.end() + 30);
                        String context = (start > 0 ? "..." : "")
                                + desc.substring(start, end)
                                + (end < desc.length() ? "..." : "");
                        item.addProperty("description_context", context);
                    }
                }

                // Show parent for context
                EObject container = obj.eContainer();
                if (container != null) {
                    item.addProperty("parent_name", getElementName(container));
                    item.addProperty("parent_type", container.eClass().getName());
                }

                results.add(item);
            }
        }
        return scanned;
    }

    /**
     * Converts a glob pattern to a Java regex pattern.
     * <ul>
     *   <li>{@code *} becomes {@code .*}</li>
     *   <li>{@code ?} becomes {@code .}</li>
     *   <li>All other regex special characters are escaped</li>
     * </ul>
     */
    private String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*':
                    regex.append(".*");
                    break;
                case '?':
                    regex.append('.');
                    break;
                case '.': case '(': case ')': case '[': case ']':
                case '{': case '}': case '\\': case '^': case '$':
                case '|': case '+':
                    regex.append('\\').append(c);
                    break;
                default:
                    regex.append(c);
            }
        }
        return regex.toString();
    }
}

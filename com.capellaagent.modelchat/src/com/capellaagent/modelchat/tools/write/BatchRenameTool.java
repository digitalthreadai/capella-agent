package com.capellaagent.modelchat.tools.write;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.capellaagent.core.capella.CapellaModelService;
import com.capellaagent.core.security.InputValidator;
import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.fa.AbstractFunction;

/**
 * Batch renames model elements matching a pattern.
 * <p>
 * Supports regex find/replace with a dry_run mode for previewing changes.
 * Can be scoped to a specific layer and element type.
 */
public class BatchRenameTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "batch_rename";
    private static final String DESCRIPTION =
            "Batch renames elements matching a pattern. Supports dry_run preview.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");
    private static final List<String> VALID_TYPES = List.of(
            "function", "component", "all");
    private static final int MAX_RENAMES = 200;

    public BatchRenameTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("find_pattern",
                "Regex or substring pattern to match element names"));
        params.add(ToolParameter.requiredString("replace_with",
                "Replacement string (supports regex groups like $1)"));
        params.add(ToolParameter.optionalEnum("layer",
                "Limit to a specific architecture layer",
                VALID_LAYERS, null));
        params.add(ToolParameter.optionalEnum("element_type",
                "Element type filter: function, component, all (default: all)",
                VALID_TYPES, "all"));
        params.add(ToolParameter.optionalBoolean("dry_run",
                "Preview changes without applying (default: true)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String findPattern = getRequiredString(parameters, "find_pattern");
        String replaceWith = getRequiredString(parameters, "replace_with");
        String layer = getOptionalString(parameters, "layer", null);
        String elementType = getOptionalString(parameters, "element_type", "all").toLowerCase();
        boolean dryRun = getOptionalBoolean(parameters, "dry_run", true);

        // Validate pattern
        Pattern pattern;
        try {
            pattern = Pattern.compile(findPattern);
        } catch (PatternSyntaxException e) {
            return ToolResult.error("Invalid regex pattern: " + e.getMessage());
        }

        // Sanitize replacement string
        try {
            InputValidator.sanitizeName(replaceWith.isEmpty() ? "placeholder" : replaceWith);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Invalid replacement: " + e.getMessage());
        }

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();

            // Collect matching elements
            List<MatchedElement> matches = new ArrayList<>();

            if (layer != null && !layer.isBlank()) {
                BlockArchitecture architecture = modelService.getArchitecture(session, layer.toLowerCase());
                collectMatches(architecture.eAllContents(), pattern, elementType, matches);
            } else {
                // Search all semantic resources
                for (Resource resource : session.getSemanticResources()) {
                    collectMatches(resource.getAllContents(), pattern, elementType, matches);
                    if (matches.size() >= MAX_RENAMES) break;
                }
            }

            // Build preview of changes
            JsonArray elements = new JsonArray();
            for (MatchedElement match : matches) {
                if (elements.size() >= MAX_RENAMES) break;
                Matcher m = pattern.matcher(match.oldName);
                String newName = m.replaceAll(replaceWith);

                JsonObject entry = new JsonObject();
                entry.addProperty("old_name", match.oldName);
                entry.addProperty("new_name", newName);
                entry.addProperty("id", match.id);
                entry.addProperty("type", match.type);
                elements.add(entry);
            }

            // Apply changes if not dry run
            if (!dryRun && !matches.isEmpty()) {
                TransactionalEditingDomain domain = getEditingDomain(session);
                domain.getCommandStack().execute(new RecordingCommand(domain,
                        "Batch rename: '" + findPattern + "' -> '" + replaceWith + "'") {
                    @Override
                    protected void doExecute() {
                        for (MatchedElement match : matches) {
                            Matcher m = pattern.matcher(match.oldName);
                            String newName = m.replaceAll(replaceWith);
                            if (match.element instanceof AbstractNamedElement) {
                                ((AbstractNamedElement) match.element).setName(newName);
                            }
                        }
                    }
                });
                modelService.invalidateCache(session);
            }

            JsonObject response = new JsonObject();
            response.addProperty("dry_run", dryRun);
            response.addProperty("find_pattern", findPattern);
            response.addProperty("replace_with", replaceWith);
            response.addProperty("matched_count", matches.size());
            response.addProperty("renamed_count", dryRun ? 0 : Math.min(matches.size(), MAX_RENAMES));
            response.add("elements", elements);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to batch rename: " + e.getMessage());
        }
    }

    /**
     * Collects elements whose names match the given pattern.
     */
    private void collectMatches(Iterator<EObject> contents, Pattern pattern,
                                  String elementType, List<MatchedElement> matches) {
        while (contents.hasNext() && matches.size() < MAX_RENAMES) {
            EObject obj = contents.next();
            if (!(obj instanceof AbstractNamedElement)) continue;

            // Type filter
            boolean typeMatch = "all".equals(elementType)
                    || ("function".equals(elementType) && obj instanceof AbstractFunction)
                    || ("component".equals(elementType) && obj instanceof Component);
            if (!typeMatch) continue;

            String name = ((AbstractNamedElement) obj).getName();
            if (name == null || name.isBlank()) continue;

            Matcher m = pattern.matcher(name);
            if (m.find()) {
                matches.add(new MatchedElement(obj, name, getElementId(obj), obj.eClass().getName()));
            }
        }
    }

    /**
     * Internal data class for matched elements.
     */
    private static class MatchedElement {
        final EObject element;
        final String oldName;
        final String id;
        final String type;

        MatchedElement(EObject element, String oldName, String id, String type) {
            this.element = element;
            this.oldName = oldName;
            this.id = id;
            this.type = type;
        }
    }
}

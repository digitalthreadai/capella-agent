package com.capellaagent.modelchat.tools.analysis;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.cs.Interface;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.ComponentExchange;
import org.polarsys.capella.core.data.fa.FunctionalExchange;

/**
 * Combines multiple quality checks and suggests improvements.
 */
public class SuggestImprovementsTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");
    private static final Pattern NAMING_PATTERN = Pattern.compile("^[A-Z].*");

    public SuggestImprovementsTool() {
        super("suggest_improvements",
                "Analyzes model quality and suggests improvements.",
                ToolCategory.ANALYSIS);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();
            BlockArchitecture arch = modelService.getArchitecture(session, layer);

            JsonArray suggestions = new JsonArray();

            Iterator<EObject> it = arch.eAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();

                // Check: empty interfaces
                if (obj instanceof Interface) {
                    Interface iface = (Interface) obj;
                    if (iface.getExchangeItems().isEmpty()) {
                        addSuggestion(suggestions, "empty_interface", "warning",
                                "Interface has no exchange items",
                                getElementName(iface), getElementId(iface));
                    }
                }

                // Check: components with no functions
                if (obj instanceof Component) {
                    Component comp = (Component) obj;
                    if (!comp.isActor()) {
                        int funcCount = 0;
                        Iterator<EObject> ci = comp.eAllContents();
                        while (ci.hasNext()) {
                            if (ci.next() instanceof AbstractFunction) funcCount++;
                        }
                        if (funcCount == 0) {
                            addSuggestion(suggestions, "empty_component", "warning",
                                    "Component has no allocated functions",
                                    getElementName(comp), getElementId(comp));
                        }
                    }
                }

                // Check: naming inconsistencies
                if (obj instanceof AbstractNamedElement) {
                    String name = getElementName(obj);
                    if (name != null && !name.isBlank()) {
                        if (name.contains("_") && (obj instanceof Component || obj instanceof AbstractFunction)) {
                            addSuggestion(suggestions, "naming", "info",
                                    "Name uses underscores; consider CamelCase",
                                    name, getElementId(obj));
                        }
                        if (name.length() < 3 && !(obj instanceof Interface)) {
                            addSuggestion(suggestions, "naming", "info",
                                    "Very short name; consider more descriptive naming",
                                    name, getElementId(obj));
                        }
                    }
                }

                // Check: exchanges without name
                if (obj instanceof FunctionalExchange) {
                    String name = getElementName(obj);
                    if (name == null || name.isBlank()) {
                        addSuggestion(suggestions, "unnamed_exchange", "warning",
                                "Functional exchange has no name",
                                "", getElementId(obj));
                    }
                }

                if (obj instanceof ComponentExchange) {
                    ComponentExchange ce = (ComponentExchange) obj;
                    String name = getElementName(ce);
                    if (name == null || name.isBlank()) {
                        addSuggestion(suggestions, "unnamed_exchange", "warning",
                                "Component exchange has no name",
                                "", getElementId(ce));
                    }
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("suggestion_count", suggestions.size());
            response.add("suggestions", suggestions);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to suggest improvements: " + e.getMessage());
        }
    }

    private void addSuggestion(JsonArray array, String category, String severity,
                                String message, String elementName, String elementId) {
        JsonObject s = new JsonObject();
        s.addProperty("category", category);
        s.addProperty("severity", severity);
        s.addProperty("message", message);
        s.addProperty("element_name", elementName);
        s.addProperty("element_id", elementId);
        array.add(s);
    }
}

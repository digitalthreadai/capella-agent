package com.capellaagent.modelchat.tools.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.capellaagent.core.capella.CapellaModelService;
import com.capellaagent.core.security.InputValidator;
import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;
import org.polarsys.capella.common.data.modellingcore.AbstractTrace;
import org.polarsys.capella.common.data.modellingcore.TraceableElement;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.ComponentExchange;
import org.polarsys.capella.core.data.fa.FunctionalExchange;

/**
 * Gathers comprehensive context for AI-predicted impact analysis of a
 * proposed change to a model element.
 * <p>
 * Performs multi-hop traversal from the target element through:
 * <ul>
 *   <li>Direct structural relationships (containment, references)</li>
 *   <li>Exchange/flow connections (functional and component exchanges)</li>
 *   <li>Traceability links (realizations, refinements across layers)</li>
 * </ul>
 * Returns the affected element graph with impact severity estimates
 * for the LLM to reason about.
 */
public class PredictImpactTool extends AbstractCapellaTool {

    private static final List<String> VALID_CHANGE_TYPES = List.of(
            "rename", "delete", "modify_interface", "move", "add_exchange", "remove_exchange");

    public PredictImpactTool() {
        super("predict_impact",
                "Gathers context for AI-predicted impact analysis of a proposed change. "
                + "Returns affected elements with severity estimates.",
                ToolCategory.AI_INTELLIGENCE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("element_uuid",
                "UUID of the element that would be changed"));
        params.add(ToolParameter.requiredEnum("change_type",
                "Type of proposed change: rename, delete, modify_interface, move, "
                + "add_exchange, remove_exchange",
                VALID_CHANGE_TYPES));
        params.add(ToolParameter.optionalInteger("max_hops",
                "Maximum traversal depth for impact propagation (default: 3, max: 5)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String elementUuid = getRequiredString(parameters, "element_uuid");
        String changeType = getRequiredString(parameters, "change_type").toLowerCase();
        int maxHops = Math.max(1, Math.min(getOptionalInt(parameters, "max_hops", 3), 5));

        try {
            elementUuid = InputValidator.validateUuid(elementUuid);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Invalid UUID: " + e.getMessage());
        }

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            EObject element = resolveElementByUuid(elementUuid);
            if (element == null) {
                return ToolResult.error("Element not found: " + elementUuid);
            }

            // BFS traversal to find affected elements
            Set<String> visited = new HashSet<>();
            Queue<ImpactEntry> queue = new LinkedList<>();
            List<ImpactEntry> allImpacted = new ArrayList<>();

            String rootId = getElementId(element);
            visited.add(rootId);
            queue.add(new ImpactEntry(element, 0, "direct", "Target of change"));

            while (!queue.isEmpty()) {
                ImpactEntry current = queue.poll();
                allImpacted.add(current);

                if (current.hop >= maxHops) continue;

                // Traverse different relationship types
                List<ImpactEntry> neighbors = findNeighbors(
                        current.element, current.hop + 1, changeType, modelService);
                for (ImpactEntry neighbor : neighbors) {
                    String neighborId = getElementId(neighbor.element);
                    if (!visited.contains(neighborId)) {
                        visited.add(neighborId);
                        queue.add(neighbor);
                    }
                }
            }

            // Build response
            JsonArray impactedElements = new JsonArray();
            int highCount = 0, mediumCount = 0, lowCount = 0;

            for (ImpactEntry entry : allImpacted) {
                JsonObject item = new JsonObject();
                item.addProperty("name", getElementName(entry.element));
                item.addProperty("id", getElementId(entry.element));
                item.addProperty("type", entry.element.eClass().getName());
                item.addProperty("layer", modelService.detectLayer(entry.element));
                item.addProperty("hop_distance", entry.hop);
                item.addProperty("relationship", entry.relationship);
                item.addProperty("reason", entry.reason);

                // Estimate severity based on hop distance and change type
                String severity = estimateSeverity(entry.hop, changeType, entry.relationship);
                item.addProperty("predicted_severity", severity);

                switch (severity) {
                    case "high": highCount++; break;
                    case "medium": mediumCount++; break;
                    case "low": lowCount++; break;
                }

                impactedElements.add(item);
            }

            JsonObject response = new JsonObject();
            response.addProperty("element_name", getElementName(element));
            response.addProperty("element_uuid", elementUuid);
            response.addProperty("change_type", changeType);
            response.addProperty("max_hops", maxHops);
            response.addProperty("total_impacted", allImpacted.size());
            response.addProperty("high_severity_count", highCount);
            response.addProperty("medium_severity_count", mediumCount);
            response.addProperty("low_severity_count", lowCount);
            response.add("impacted_elements", impactedElements);

            // Risk assessment prompt for the LLM
            response.addProperty("analysis_prompt",
                    "Based on the impacted elements above, provide:\n"
                    + "1. Overall risk assessment (low/medium/high/critical)\n"
                    + "2. Key risks and potential breakages\n"
                    + "3. Recommended mitigation steps\n"
                    + "4. Suggested order of changes to minimize disruption\n"
                    + "5. Elements that need manual review");

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Impact prediction failed: " + e.getMessage());
        }
    }

    /**
     * Finds neighboring elements connected to the given element through
     * various relationship types.
     */
    private List<ImpactEntry> findNeighbors(EObject element, int hop,
                                              String changeType,
                                              CapellaModelService modelService) {
        List<ImpactEntry> neighbors = new ArrayList<>();

        // 1. Container (parent)
        EObject container = element.eContainer();
        if (container instanceof AbstractNamedElement) {
            String name = getElementName(container);
            if (name != null && !name.isBlank()) {
                neighbors.add(new ImpactEntry(container, hop, "containment",
                        "Parent container of changed element"));
            }
        }

        // 2. Direct children
        for (EObject child : element.eContents()) {
            if (child instanceof AbstractNamedElement) {
                String name = getElementName(child);
                if (name != null && !name.isBlank()) {
                    neighbors.add(new ImpactEntry(child, hop, "containment",
                            "Child element affected by parent change"));
                }
            }
        }

        // 3. Exchange connections (for functions)
        if (element instanceof AbstractFunction) {
            AbstractFunction func = (AbstractFunction) element;
            for (var incoming : func.getIncoming()) {
                if (incoming instanceof FunctionalExchange) {
                    FunctionalExchange fe = (FunctionalExchange) incoming;
                    EObject source = fe.getSource();
                    if (source != null) {
                        EObject sourceFunc = source.eContainer();
                        if (sourceFunc instanceof AbstractFunction) {
                            neighbors.add(new ImpactEntry(sourceFunc, hop, "functional_exchange",
                                    "Connected via incoming exchange '" + getElementName(fe) + "'"));
                        }
                    }
                }
            }
            for (var outgoing : func.getOutgoing()) {
                if (outgoing instanceof FunctionalExchange) {
                    FunctionalExchange fe = (FunctionalExchange) outgoing;
                    EObject target = fe.getTarget();
                    if (target != null) {
                        EObject targetFunc = target.eContainer();
                        if (targetFunc instanceof AbstractFunction) {
                            neighbors.add(new ImpactEntry(targetFunc, hop, "functional_exchange",
                                    "Connected via outgoing exchange '" + getElementName(fe) + "'"));
                        }
                    }
                }
            }
        }

        // 4. Component exchanges
        if (element instanceof Component) {
            Component comp = (Component) element;
            Iterator<EObject> compIt = comp.eAllContents();
            while (compIt.hasNext()) {
                EObject obj = compIt.next();
                if (obj instanceof ComponentExchange) {
                    ComponentExchange ce = (ComponentExchange) obj;
                    EObject source = findOwnerComponent(ce.getSource());
                    EObject target = findOwnerComponent(ce.getTarget());

                    if (source != null && !getElementId(source).equals(getElementId(element))) {
                        neighbors.add(new ImpactEntry(source, hop, "component_exchange",
                                "Connected via exchange '" + getElementName(ce) + "'"));
                    }
                    if (target != null && !getElementId(target).equals(getElementId(element))) {
                        neighbors.add(new ImpactEntry(target, hop, "component_exchange",
                                "Connected via exchange '" + getElementName(ce) + "'"));
                    }
                }
            }
        }

        // 5. Traceability links
        if (element instanceof TraceableElement) {
            TraceableElement traceable = (TraceableElement) element;
            for (AbstractTrace trace : traceable.getOutgoingTraces()) {
                TraceableElement target = trace.getTargetElement();
                if (target != null) {
                    neighbors.add(new ImpactEntry(target, hop, "traceability",
                            "Traced via " + trace.eClass().getName()));
                }
            }
            for (AbstractTrace trace : traceable.getIncomingTraces()) {
                TraceableElement source = trace.getSourceElement();
                if (source != null) {
                    neighbors.add(new ImpactEntry(source, hop, "traceability",
                            "Traced via " + trace.eClass().getName()));
                }
            }
        }

        return neighbors;
    }

    /**
     * Estimates impact severity based on hop distance, change type, and relationship.
     */
    private String estimateSeverity(int hop, String changeType, String relationship) {
        if (hop == 0) return "high"; // Direct target

        // Delete has highest propagation impact
        if ("delete".equals(changeType)) {
            if (hop == 1) return "high";
            if (hop == 2) return "medium";
            return "low";
        }

        // Interface modification propagates through exchanges
        if ("modify_interface".equals(changeType)) {
            if (relationship.contains("exchange")) return hop <= 2 ? "high" : "medium";
            if (relationship.equals("traceability")) return "medium";
        }

        // General severity by distance
        if (hop == 1) return "medium";
        return "low";
    }

    private EObject findOwnerComponent(EObject obj) {
        while (obj != null) {
            if (obj instanceof Component) return obj;
            obj = obj.eContainer();
        }
        return null;
    }

    /**
     * Internal record for BFS traversal tracking.
     */
    private static class ImpactEntry {
        final EObject element;
        final int hop;
        final String relationship;
        final String reason;

        ImpactEntry(EObject element, int hop, String relationship, String reason) {
            this.element = element;
            this.hop = hop;
            this.relationship = relationship;
            this.reason = reason;
        }
    }
}

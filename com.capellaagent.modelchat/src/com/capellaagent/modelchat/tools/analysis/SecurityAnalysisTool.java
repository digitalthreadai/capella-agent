package com.capellaagent.modelchat.tools.analysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.capellaagent.core.capella.CapellaModelService;
import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.cs.Interface;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.ComponentExchange;
import org.polarsys.capella.core.data.fa.ComponentPort;
import org.polarsys.capella.core.data.fa.FunctionalExchange;
import org.polarsys.capella.core.data.information.ExchangeItem;

/**
 * Identifies security-relevant interfaces, data flows, and trust boundaries
 * in the Capella model.
 * <p>
 * Analysis includes:
 * <ul>
 *   <li>External interfaces (actor-to-system boundaries)</li>
 *   <li>Cross-component data flows that traverse trust boundaries</li>
 *   <li>Functions handling sensitive data (based on naming heuristics)</li>
 *   <li>Components with high fan-in (potential attack surface)</li>
 *   <li>Unprotected exchange items crossing boundaries</li>
 * </ul>
 */
public class SecurityAnalysisTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("sa", "la", "pa");

    // Security-sensitive keyword patterns (case-insensitive matching)
    private static final String[] SENSITIVE_KEYWORDS = {
            "auth", "login", "password", "credential", "token", "encrypt",
            "decrypt", "certificate", "key", "secret", "secure", "privacy",
            "personal", "payment", "financial", "health", "medical",
            "command", "control", "safety", "critical"
    };

    public SecurityAnalysisTool() {
        super("security_analysis",
                "Identifies security-relevant interfaces, data flows, and trust boundaries.",
                ToolCategory.ANALYSIS);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer to analyze: sa, la, pa",
                VALID_LAYERS));
        params.add(ToolParameter.optionalBoolean("include_recommendations",
                "Include security recommendations (default: true)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        boolean includeRecommendations = getOptionalBoolean(
                parameters, "include_recommendations", true);

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();
            BlockArchitecture arch = modelService.getArchitecture(session, layer);

            JsonArray externalInterfaces = new JsonArray();
            JsonArray trustBoundaryCrossings = new JsonArray();
            JsonArray sensitiveFunctions = new JsonArray();
            JsonArray highFanInComponents = new JsonArray();
            JsonArray securityFindings = new JsonArray();

            // Collect all components and categorize
            List<Component> allComponents = new ArrayList<>();
            List<Component> actors = new ArrayList<>();
            List<Component> systemComponents = new ArrayList<>();
            Map<String, Integer> componentIncomingCount = new java.util.HashMap<>();

            Iterator<EObject> it = arch.eAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();

                // Categorize components
                if (obj instanceof Component) {
                    Component comp = (Component) obj;
                    allComponents.add(comp);
                    if (comp.isActor()) {
                        actors.add(comp);
                    } else {
                        systemComponents.add(comp);
                    }
                }

                // Identify external interfaces (actor-to-system exchanges)
                if (obj instanceof ComponentExchange) {
                    ComponentExchange ce = (ComponentExchange) obj;
                    EObject sourceComp = findOwnerComponent(ce.getSource());
                    EObject targetComp = findOwnerComponent(ce.getTarget());

                    boolean sourceIsActor = sourceComp instanceof Component
                            && ((Component) sourceComp).isActor();
                    boolean targetIsActor = targetComp instanceof Component
                            && ((Component) targetComp).isActor();

                    // Track incoming exchanges per component
                    if (targetComp != null) {
                        String targetId = getElementId(targetComp);
                        componentIncomingCount.merge(targetId, 1, Integer::sum);
                    }

                    if (sourceIsActor || targetIsActor) {
                        JsonObject extIntf = new JsonObject();
                        extIntf.addProperty("exchange_name", getElementName(ce));
                        extIntf.addProperty("exchange_id", getElementId(ce));
                        extIntf.addProperty("source",
                                sourceComp != null ? getElementName(sourceComp) : "unknown");
                        extIntf.addProperty("target",
                                targetComp != null ? getElementName(targetComp) : "unknown");
                        extIntf.addProperty("source_is_actor", sourceIsActor);
                        extIntf.addProperty("target_is_actor", targetIsActor);
                        extIntf.addProperty("security_relevance", "high");
                        extIntf.addProperty("reason", "Crosses system boundary (actor interface)");
                        externalInterfaces.add(extIntf);

                        // This is a trust boundary crossing
                        JsonObject crossing = new JsonObject();
                        crossing.addProperty("exchange", getElementName(ce));
                        crossing.addProperty("boundary_type", "actor_system");
                        crossing.addProperty("from", sourceComp != null
                                ? getElementName(sourceComp) : "unknown");
                        crossing.addProperty("to", targetComp != null
                                ? getElementName(targetComp) : "unknown");
                        trustBoundaryCrossings.add(crossing);
                    }
                }

                // Identify security-sensitive functions
                if (obj instanceof AbstractFunction) {
                    AbstractFunction func = (AbstractFunction) obj;
                    String name = getElementName(func);
                    String desc = getElementDescription(func);
                    if (name == null || name.isBlank() || name.contains("Root")) continue;

                    String combinedText = (name + " " + (desc != null ? desc : "")).toLowerCase();
                    List<String> matchedKeywords = new ArrayList<>();
                    for (String keyword : SENSITIVE_KEYWORDS) {
                        if (combinedText.contains(keyword)) {
                            matchedKeywords.add(keyword);
                        }
                    }

                    if (!matchedKeywords.isEmpty()) {
                        JsonObject sf = new JsonObject();
                        sf.addProperty("function_name", name);
                        sf.addProperty("function_id", getElementId(func));
                        sf.addProperty("matched_keywords", String.join(", ", matchedKeywords));
                        sf.addProperty("incoming_exchanges", func.getIncoming().size());
                        sf.addProperty("outgoing_exchanges", func.getOutgoing().size());
                        sensitiveFunctions.add(sf);
                    }
                }
            }

            // Identify high fan-in components (potential attack surface)
            for (Component comp : systemComponents) {
                String compId = getElementId(comp);
                int inCount = componentIncomingCount.getOrDefault(compId, 0);
                if (inCount >= 3) {
                    JsonObject hfc = new JsonObject();
                    hfc.addProperty("component_name", getElementName(comp));
                    hfc.addProperty("component_id", compId);
                    hfc.addProperty("incoming_exchanges", inCount);
                    hfc.addProperty("risk", inCount >= 5 ? "high" : "medium");
                    hfc.addProperty("reason", "High fan-in increases attack surface");
                    highFanInComponents.add(hfc);
                }
            }

            // Generate security findings
            if (externalInterfaces.size() > 0) {
                JsonObject finding = new JsonObject();
                finding.addProperty("id", "SEC-001");
                finding.addProperty("title", "External Interface Exposure");
                finding.addProperty("severity", "high");
                finding.addProperty("count", externalInterfaces.size());
                finding.addProperty("description",
                        externalInterfaces.size() + " external interfaces cross the system boundary");
                if (includeRecommendations) {
                    finding.addProperty("recommendation",
                            "Ensure all actor interfaces implement authentication, "
                            + "input validation, and rate limiting");
                }
                securityFindings.add(finding);
            }

            if (sensitiveFunctions.size() > 0) {
                JsonObject finding = new JsonObject();
                finding.addProperty("id", "SEC-002");
                finding.addProperty("title", "Security-Sensitive Functions");
                finding.addProperty("severity", "medium");
                finding.addProperty("count", sensitiveFunctions.size());
                finding.addProperty("description",
                        sensitiveFunctions.size() + " functions handle potentially sensitive data");
                if (includeRecommendations) {
                    finding.addProperty("recommendation",
                            "Review these functions for proper data protection, "
                            + "encryption, and access control");
                }
                securityFindings.add(finding);
            }

            if (highFanInComponents.size() > 0) {
                JsonObject finding = new JsonObject();
                finding.addProperty("id", "SEC-003");
                finding.addProperty("title", "High Fan-In Components");
                finding.addProperty("severity", "medium");
                finding.addProperty("count", highFanInComponents.size());
                finding.addProperty("description",
                        highFanInComponents.size() + " components have high fan-in "
                        + "(3+ incoming exchanges)");
                if (includeRecommendations) {
                    finding.addProperty("recommendation",
                            "Consider adding input validation and error isolation "
                            + "at high fan-in points");
                }
                securityFindings.add(finding);
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("actor_count", actors.size());
            response.addProperty("system_component_count", systemComponents.size());
            response.addProperty("external_interface_count", externalInterfaces.size());
            response.addProperty("trust_boundary_crossing_count", trustBoundaryCrossings.size());
            response.addProperty("sensitive_function_count", sensitiveFunctions.size());
            response.addProperty("finding_count", securityFindings.size());
            response.add("external_interfaces", externalInterfaces);
            response.add("trust_boundary_crossings", trustBoundaryCrossings);
            response.add("sensitive_functions", sensitiveFunctions);
            response.add("high_fan_in_components", highFanInComponents);
            response.add("security_findings", securityFindings);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Security analysis failed: " + e.getMessage());
        }
    }

    /**
     * Finds the owning Component of a port or exchange endpoint by
     * traversing the containment hierarchy.
     */
    private EObject findOwnerComponent(EObject portOrEndpoint) {
        if (portOrEndpoint == null) return null;
        EObject current = portOrEndpoint;
        while (current != null) {
            if (current instanceof Component) return current;
            current = current.eContainer();
        }
        return null;
    }
}

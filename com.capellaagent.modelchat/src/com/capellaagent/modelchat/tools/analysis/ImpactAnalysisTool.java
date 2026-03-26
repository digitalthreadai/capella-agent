package com.capellaagent.modelchat.tools.analysis;

import java.util.ArrayList;
import java.util.Collection;
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
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.sirius.business.api.dialect.DialectManager;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.diagram.DDiagram;
import org.eclipse.sirius.diagram.DDiagramElement;
import org.eclipse.sirius.viewpoint.DRepresentation;
import org.eclipse.sirius.viewpoint.DRepresentationDescriptor;
import org.eclipse.sirius.viewpoint.DSemanticDecorator;

/**
 * Performs impact analysis on a model element.
 * <p>
 * Uses EcoreUtil.UsageCrossReferencer to find ALL references to the element,
 * classified by type (allocations, exchanges, traces, diagram appearances).
 * This helps understand the full impact of modifying or deleting an element.
 */
public class ImpactAnalysisTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "impact_analysis";
    private static final String DESCRIPTION =
            "Analyzes the impact of an element: references, diagrams, dependencies.";

    public ImpactAnalysisTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.ANALYSIS);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("element_uuid",
                "UUID of the element to analyze"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String elementUuid = getRequiredString(parameters, "element_uuid");

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            EObject element = resolveElementByUuid(elementUuid);
            if (element == null) {
                return ToolResult.error("Element not found with UUID: " + elementUuid);
            }

            // Find all cross-references to this element using EcoreUtil
            Collection<EStructuralFeature.Setting> usages =
                    EcoreUtil.UsageCrossReferencer.find(element, element.eResource().getResourceSet());

            JsonArray directReferences = new JsonArray();
            int allocationCount = 0;
            int exchangeCount = 0;
            int traceCount = 0;
            int otherCount = 0;

            for (EStructuralFeature.Setting setting : usages) {
                EObject referrer = setting.getEObject();
                if (referrer == null) continue;

                String referrerType = referrer.eClass().getName();
                String featureName = setting.getEStructuralFeature().getName();

                // Classify the reference
                String relationshipType;
                if (referrerType.contains("Allocation") || featureName.contains("allocation")) {
                    relationshipType = "allocation";
                    allocationCount++;
                } else if (referrerType.contains("Exchange") || featureName.contains("exchange")) {
                    relationshipType = "exchange";
                    exchangeCount++;
                } else if (referrerType.contains("Realization") || referrerType.contains("Trace")
                        || featureName.contains("trace") || featureName.contains("realization")) {
                    relationshipType = "trace";
                    traceCount++;
                } else {
                    relationshipType = "reference";
                    otherCount++;
                }

                if (directReferences.size() < 200) { // Limit output size
                    JsonObject refObj = new JsonObject();
                    refObj.addProperty("name", getElementName(referrer));
                    refObj.addProperty("id", getElementId(referrer));
                    refObj.addProperty("type", referrerType);
                    refObj.addProperty("relationship_type", relationshipType);
                    refObj.addProperty("feature", featureName);
                    refObj.addProperty("layer", modelService.detectLayer(referrer));
                    directReferences.add(refObj);
                }
            }

            // Find diagram appearances
            JsonArray diagramAppearances = new JsonArray();
            Collection<DRepresentationDescriptor> allDescriptors =
                    DialectManager.INSTANCE.getAllRepresentationDescriptors(session);

            for (DRepresentationDescriptor desc : allDescriptors) {
                try {
                    DRepresentation rep = desc.getRepresentation();
                    if (rep instanceof DDiagram) {
                        DDiagram diagram = (DDiagram) rep;
                        for (DDiagramElement de : diagram.getDiagramElements()) {
                            if (de instanceof DSemanticDecorator) {
                                EObject target = ((DSemanticDecorator) de).getTarget();
                                if (target == element) {
                                    JsonObject diagObj = new JsonObject();
                                    diagObj.addProperty("diagram_name", desc.getName());
                                    diagObj.addProperty("diagram_id",
                                            desc.getUid() != null ? desc.getUid().toString() : "");
                                    diagramAppearances.add(diagObj);
                                    break; // Count each diagram once
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Skip unloadable representations
                }
            }

            int totalImpact = directReferences.size() + diagramAppearances.size();

            JsonObject response = new JsonObject();
            response.addProperty("element_name", getElementName(element));
            response.addProperty("element_uuid", elementUuid);
            response.addProperty("element_type", element.eClass().getName());
            response.addProperty("layer", modelService.detectLayer(element));
            response.addProperty("total_impact_count", totalImpact);

            JsonObject breakdown = new JsonObject();
            breakdown.addProperty("allocations", allocationCount);
            breakdown.addProperty("exchanges", exchangeCount);
            breakdown.addProperty("traces", traceCount);
            breakdown.addProperty("other_references", otherCount);
            breakdown.addProperty("diagram_appearances", diagramAppearances.size());
            response.add("impact_breakdown", breakdown);

            response.add("direct_references", directReferences);
            response.add("diagram_appearances", diagramAppearances);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to perform impact analysis: " + e.getMessage());
        }
    }
}

package com.capellaagent.modelchat.tools.read;

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
import org.polarsys.capella.common.data.modellingcore.AbstractTrace;
import org.polarsys.capella.common.data.modellingcore.TraceableElement;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.ComponentFunctionalAllocation;
import org.polarsys.capella.core.data.fa.FunctionalExchange;
import org.polarsys.capella.core.data.interaction.AbstractCapability;

/**
 * Gathers comprehensive context about a model element for LLM explanation.
 * <p>
 * Resolves the element and collects: name, type, layer, description, parent,
 * children, allocations, exchanges, traceability links, and diagram appearances.
 * The LLM uses this rich context to generate a human-friendly explanation.
 */
public class ExplainElementTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "explain_element";
    private static final String DESCRIPTION =
            "Gathers comprehensive element context for LLM-generated explanation.";

    public ExplainElementTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("element_uuid",
                "UUID of the element to explain"));
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

            JsonObject response = new JsonObject();

            // Basic info
            response.addProperty("name", getElementName(element));
            response.addProperty("uuid", elementUuid);
            response.addProperty("type", element.eClass().getName());
            response.addProperty("layer", modelService.detectLayer(element));
            response.addProperty("description", getElementDescription(element));

            // Parent info
            EObject parent = element.eContainer();
            if (parent != null) {
                JsonObject parentObj = new JsonObject();
                parentObj.addProperty("name", getElementName(parent));
                parentObj.addProperty("id", getElementId(parent));
                parentObj.addProperty("type", parent.eClass().getName());
                response.add("parent", parentObj);
            }

            // Direct children
            JsonArray children = new JsonArray();
            int childCount = 0;
            for (EObject child : element.eContents()) {
                if (childCount >= 50) break; // Limit children
                String childName = getElementName(child);
                if (childName != null && !childName.isBlank()) {
                    JsonObject childObj = new JsonObject();
                    childObj.addProperty("name", childName);
                    childObj.addProperty("id", getElementId(child));
                    childObj.addProperty("type", child.eClass().getName());
                    children.add(childObj);
                    childCount++;
                }
            }
            response.add("children", children);
            response.addProperty("child_count", childCount);

            // Function-specific: allocations and exchanges
            if (element instanceof AbstractFunction) {
                AbstractFunction fn = (AbstractFunction) element;

                // Allocations
                JsonArray allocations = new JsonArray();
                try {
                    List<?> allocs = fn.getComponentFunctionalAllocations();
                    if (allocs != null) {
                        for (Object alloc : allocs) {
                            if (alloc instanceof ComponentFunctionalAllocation) {
                                ComponentFunctionalAllocation cfa = (ComponentFunctionalAllocation) alloc;
                                EObject comp = cfa.getSourceElement();
                                if (comp != null) {
                                    JsonObject allocObj = new JsonObject();
                                    allocObj.addProperty("component_name", getElementName(comp));
                                    allocObj.addProperty("component_id", getElementId(comp));
                                    allocations.add(allocObj);
                                }
                            }
                        }
                    }
                } catch (Exception e) { /* skip */ }
                response.add("allocated_to_components", allocations);

                // Functional exchanges
                JsonArray exchanges = new JsonArray();
                try {
                    List<FunctionalExchange> ownedExchanges = fn.getOwnedFunctionalExchanges();
                    if (ownedExchanges != null) {
                        for (FunctionalExchange fe : ownedExchanges) {
                            JsonObject feObj = new JsonObject();
                            feObj.addProperty("name", getElementName(fe));
                            feObj.addProperty("id", getElementId(fe));
                            if (fe.getTarget() != null) {
                                EObject tgtFn = fe.getTarget();
                                while (tgtFn != null && !(tgtFn instanceof AbstractFunction)) {
                                    tgtFn = tgtFn.eContainer();
                                }
                                if (tgtFn != null) {
                                    feObj.addProperty("target_function", getElementName(tgtFn));
                                }
                            }
                            exchanges.add(feObj);
                        }
                    }
                } catch (Exception e) { /* skip */ }
                response.add("outgoing_exchanges", exchanges);
            }

            // Component-specific: allocated functions
            if (element instanceof Component) {
                Component comp = (Component) element;
                JsonArray allocatedFunctions = new JsonArray();
                try {
                    List<ComponentFunctionalAllocation> allocs = comp.getFunctionalAllocations();
                    if (allocs != null) {
                        for (ComponentFunctionalAllocation alloc : allocs) {
                            AbstractFunction fn = alloc.getFunction();
                            if (fn != null) {
                                JsonObject fnObj = new JsonObject();
                                fnObj.addProperty("name", getElementName(fn));
                                fnObj.addProperty("id", getElementId(fn));
                                allocatedFunctions.add(fnObj);
                            }
                        }
                    }
                } catch (Exception e) { /* skip */ }
                response.add("allocated_functions", allocatedFunctions);
                response.addProperty("is_actor", comp.isActor());
            }

            // Traceability links
            if (element instanceof TraceableElement) {
                TraceableElement te = (TraceableElement) element;

                JsonArray realizingElements = new JsonArray();
                JsonArray realizedElements = new JsonArray();

                try {
                    for (AbstractTrace trace : te.getIncomingTraces()) {
                        TraceableElement source = trace.getSourceElement();
                        if (source != null && source != element) {
                            JsonObject linkObj = new JsonObject();
                            linkObj.addProperty("name", getElementName(source));
                            linkObj.addProperty("id", getElementId(source));
                            linkObj.addProperty("type", source.eClass().getName());
                            linkObj.addProperty("layer", modelService.detectLayer(source));
                            realizingElements.add(linkObj);
                        }
                    }
                } catch (Exception e) { /* skip */ }

                try {
                    for (AbstractTrace trace : te.getOutgoingTraces()) {
                        TraceableElement target = trace.getTargetElement();
                        if (target != null && target != element) {
                            JsonObject linkObj = new JsonObject();
                            linkObj.addProperty("name", getElementName(target));
                            linkObj.addProperty("id", getElementId(target));
                            linkObj.addProperty("type", target.eClass().getName());
                            linkObj.addProperty("layer", modelService.detectLayer(target));
                            realizedElements.add(linkObj);
                        }
                    }
                } catch (Exception e) { /* skip */ }

                response.add("realizing_elements", realizingElements);
                response.add("realized_elements", realizedElements);
            }

            // Diagram appearances
            JsonArray diagrams = new JsonArray();
            try {
                Collection<DRepresentationDescriptor> allDescriptors =
                        DialectManager.INSTANCE.getAllRepresentationDescriptors(session);
                for (DRepresentationDescriptor desc : allDescriptors) {
                    try {
                        DRepresentation rep = desc.getRepresentation();
                        if (rep instanceof DDiagram) {
                            DDiagram diagram = (DDiagram) rep;
                            for (DDiagramElement de : diagram.getDiagramElements()) {
                                if (de instanceof DSemanticDecorator
                                        && ((DSemanticDecorator) de).getTarget() == element) {
                                    JsonObject diagObj = new JsonObject();
                                    diagObj.addProperty("diagram_name", desc.getName());
                                    diagObj.addProperty("diagram_id",
                                            desc.getUid() != null ? desc.getUid().toString() : "");
                                    diagrams.add(diagObj);
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) { /* skip unloadable diagrams */ }
                }
            } catch (Exception e) { /* skip */ }
            response.add("diagram_appearances", diagrams);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to explain element: " + e.getMessage());
        }
    }
}

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
import org.polarsys.capella.common.data.modellingcore.ModelElement;
import org.polarsys.capella.core.data.capellacore.Constraint;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.information.datavalue.OpaqueExpression;

/**
 * Lists constraints and their constrained elements in a layer or element.
 */
public class GetConstraintsTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public GetConstraintsTool() {
        super("get_constraints",
                "Lists constraints with expressions and constrained elements.",
                ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalString("element_uuid",
                "UUID of element to get constraints for"));
        params.add(ToolParameter.optionalEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS, null));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String elementUuid = getOptionalString(parameters, "element_uuid", null);
        String layer = getOptionalString(parameters, "layer", null);

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            List<Constraint> constraints = new ArrayList<>();

            if (elementUuid != null && !elementUuid.isBlank()) {
                EObject element = resolveElementByUuid(elementUuid);
                if (element == null) {
                    return ToolResult.error("Element not found: " + elementUuid);
                }
                collectConstraints(element, constraints);
            } else {
                List<String> layers = (layer != null && !layer.isBlank())
                        ? List.of(layer.toLowerCase()) : VALID_LAYERS;
                for (String l : layers) {
                    try {
                        BlockArchitecture arch = modelService.getArchitecture(session, l);
                        collectConstraints(arch, constraints);
                    } catch (Exception e) { /* skip */ }
                }
            }

            JsonArray results = new JsonArray();
            for (Constraint c : constraints) {
                JsonObject cObj = new JsonObject();
                cObj.addProperty("name", getElementName(c));
                cObj.addProperty("id", getElementId(c));

                // Get expression
                String expression = "";
                if (c.getOwnedSpecification() != null) {
                    EObject spec = c.getOwnedSpecification();
                    if (spec instanceof OpaqueExpression) {
                        OpaqueExpression oe = (OpaqueExpression) spec;
                        if (oe.getBodies() != null && !oe.getBodies().isEmpty()) {
                            expression = oe.getBodies().get(0);
                        }
                    } else {
                        expression = spec.toString();
                    }
                }
                cObj.addProperty("expression", expression);

                JsonArray constrained = new JsonArray();
                for (ModelElement me : c.getConstrainedElements()) {
                    JsonObject meObj = new JsonObject();
                    meObj.addProperty("name", getElementName(me));
                    meObj.addProperty("id", getElementId(me));
                    constrained.add(meObj);
                }
                cObj.add("constrained_elements", constrained);
                results.add(cObj);
            }

            JsonObject response = new JsonObject();
            response.addProperty("count", results.size());
            response.add("constraints", results);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to get constraints: " + e.getMessage());
        }
    }

    private void collectConstraints(EObject root, List<Constraint> constraints) {
        Iterator<EObject> it = root.eAllContents();
        while (it.hasNext()) {
            EObject obj = it.next();
            if (obj instanceof Constraint) {
                constraints.add((Constraint) obj);
            }
        }
    }
}

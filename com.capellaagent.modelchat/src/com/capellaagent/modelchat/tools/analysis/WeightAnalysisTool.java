package com.capellaagent.modelchat.tools.analysis;

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
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.pa.PhysicalArchitecture;
import org.polarsys.capella.core.data.pa.PhysicalComponent;
import org.polarsys.capella.core.data.pa.PhysicalComponentNature;
import org.polarsys.capella.core.data.capellacore.AbstractPropertyValue;
import org.polarsys.capella.core.data.capellacore.FloatPropertyValue;
import org.polarsys.capella.core.data.capellacore.IntegerPropertyValue;
import org.polarsys.capella.core.data.capellacore.StringPropertyValue;

/**
 * Estimates system weight from physical component properties.
 * <p>
 * Scans components (especially PhysicalComponents in PA layer) for
 * weight-related property values. Computes totals per component and
 * across the system. For components without explicit weight properties,
 * provides heuristic estimates based on component nature (NODE vs BEHAVIOR).
 */
public class WeightAnalysisTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("sa", "la", "pa");

    // Property names that typically store weight information
    private static final String[] WEIGHT_PROPERTY_NAMES = {
            "weight", "mass", "Weight", "Mass", "weight_kg", "mass_kg",
            "Weight (kg)", "Mass (kg)", "weight_lbs", "mass_lbs"
    };

    public WeightAnalysisTool() {
        super("weight_analysis",
                "Estimates system weight from component properties and physical architecture.",
                ToolCategory.ANALYSIS);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalEnum("layer",
                "Architecture layer to analyze (default: pa for physical)",
                VALID_LAYERS, "pa"));
        params.add(ToolParameter.optionalBoolean("include_estimates",
                "Include heuristic weight estimates for components without explicit "
                + "weight properties (default: true)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getOptionalString(parameters, "layer", "pa").toLowerCase();
        boolean includeEstimates = getOptionalBoolean(parameters, "include_estimates", true);

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();
            BlockArchitecture arch = modelService.getArchitecture(session, layer);

            JsonArray componentWeights = new JsonArray();
            double totalExplicitWeight = 0.0;
            double totalEstimatedWeight = 0.0;
            int componentsWithWeight = 0;
            int componentsWithEstimate = 0;
            int totalComponents = 0;

            Iterator<EObject> it = arch.eAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();

                if (!(obj instanceof Component)) continue;
                Component comp = (Component) obj;
                if (comp.isActor()) continue;

                String compName = getElementName(comp);
                if (compName == null || compName.isBlank()) continue;

                totalComponents++;

                JsonObject compWeight = new JsonObject();
                compWeight.addProperty("component_name", compName);
                compWeight.addProperty("component_id", getElementId(comp));
                compWeight.addProperty("component_type", comp.eClass().getName());

                // Check if it's a PhysicalComponent and get its nature
                String nature = "unknown";
                if (comp instanceof PhysicalComponent) {
                    PhysicalComponent pc = (PhysicalComponent) comp;
                    PhysicalComponentNature pcNature = pc.getNature();
                    if (pcNature != null) {
                        nature = pcNature.getName();
                    }
                    compWeight.addProperty("nature", nature);
                }

                // Search for weight property values
                Double weightValue = findWeightProperty(comp);

                if (weightValue != null) {
                    compWeight.addProperty("weight_kg", weightValue);
                    compWeight.addProperty("weight_source", "explicit_property");
                    totalExplicitWeight += weightValue;
                    componentsWithWeight++;
                } else if (includeEstimates) {
                    // Heuristic estimate based on component nature and children
                    double estimate = estimateWeight(comp, nature);
                    if (estimate > 0) {
                        compWeight.addProperty("weight_kg", estimate);
                        compWeight.addProperty("weight_source", "heuristic_estimate");
                        totalEstimatedWeight += estimate;
                        componentsWithEstimate++;
                    } else {
                        compWeight.addProperty("weight_kg", 0.0);
                        compWeight.addProperty("weight_source", "no_data");
                    }
                } else {
                    compWeight.addProperty("weight_kg", 0.0);
                    compWeight.addProperty("weight_source", "no_data");
                }

                // Count sub-components
                int subCompCount = 0;
                for (EObject child : comp.eContents()) {
                    if (child instanceof Component) subCompCount++;
                }
                compWeight.addProperty("sub_component_count", subCompCount);

                componentWeights.add(compWeight);
            }

            // Build summary
            JsonObject summary = new JsonObject();
            summary.addProperty("total_components", totalComponents);
            summary.addProperty("components_with_explicit_weight", componentsWithWeight);
            summary.addProperty("components_with_estimate", componentsWithEstimate);
            summary.addProperty("components_without_weight",
                    totalComponents - componentsWithWeight - componentsWithEstimate);
            summary.addProperty("total_explicit_weight_kg", roundTo(totalExplicitWeight, 2));
            summary.addProperty("total_estimated_weight_kg", roundTo(totalEstimatedWeight, 2));
            summary.addProperty("total_weight_kg",
                    roundTo(totalExplicitWeight + totalEstimatedWeight, 2));

            // Weight coverage percentage
            double coveragePercent = totalComponents > 0
                    ? ((componentsWithWeight + componentsWithEstimate) * 100.0 / totalComponents)
                    : 0.0;
            summary.addProperty("weight_coverage_percent", roundTo(coveragePercent, 1));

            // Weight confidence rating
            String confidence;
            if (componentsWithWeight == totalComponents) {
                confidence = "high";
            } else if (componentsWithWeight > 0) {
                confidence = "medium";
            } else if (componentsWithEstimate > 0) {
                confidence = "low";
            } else {
                confidence = "none";
            }
            summary.addProperty("confidence", confidence);

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.add("summary", summary);
            response.add("component_weights", componentWeights);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Weight analysis failed: " + e.getMessage());
        }
    }

    /**
     * Searches a component's property values for weight/mass data.
     * Returns the weight in kg, or null if not found.
     */
    private Double findWeightProperty(Component comp) {
        try {
            List<AbstractPropertyValue> propertyValues = comp.getOwnedPropertyValues();
            for (AbstractPropertyValue pv : propertyValues) {
                String pvName = pv.getName();
                if (pvName == null) continue;

                String pvNameLower = pvName.toLowerCase();
                boolean isWeightProp = false;
                for (String weightName : WEIGHT_PROPERTY_NAMES) {
                    if (pvNameLower.contains(weightName.toLowerCase())) {
                        isWeightProp = true;
                        break;
                    }
                }

                if (isWeightProp) {
                    if (pv instanceof FloatPropertyValue) {
                        return (double) ((FloatPropertyValue) pv).getValue();
                    } else if (pv instanceof IntegerPropertyValue) {
                        return (double) ((IntegerPropertyValue) pv).getValue();
                    } else if (pv instanceof StringPropertyValue) {
                        try {
                            return Double.parseDouble(((StringPropertyValue) pv).getValue());
                        } catch (NumberFormatException e) {
                            // Not a numeric string
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Property API may not be available
        }

        // Also check via generic EStructuralFeature for custom metamodel extensions
        for (String propName : WEIGHT_PROPERTY_NAMES) {
            EStructuralFeature feature = comp.eClass().getEStructuralFeature(propName);
            if (feature != null) {
                Object val = comp.eGet(feature);
                if (val instanceof Number) {
                    return ((Number) val).doubleValue();
                }
            }
        }

        return null;
    }

    /**
     * Provides a heuristic weight estimate based on component nature and structure.
     * <ul>
     *   <li>NODE components (hardware): estimated at 5 kg base + 2 kg per sub-component</li>
     *   <li>BEHAVIOR components (software): estimated at 0 kg (no physical weight)</li>
     *   <li>Unknown: estimated at 1 kg base + 0.5 kg per sub-component</li>
     * </ul>
     */
    private double estimateWeight(Component comp, String nature) {
        int subCompCount = 0;
        for (EObject child : comp.eContents()) {
            if (child instanceof Component) subCompCount++;
        }

        // Don't estimate leaf containers that hold sub-components (avoid double counting)
        if (subCompCount > 0) {
            return 0.0; // Weight comes from children
        }

        switch (nature.toUpperCase()) {
            case "NODE":
                return 5.0; // Base hardware component weight
            case "BEHAVIOR":
                return 0.0; // Software has no physical weight
            default:
                return 1.0; // Unknown nature, small default estimate
        }
    }

    private double roundTo(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}

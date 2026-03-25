package com.capellaagent.simulation.orchestrator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Platform;

// PLACEHOLDER imports for Capella API:
// import org.eclipse.sirius.business.api.session.Session;
// import org.eclipse.emf.ecore.EObject;
// import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Extracts parameter values from the Capella model based on parameter mappings.
 * <p>
 * Reads the current values of Capella model element properties and converts
 * them to the data types expected by the simulation engine.
 *
 * <h3>PLACEHOLDER Notice</h3>
 * The actual Capella model property reading logic requires Capella metamodel
 * APIs. This implementation uses placeholder code that returns default values.
 */
public class ParameterExtractor {

    /**
     * Extracts parameter values from the Capella model using the given mappings.
     *
     * @param mappings the parameter mappings defining which model properties to read
     * @param session  the Sirius session (passed as Object to avoid compile-time dependency)
     * @return a map of parameter name to extracted value
     */
    public Map<String, Object> extract(List<ParameterMapping> mappings, Object session) {
        Map<String, Object> parameters = new HashMap<>();

        for (ParameterMapping mapping : mappings) {
            if (!mapping.isValid()) {
                Platform.getLog(getClass()).warn(
                        "Skipping invalid parameter mapping: " + mapping);
                continue;
            }

            Object value = extractValue(mapping, session);
            parameters.put(mapping.paramName(), value);

            Platform.getLog(getClass()).info(
                    "Extracted parameter '" + mapping.paramName() + "' = " + value
                            + " from element " + mapping.elementUuid());
        }

        return parameters;
    }

    /**
     * Extracts a single parameter value from the Capella model.
     *
     * @param mapping the parameter mapping
     * @param session the Sirius session
     * @return the extracted value, converted to the appropriate type
     */
    private Object extractValue(ParameterMapping mapping, Object session) {
        // PLACEHOLDER: Read the actual value from the Capella model.
        // In a real implementation:
        //
        // 1. Find the EObject by UUID
        //    Session siriusSession = (Session) session;
        //    EObject element = findByUuid(siriusSession, mapping.elementUuid());
        //
        // 2. Navigate the property path
        //    Object rawValue = resolvePropertyPath(element, mapping.propertyPath());
        //
        // 3. Convert to the target data type
        //    return convertValue(rawValue, mapping.dataType());

        // Return a typed default value based on the data type
        return getDefaultValue(mapping.dataType());
    }

    /**
     * Returns a default value for the given data type.
     */
    private Object getDefaultValue(String dataType) {
        return switch (dataType.toLowerCase()) {
            case "double" -> 0.0;
            case "int", "integer" -> 0;
            case "string" -> "";
            case "boolean", "bool" -> false;
            default -> null;
        };
    }

    /**
     * Converts a raw value to the specified data type.
     * <p>
     * Used after reading a property value from the Capella model to ensure
     * it matches the type expected by the simulation engine.
     *
     * @param value    the raw value from the model
     * @param dataType the target data type
     * @return the converted value
     */
    public Object convertValue(Object value, String dataType) {
        if (value == null) {
            return getDefaultValue(dataType);
        }

        return switch (dataType.toLowerCase()) {
            case "double" -> {
                if (value instanceof Number n) {
                    yield n.doubleValue();
                }
                yield Double.parseDouble(value.toString());
            }
            case "int", "integer" -> {
                if (value instanceof Number n) {
                    yield n.intValue();
                }
                yield Integer.parseInt(value.toString());
            }
            case "string" -> value.toString();
            case "boolean", "bool" -> {
                if (value instanceof Boolean b) {
                    yield b;
                }
                yield Boolean.parseBoolean(value.toString());
            }
            default -> value;
        };
    }
}

package com.capellaagent.simulation.orchestrator;

/**
 * Data Transfer Object mapping a simulation parameter to a Capella model element property.
 * <p>
 * Defines how a named simulation parameter's value should be extracted from a
 * specific property of a Capella model element identified by UUID.
 *
 * @param paramName    the simulation parameter name (used in the engine workspace)
 * @param elementUuid  the UUID of the Capella model element to read from
 * @param propertyPath the EMF property path within the element (e.g., "ownedPropertyValues.value")
 * @param dataType     the expected data type: "double", "int", "string", or "boolean"
 */
public record ParameterMapping(
        String paramName,
        String elementUuid,
        String propertyPath,
        String dataType
) {

    /**
     * Validates this mapping has all required fields.
     *
     * @return {@code true} if all fields are non-null and non-empty
     */
    public boolean isValid() {
        return paramName != null && !paramName.isEmpty()
                && elementUuid != null && !elementUuid.isEmpty()
                && propertyPath != null && !propertyPath.isEmpty()
                && dataType != null && !dataType.isEmpty();
    }

    /**
     * Returns a human-readable description of this mapping.
     *
     * @return a string describing the parameter mapping
     */
    @Override
    public String toString() {
        return "ParameterMapping{" + paramName + " <- " + elementUuid
                + "." + propertyPath + " (" + dataType + ")}";
    }
}

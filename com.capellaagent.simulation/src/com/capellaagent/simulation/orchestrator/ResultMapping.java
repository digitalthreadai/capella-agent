package com.capellaagent.simulation.orchestrator;

/**
 * Data Transfer Object mapping a simulation output to a Capella model element property.
 * <p>
 * Defines how a named simulation output should be written back to a specific
 * property of a Capella model element identified by UUID.
 *
 * @param outputName   the simulation output variable name
 * @param elementUuid  the UUID of the Capella model element to write to
 * @param propertyPath the EMF property path within the element
 */
public record ResultMapping(
        String outputName,
        String elementUuid,
        String propertyPath
) {

    /**
     * Validates this mapping has all required fields.
     *
     * @return {@code true} if all fields are non-null and non-empty
     */
    public boolean isValid() {
        return outputName != null && !outputName.isEmpty()
                && elementUuid != null && !elementUuid.isEmpty()
                && propertyPath != null && !propertyPath.isEmpty();
    }

    @Override
    public String toString() {
        return "ResultMapping{" + outputName + " -> " + elementUuid
                + "." + propertyPath + "}";
    }
}

package com.capellaagent.core.staging;

/**
 * A single proposed change in an architecture diff.
 * <p>
 * Immutable value object. Validated by the proposal tool before staging.
 */
public record ProposedChange(
        /** Operation: "CREATE", "MODIFY", or "DELETE". */
        String operation,
        /** The ARCADIA layer: "oa", "sa", "la", "pa". */
        String layer,
        /** The EMF type name, e.g. "LogicalComponent". */
        String elementType,
        /** The element name (new name for MODIFY). */
        String name,
        /** UUID of the parent element (for CREATE). May be null for DELETE. */
        String parentUuid,
        /** UUID of the element to modify or delete. Null for CREATE. */
        String targetUuid,
        /** Human-readable rationale, e.g. requirement IDs. */
        String rationale
) {}

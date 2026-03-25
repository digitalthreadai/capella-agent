package com.capellaagent.core.security;

/**
 * Defines the access mode for agent operations on the Capella model.
 * <p>
 * The access mode determines whether an agent can modify the model or
 * is restricted to read-only operations. This is a critical safety
 * mechanism preventing unintended model mutations.
 */
public enum AccessMode {

    /**
     * The agent can only read model data. Any attempt to create, update,
     * or delete model elements will be rejected.
     */
    READ_ONLY,

    /**
     * The agent can both read and write model data. Write operations are
     * executed within EMF transactions and are audited.
     */
    READ_WRITE
}

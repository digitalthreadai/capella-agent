package com.capellaagent.core.bus;

/**
 * Event fired when a Capella model element is created, updated, or deleted.
 * <p>
 * This event enables reactive updates: agents and UI components can subscribe
 * to model changes to refresh views, trigger validations, or synchronize
 * with external systems.
 */
public class ModelChangedEvent extends AgentEvent {

    /** Change type constant for element creation. */
    public static final String CREATE = "CREATE";

    /** Change type constant for element modification. */
    public static final String UPDATE = "UPDATE";

    /** Change type constant for element deletion. */
    public static final String DELETE = "DELETE";

    private final String elementUuid;
    private final String changeType;

    /**
     * Constructs a new model changed event.
     *
     * @param source      the source that triggered the change (e.g., tool name)
     * @param elementUuid the UUID of the affected model element
     * @param changeType  one of {@link #CREATE}, {@link #UPDATE}, {@link #DELETE}
     */
    public ModelChangedEvent(String source, String elementUuid, String changeType) {
        super(source);
        this.elementUuid = elementUuid;
        this.changeType = changeType;
    }

    /**
     * Returns the UUID of the affected model element.
     *
     * @return the element UUID
     */
    public String getElementUuid() {
        return elementUuid;
    }

    /**
     * Returns the type of change.
     *
     * @return "CREATE", "UPDATE", or "DELETE"
     */
    public String getChangeType() {
        return changeType;
    }

    @Override
    public String toString() {
        return "ModelChangedEvent{source='" + getSource() +
                "', elementUuid='" + elementUuid +
                "', changeType='" + changeType + "'}";
    }
}

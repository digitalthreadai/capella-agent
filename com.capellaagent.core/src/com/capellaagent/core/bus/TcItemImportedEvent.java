package com.capellaagent.core.bus;

/**
 * Event fired when a Teamcenter item has been imported or linked to a Capella element.
 * <p>
 * Enables the TC bridge and other components to react to import operations,
 * for example updating traceability matrices or triggering validation checks.
 */
public class TcItemImportedEvent extends AgentEvent {

    private final String tcUid;
    private final String capellaUuid;
    private final String itemType;

    /**
     * Constructs a new TC item imported event.
     *
     * @param source       the event source (e.g., "tc-bridge")
     * @param tcUid        the Teamcenter item UID
     * @param capellaUuid  the UUID of the linked Capella element
     * @param itemType     the Teamcenter item type (e.g., "Requirement", "Part")
     */
    public TcItemImportedEvent(String source, String tcUid, String capellaUuid, String itemType) {
        super(source);
        this.tcUid = tcUid;
        this.capellaUuid = capellaUuid;
        this.itemType = itemType;
    }

    /**
     * Returns the Teamcenter item UID.
     *
     * @return the TC UID
     */
    public String getTcUid() {
        return tcUid;
    }

    /**
     * Returns the UUID of the linked Capella element.
     *
     * @return the Capella element UUID
     */
    public String getCapellaUuid() {
        return capellaUuid;
    }

    /**
     * Returns the Teamcenter item type.
     *
     * @return the item type string
     */
    public String getItemType() {
        return itemType;
    }

    @Override
    public String toString() {
        return "TcItemImportedEvent{source='" + getSource() +
                "', tcUid='" + tcUid +
                "', capellaUuid='" + capellaUuid +
                "', itemType='" + itemType + "'}";
    }
}

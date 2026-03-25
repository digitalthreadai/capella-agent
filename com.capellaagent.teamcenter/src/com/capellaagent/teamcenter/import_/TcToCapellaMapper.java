package com.capellaagent.teamcenter.import_;

import com.google.gson.JsonObject;

/**
 * Maps Teamcenter object types and properties to Capella element types and attributes.
 * <p>
 * Acts as a translation layer between the Teamcenter data model and the Capella
 * MBSE model, producing intermediate Data Transfer Objects that the
 * {@link RequirementImporter} and {@link PartImporter} consume.
 * <p>
 * The mapping rules can be extended by subclassing or by providing custom
 * mapping configuration in the future.
 */
public class TcToCapellaMapper {

    /**
     * Teamcenter object type for requirements.
     * PLACEHOLDER: Actual type name depends on Teamcenter configuration.
     */
    private static final String TC_TYPE_REQUIREMENT = "Requirement";

    /**
     * Teamcenter object type for requirement specifications.
     * PLACEHOLDER: Actual type name depends on Teamcenter configuration.
     */
    private static final String TC_TYPE_REQ_SPEC = "RequirementSpec";

    /**
     * Teamcenter object type for parts (Item).
     * PLACEHOLDER: Actual type name depends on Teamcenter configuration.
     */
    private static final String TC_TYPE_ITEM = "Item";

    /**
     * Maps a Teamcenter requirement object to a Capella requirement DTO.
     * <p>
     * The returned DTO contains the fields needed by {@link RequirementImporter}
     * to create a Capella requirement element:
     * <ul>
     *   <li>{@code name} - the requirement name</li>
     *   <li>{@code description} - the requirement text</li>
     *   <li>{@code type} - the mapped Capella requirement type</li>
     *   <li>{@code tcUid} - the source Teamcenter UID for traceability</li>
     *   <li>{@code tcType} - the original Teamcenter type</li>
     *   <li>{@code priority} - mapped priority value</li>
     *   <li>{@code status} - mapped status value</li>
     * </ul>
     *
     * @param tcReq the Teamcenter requirement JSON object
     * @return a DTO containing mapped Capella requirement attributes
     */
    public JsonObject mapRequirement(JsonObject tcReq) {
        JsonObject dto = new JsonObject();

        // Map basic properties
        dto.addProperty("name", getStringProperty(tcReq, "object_name", "Unnamed Requirement"));
        dto.addProperty("description", getStringProperty(tcReq, "body_text", ""));
        dto.addProperty("tcUid", getStringProperty(tcReq, "uid", ""));
        dto.addProperty("tcType", getStringProperty(tcReq, "type", TC_TYPE_REQUIREMENT));

        // PLACEHOLDER: Map Teamcenter requirement type to Capella requirement type.
        // Capella Requirements VP uses types like "SystemFunctionalRequirement",
        // "SystemNonFunctionalRequirement", etc.
        String tcType = getStringProperty(tcReq, "type", "");
        dto.addProperty("type", mapRequirementType(tcType));

        // Map priority
        String tcPriority = getStringProperty(tcReq, "priority", "");
        dto.addProperty("priority", mapPriority(tcPriority));

        // Map status
        String tcStatus = getStringProperty(tcReq, "status", "");
        dto.addProperty("status", mapStatus(tcStatus));

        // Preserve the original Teamcenter ID for reference
        dto.addProperty("tcItemId", getStringProperty(tcReq, "item_id", ""));

        return dto;
    }

    /**
     * Maps a Teamcenter part (Item) to a Capella PhysicalComponent DTO.
     * <p>
     * The returned DTO contains:
     * <ul>
     *   <li>{@code name} - the component name</li>
     *   <li>{@code description} - the item description</li>
     *   <li>{@code tcUid} - the source Teamcenter UID</li>
     *   <li>{@code nature} - the Capella PhysicalComponent nature (NODE/BEHAVIOR)</li>
     *   <li>{@code partNumber} - the Teamcenter item ID / part number</li>
     * </ul>
     *
     * @param tcPart the Teamcenter part/item JSON object
     * @return a DTO containing mapped Capella component attributes
     */
    public JsonObject mapPart(JsonObject tcPart) {
        JsonObject dto = new JsonObject();

        dto.addProperty("name", getStringProperty(tcPart, "object_name", "Unnamed Part"));
        dto.addProperty("description", getStringProperty(tcPart, "object_desc", ""));
        dto.addProperty("tcUid", getStringProperty(tcPart, "uid", ""));
        dto.addProperty("tcType", getStringProperty(tcPart, "type", TC_TYPE_ITEM));
        dto.addProperty("partNumber", getStringProperty(tcPart, "item_id", ""));

        // PLACEHOLDER: Determine the Capella PhysicalComponent nature.
        // Heuristic: if the Teamcenter type contains "Software" or "Electronic",
        // use BEHAVIOR; otherwise use NODE.
        String type = getStringProperty(tcPart, "type", "");
        dto.addProperty("nature", type.toLowerCase().contains("software") ? "BEHAVIOR" : "NODE");

        return dto;
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    /**
     * Maps a Teamcenter requirement type to a Capella requirement type.
     * PLACEHOLDER: Extend with actual type mappings from your Teamcenter configuration.
     */
    private String mapRequirementType(String tcType) {
        return switch (tcType.toLowerCase()) {
            case "functional requirement", "functionalrequirement" -> "SystemFunctionalRequirement";
            case "performance requirement", "performancerequirement" -> "SystemNonFunctionalRequirement";
            case "interface requirement", "interfacerequirement" -> "SystemInterfaceRequirement";
            default -> "SystemUserRequirement";
        };
    }

    /**
     * Maps a Teamcenter priority value to a normalized priority.
     */
    private String mapPriority(String tcPriority) {
        return switch (tcPriority.toLowerCase()) {
            case "high", "1", "critical" -> "HIGH";
            case "medium", "2", "normal" -> "MEDIUM";
            case "low", "3", "minor" -> "LOW";
            default -> "UNDEFINED";
        };
    }

    /**
     * Maps a Teamcenter status to a normalized status.
     */
    private String mapStatus(String tcStatus) {
        return switch (tcStatus.toLowerCase()) {
            case "approved", "released" -> "APPROVED";
            case "draft", "in work" -> "DRAFT";
            case "review", "in review" -> "IN_REVIEW";
            case "obsolete", "retired" -> "OBSOLETE";
            default -> "DRAFT";
        };
    }

    private String getStringProperty(JsonObject obj, String key, String defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return defaultValue;
    }
}

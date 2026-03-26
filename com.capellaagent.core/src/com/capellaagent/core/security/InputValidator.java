package com.capellaagent.core.security;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates and sanitizes user input before it reaches the Capella model.
 * <p>
 * Prevents injection of invalid characters into model element names and
 * descriptions, and validates enumerated parameters like layer and element type.
 * All tool implementations should use these methods before creating or updating
 * model elements.
 */
public final class InputValidator {

    /** Maximum allowed length for element names. */
    private static final int MAX_NAME_LENGTH = 500;

    /** Maximum allowed length for element descriptions. */
    private static final int MAX_DESCRIPTION_LENGTH = 10000;

    /** Valid ARCADIA architecture layers. */
    private static final Set<String> VALID_LAYERS = Set.of("oa", "sa", "la", "pa");

    /** Valid element type filters for queries. */
    private static final Set<String> VALID_ELEMENT_TYPES = Set.of(
            "function", "component", "actor", "exchange", "capability", "all");

    /**
     * Pattern matching XML-invalid characters (control chars except tab, newline, carriage return).
     */
    private static final Pattern XML_INVALID_CHARS = Pattern.compile(
            "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    /**
     * Pattern matching all control characters including tab/newline/CR.
     */
    private static final Pattern CONTROL_CHARS = Pattern.compile(
            "[\\x00-\\x1F\\x7F]");

    private InputValidator() {
    }

    /**
     * Sanitizes a name for use as a Capella element name.
     * <p>
     * Strips XML-invalid characters, trims whitespace, and enforces
     * the maximum length of {@value #MAX_NAME_LENGTH} characters.
     *
     * @param name the raw name input
     * @return the sanitized name
     * @throws IllegalArgumentException if the name is null or empty after sanitization
     */
    public static String sanitizeName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Element name must not be null");
        }

        // Strip XML-invalid characters
        String sanitized = XML_INVALID_CHARS.matcher(name).replaceAll("");

        // Trim whitespace
        sanitized = sanitized.trim();

        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Element name must not be empty after sanitization");
        }

        // Enforce max length
        if (sanitized.length() > MAX_NAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_NAME_LENGTH);
        }

        return sanitized;
    }

    /**
     * Sanitizes a description for use as a Capella element description.
     * <p>
     * Strips control characters (except newlines and tabs for formatting),
     * trims whitespace, and enforces the maximum length of
     * {@value #MAX_DESCRIPTION_LENGTH} characters.
     *
     * @param desc the raw description input
     * @return the sanitized description, or empty string if null
     */
    public static String sanitizeDescription(String desc) {
        if (desc == null) {
            return "";
        }

        // Strip control chars but preserve newlines (\n), carriage returns (\r), and tabs (\t)
        StringBuilder sb = new StringBuilder(desc.length());
        for (int i = 0; i < desc.length(); i++) {
            char c = desc.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t' || c >= 0x20 && c != 0x7F) {
                sb.append(c);
            }
        }

        String sanitized = sb.toString().trim();

        // Enforce max length
        if (sanitized.length() > MAX_DESCRIPTION_LENGTH) {
            sanitized = sanitized.substring(0, MAX_DESCRIPTION_LENGTH);
        }

        return sanitized;
    }

    /**
     * Validates that the given layer string is a valid ARCADIA layer identifier.
     *
     * @param layer the layer to validate
     * @return the validated layer in lowercase
     * @throws IllegalArgumentException if the layer is invalid
     */
    public static String validateLayer(String layer) {
        if (layer == null || layer.isBlank()) {
            throw new IllegalArgumentException(
                    "Layer must not be null or empty. Valid values: oa, sa, la, pa");
        }
        String normalized = layer.trim().toLowerCase();
        if (!VALID_LAYERS.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid layer '" + layer + "'. Must be one of: oa, sa, la, pa");
        }
        return normalized;
    }

    /**
     * Validates that the given UUID is non-null, non-empty, and has a reasonable format.
     *
     * @param uuid the UUID to validate
     * @return the trimmed UUID
     * @throws IllegalArgumentException if the UUID is invalid
     */
    public static String validateUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            throw new IllegalArgumentException("UUID must not be null or empty");
        }
        String trimmed = uuid.trim();
        // Capella UUIDs are typically 36-char standard UUIDs or shorter internal IDs
        // Reject obviously invalid inputs (too long or containing dangerous chars)
        if (trimmed.length() > 200) {
            throw new IllegalArgumentException(
                    "UUID is too long (" + trimmed.length() + " chars). Maximum is 200.");
        }
        // Reject strings with control characters or whitespace
        if (CONTROL_CHARS.matcher(trimmed).find() || trimmed.contains(" ")) {
            throw new IllegalArgumentException(
                    "UUID contains invalid characters. Must not contain control characters or spaces.");
        }
        return trimmed;
    }

    /**
     * Validates that the given element type is a recognized type filter.
     *
     * @param type the element type to validate
     * @return the validated type in lowercase
     * @throws IllegalArgumentException if the type is invalid
     */
    public static String validateElementType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException(
                    "Element type must not be null or empty. Valid values: "
                    + String.join(", ", VALID_ELEMENT_TYPES));
        }
        String normalized = type.trim().toLowerCase();
        if (!VALID_ELEMENT_TYPES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid element type '" + type + "'. Must be one of: "
                    + String.join(", ", VALID_ELEMENT_TYPES));
        }
        return normalized;
    }
}

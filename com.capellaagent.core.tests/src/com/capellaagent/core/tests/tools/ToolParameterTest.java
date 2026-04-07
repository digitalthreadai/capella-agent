package com.capellaagent.core.tests.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.capellaagent.core.tools.ToolParameter;

/**
 * Unit tests for {@link ToolParameter}.
 */
class ToolParameterTest {

    @Test
    @DisplayName("requiredString creates a required string parameter")
    void requiredStringMarksRequired() {
        ToolParameter p = ToolParameter.requiredString("name", "the name");
        assertEquals("name", p.getName());
        assertEquals("the name", p.getDescription());
        assertEquals("string", p.getType());
        assertTrue(p.isRequired());
    }

    @Test
    @DisplayName("optionalString creates an optional string parameter")
    void optionalStringMarksOptional() {
        ToolParameter p = ToolParameter.optionalString("note", "a note");
        assertEquals("string", p.getType());
        assertFalse(p.isRequired());
    }

    @Test
    @DisplayName("requiredInteger has type=integer")
    void requiredIntegerType() {
        ToolParameter p = ToolParameter.requiredInteger("count", "how many");
        assertEquals("integer", p.getType());
        assertTrue(p.isRequired());
    }

    @Test
    @DisplayName("requiredBoolean has type=boolean")
    void requiredBooleanType() {
        ToolParameter p = ToolParameter.requiredBoolean("confirm", "confirm action");
        assertEquals("boolean", p.getType());
    }

    @Test
    @DisplayName("optionalStringArray has type=array")
    void optionalStringArrayType() {
        ToolParameter p = ToolParameter.optionalStringArray("ids", "a list of ids");
        assertEquals("array", p.getType());
        assertFalse(p.isRequired());
    }

    @Test
    @DisplayName("requiredEnum stores enum values")
    void requiredEnumStoresValues() {
        ToolParameter p = ToolParameter.requiredEnum("layer", "architecture layer",
            List.of("oa", "sa", "la", "pa"));
        assertEquals("string", p.getType());
        assertTrue(p.isRequired());
        assertEquals(4, p.getEnumValues().size());
        assertTrue(p.getEnumValues().contains("pa"));
    }

    @Test
    @DisplayName("optionalStringWithDefault stores default value")
    void optionalStringWithDefaultStoresDefault() {
        ToolParameter p = ToolParameter.optionalStringWithDefault("color", "the color", "blue");
        assertEquals("blue", p.getDefaultValue());
        assertFalse(p.isRequired());
    }

    @Test
    @DisplayName("getEnumValues returns null when no enum is set (current behavior)")
    void getEnumValuesNullByDefault() {
        ToolParameter p = ToolParameter.requiredString("x", "y");
        // ToolParameter currently uses null to signal "no enum constraint";
        // documenting the contract here so any future change is intentional.
        assertNull(p.getEnumValues());
    }
}

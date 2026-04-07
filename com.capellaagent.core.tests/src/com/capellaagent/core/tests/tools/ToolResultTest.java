package com.capellaagent.core.tests.tools;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonObject;

/**
 * Unit tests for {@link ToolResult}.
 */
class ToolResultTest {

    @Test
    @DisplayName("success(data) returns isSuccess=true with data")
    void successWithData() {
        JsonObject data = new JsonObject();
        data.addProperty("count", 5);
        ToolResult r = ToolResult.success(data);
        assertTrue(r.isSuccess());
        assertNotNull(r.getData());
        assertEquals(5, r.getData().get("count").getAsInt());
        assertNull(r.getErrorMessage());
    }

    @Test
    @DisplayName("successMessage(s) wraps a string")
    void successMessageWrapsString() {
        ToolResult r = ToolResult.successMessage("done");
        assertTrue(r.isSuccess());
        assertNotNull(r.getData());
    }

    @Test
    @DisplayName("error(msg) returns isSuccess=false with the error message")
    void errorMessage() {
        ToolResult r = ToolResult.error("boom");
        assertFalse(r.isSuccess());
        assertEquals("boom", r.getErrorMessage());
    }

    @Test
    @DisplayName("toJson serializes a success result")
    void toJsonSuccess() {
        JsonObject data = new JsonObject();
        data.addProperty("name", "alpha");
        ToolResult r = ToolResult.success(data);
        JsonObject json = r.toJson();
        assertNotNull(json);
        // Should at least contain the data fields
        assertTrue(json.toString().contains("alpha"));
    }

    @Test
    @DisplayName("toJson serializes an error result")
    void toJsonError() {
        ToolResult r = ToolResult.error("nope");
        JsonObject json = r.toJson();
        assertNotNull(json);
        assertTrue(json.toString().contains("nope"));
    }

    @Test
    @DisplayName("toString does not throw on success")
    void toStringSuccess() {
        ToolResult r = ToolResult.success(new JsonObject());
        assertNotNull(r.toString());
    }

    @Test
    @DisplayName("toString does not throw on error")
    void toStringError() {
        ToolResult r = ToolResult.error("e");
        assertNotNull(r.toString());
    }
}

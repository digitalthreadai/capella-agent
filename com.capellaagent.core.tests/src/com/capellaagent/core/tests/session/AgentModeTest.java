package com.capellaagent.core.tests.session;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.capellaagent.core.session.AgentMode;

/**
 * Unit tests for {@link AgentMode}.
 */
class AgentModeTest {

    @Test
    @DisplayName("GENERAL mode has all non-null fields and non-empty starter prompts")
    void generalModeHasAllFields() {
        AgentMode mode = AgentMode.GENERAL;
        assertNotNull(mode.displayName());
        assertNotNull(mode.shortDescription());
        assertNotNull(mode.systemPrompt());
        assertFalse(mode.systemPrompt().isEmpty());
        assertNotNull(mode.preferredToolCategories());
        assertNotNull(mode.starterPrompts());
        assertFalse(mode.starterPrompts().isEmpty(),
            "GENERAL mode must provide starter prompts for empty state");
    }

    @Test
    @DisplayName("SUSTAINMENT mode system prompt instructs agent not to invent UUIDs")
    void sustainmentPromptForbidsFakeUuids() {
        String prompt = AgentMode.SUSTAINMENT.systemPrompt();
        assertTrue(prompt.toLowerCase().contains("never invent uuid")
            || prompt.toLowerCase().contains("verbatim"),
            "SUSTAINMENT mode must explicitly forbid invented UUIDs (hallucination guard)");
    }

    @Test
    @DisplayName("SUSTAINMENT mode references the right tool chain")
    void sustainmentPromptReferencesTools() {
        String prompt = AgentMode.SUSTAINMENT.systemPrompt();
        assertTrue(prompt.contains("get_functional_chain"));
        assertTrue(prompt.contains("impact_analysis"));
        assertTrue(prompt.contains("get_traceability"));
        // The two sustainment-specific tools (added in Week 4)
        assertTrue(prompt.contains("lookup_fault_symptom"));
        assertTrue(prompt.contains("classify_fault_ata"));
    }

    @Test
    @DisplayName("SUSTAINMENT starter prompts use IFE-compatible symptoms, not landing gear")
    void sustainmentStartersMatchIfeModel() {
        String allStarters = String.join(" ", AgentMode.SUSTAINMENT.starterPrompts())
            .toLowerCase();
        // IFE-compatible symptoms (per demo/IFE_MODEL_INVENTORY.md)
        assertTrue(
            allStarters.contains("seat tv")
            || allStarters.contains("cabin")
            || allStarters.contains("vod")
            || allStarters.contains("audio"),
            "starters must reference IFE elements, not fictional ones");
        // The original broken symptom that the plan review caught
        assertFalse(allStarters.contains("nose wheel"),
            "IFE model has no landing gear — starters must not mention it");
        assertFalse(allStarters.contains("landing gear"),
            "IFE model has no landing gear — starters must not mention it");
    }

    @Test
    @DisplayName("Every mode has a non-empty displayName and description")
    void everyModeHasNameAndDescription() {
        for (AgentMode mode : AgentMode.values()) {
            assertNotNull(mode.displayName(), mode + " displayName");
            assertFalse(mode.displayName().isEmpty(), mode + " displayName empty");
            assertNotNull(mode.shortDescription(), mode + " description");
            assertFalse(mode.shortDescription().isEmpty(), mode + " description empty");
        }
    }

    @Test
    @DisplayName("SUSTAINMENT mode prefers analysis + model_read categories")
    void sustainmentPrefersCorrectCategories() {
        var cats = AgentMode.SUSTAINMENT.preferredToolCategories();
        assertTrue(cats.contains("model_read"));
        assertTrue(cats.contains("analysis"));
    }

    @Test
    @DisplayName("GENERAL mode has empty preferredToolCategories (all categories)")
    void generalHasEmptyPreferredCategories() {
        assertTrue(AgentMode.GENERAL.preferredToolCategories().isEmpty(),
            "GENERAL should expose all tool categories via empty list");
    }
}

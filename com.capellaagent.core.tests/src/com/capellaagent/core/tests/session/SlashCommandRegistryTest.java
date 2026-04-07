package com.capellaagent.core.tests.session;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.capellaagent.core.session.SlashCommandRegistry;
import com.capellaagent.core.session.SlashCommandRegistry.SlashCommand;

/**
 * Unit tests for {@link SlashCommandRegistry}.
 */
class SlashCommandRegistryTest {

    @Test
    @DisplayName("defaults() provides the 9 standard commands")
    void defaultsProvidesStandardCommands() {
        SlashCommandRegistry r = SlashCommandRegistry.defaults();
        assertTrue(r.lookup("/help").isPresent());
        assertTrue(r.lookup("/tools").isPresent());
        assertTrue(r.lookup("/clear").isPresent());
        assertTrue(r.lookup("/export").isPresent());
        assertTrue(r.lookup("/general").isPresent());
        assertTrue(r.lookup("/sustainment").isPresent());
        assertTrue(r.lookup("/diff").isPresent());
        assertTrue(r.lookup("/undo").isPresent());
        assertTrue(r.lookup("/cancel").isPresent());
    }

    @Test
    @DisplayName("aliases resolve to canonical commands")
    void aliasesResolve() {
        SlashCommandRegistry r = SlashCommandRegistry.defaults();
        assertEquals("/help", r.lookup("/?").orElseThrow().name());
        assertEquals("/clear", r.lookup("/cls").orElseThrow().name());
        assertEquals("/sustainment", r.lookup("/sust").orElseThrow().name());
    }

    @Test
    @DisplayName("lookup returns empty for unknown commands")
    void lookupEmptyForUnknown() {
        SlashCommandRegistry r = SlashCommandRegistry.defaults();
        assertTrue(r.lookup("/nonexistent").isEmpty());
        assertTrue(r.lookup(null).isEmpty());
    }

    @Test
    @DisplayName("suggest returns matching prefix, dedupes aliases")
    void suggestReturnsMatches() {
        SlashCommandRegistry r = SlashCommandRegistry.defaults();
        List<SlashCommand> hits = r.suggest("/s");
        // Should include /sustainment (and NOT include its aliases as separate entries)
        long sustainmentCount = hits.stream()
            .filter(c -> c.name().equals("/sustainment")).count();
        assertEquals(1, sustainmentCount, "aliases must be deduplicated");
    }

    @Test
    @DisplayName("suggest returns empty for non-slash prefix")
    void suggestRejectsNonSlash() {
        SlashCommandRegistry r = SlashCommandRegistry.defaults();
        assertTrue(r.suggest("help").isEmpty(), "prefix without leading / returns empty");
        assertTrue(r.suggest("").isEmpty());
        assertTrue(r.suggest(null).isEmpty());
    }

    @Test
    @DisplayName("parse recognizes a leading slash command in a user message")
    void parseRecognizesCommand() {
        SlashCommandRegistry r = SlashCommandRegistry.defaults();
        assertTrue(r.parse("/clear").isPresent());
        assertTrue(r.parse("/clear all the things").isPresent(),
            "args after the command should still match");
        assertEquals("/clear", r.parse("/clear").orElseThrow().name());
    }

    @Test
    @DisplayName("parse returns empty for non-slash messages")
    void parseEmptyForPlainMessage() {
        SlashCommandRegistry r = SlashCommandRegistry.defaults();
        assertTrue(r.parse("list all functions").isEmpty());
        assertTrue(r.parse("").isEmpty());
        assertTrue(r.parse(null).isEmpty());
        // A slash that's not a known command
        assertTrue(r.parse("/nothere").isEmpty());
    }

    @Test
    @DisplayName("listAll returns 9 canonical commands")
    void listAllReturnsCanonical() {
        SlashCommandRegistry r = SlashCommandRegistry.defaults();
        List<SlashCommand> all = r.listAll();
        assertEquals(9, all.size(), "defaults should have exactly 9 canonical commands");
    }

    @Test
    @DisplayName("custom registry: add and lookup a command")
    void customRegistration() {
        SlashCommandRegistry r = new SlashCommandRegistry();
        r.register(new SlashCommand("/foo", "do foo", "foo_action", List.of("/f")));
        assertTrue(r.lookup("/foo").isPresent());
        assertTrue(r.lookup("/f").isPresent());
        assertEquals("foo_action", r.lookup("/foo").orElseThrow().actionTag());
    }

    @Test
    @DisplayName("parse ignores surrounding whitespace")
    void parseIgnoresWhitespace() {
        SlashCommandRegistry r = SlashCommandRegistry.defaults();
        assertTrue(r.parse("  /help  ").isPresent());
    }
}

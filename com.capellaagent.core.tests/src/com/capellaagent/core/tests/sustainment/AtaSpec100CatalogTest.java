package com.capellaagent.core.tests.sustainment;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.capellaagent.core.sustainment.AtaSpec100Catalog;

/**
 * Unit tests for {@link AtaSpec100Catalog}.
 */
class AtaSpec100CatalogTest {

    @Test
    @DisplayName("chapterName returns known chapters")
    void chapterNameReturnsKnownChapters() {
        assertEquals("Landing Gear", AtaSpec100Catalog.chapterName(32));
        assertEquals("Cabin Systems", AtaSpec100Catalog.chapterName(44));
        assertEquals("Flight Controls", AtaSpec100Catalog.chapterName(27));
        assertEquals("Engine", AtaSpec100Catalog.chapterName(72));
    }

    @Test
    @DisplayName("chapterName returns null for unknown chapters")
    void chapterNameNullForUnknown() {
        assertNull(AtaSpec100Catalog.chapterName(999));
        assertNull(AtaSpec100Catalog.chapterName(0));
    }

    @Test
    @DisplayName("classify matches Cabin Systems for IFE-related symptoms")
    void classifyMatchesIfeSymptoms() {
        assertEquals(44, AtaSpec100Catalog.classify(
            "The Seat TV in row 14 is rebooting every time we try to play a VOD movie"));
        assertEquals(44, AtaSpec100Catalog.classify(
            "cabin video display unit shows a black screen"));
        assertEquals(44, AtaSpec100Catalog.classify(
            "Cabin Management Unit has intermittent connectivity"));
        assertEquals(44, AtaSpec100Catalog.classify(
            "Available VOD Movies List is showing yesterday's titles"));
    }

    @Test
    @DisplayName("classify matches Landing Gear")
    void classifyMatchesLandingGear() {
        assertEquals(32, AtaSpec100Catalog.classify("nose wheel steering fault"));
        assertEquals(32, AtaSpec100Catalog.classify("landing gear retraction slow"));
        assertEquals(32, AtaSpec100Catalog.classify("brake temperature warning"));
    }

    @Test
    @DisplayName("classify matches Hydraulic / Electrical / Engine")
    void classifyMatchesMajorSystems() {
        assertEquals(29, AtaSpec100Catalog.classify("hydraulic pressure drop"));
        assertEquals(24, AtaSpec100Catalog.classify("electrical bus fault"));
        assertEquals(72, AtaSpec100Catalog.classify("engine temperature out of range"));
        assertEquals(49, AtaSpec100Catalog.classify("APU failed to start"));
    }

    @Test
    @DisplayName("classify is case-insensitive")
    void classifyIsCaseInsensitive() {
        assertEquals(44, AtaSpec100Catalog.classify("SEAT TV"));
        assertEquals(44, AtaSpec100Catalog.classify("Seat TV"));
        assertEquals(44, AtaSpec100Catalog.classify("seat tv"));
    }

    @Test
    @DisplayName("classify returns -1 for unknown or empty input")
    void classifyReturnsMinusOneForUnknown() {
        assertEquals(-1, AtaSpec100Catalog.classify(""));
        assertEquals(-1, AtaSpec100Catalog.classify(null));
        assertEquals(-1, AtaSpec100Catalog.classify("blah blah blah"));
    }

    @Test
    @DisplayName("classifyAsLabel formats as 'N - Name'")
    void classifyAsLabelFormats() {
        String label = AtaSpec100Catalog.classifyAsLabel("Seat TV is rebooting");
        assertTrue(label.contains("44"));
        assertTrue(label.contains("Cabin"));
        assertEquals("Unclassified", AtaSpec100Catalog.classifyAsLabel("gibberish"));
    }

    @Test
    @DisplayName("allChapters returns the full catalog")
    void allChaptersReturnsFullCatalog() {
        var all = AtaSpec100Catalog.allChapters();
        assertTrue(all.size() >= 20, "should have at least 20 chapters, got " + all.size());
        assertTrue(all.containsKey(21));
        assertTrue(all.containsKey(44));
        assertTrue(all.containsKey(80));
    }

    @Test
    @DisplayName("allChapters is immutable")
    void allChaptersImmutable() {
        var all = AtaSpec100Catalog.allChapters();
        assertThrows(UnsupportedOperationException.class,
            () -> all.put(999, "Hack"));
    }

    @Test
    @DisplayName("most-specific keywords win: 'seat tv' matches 44 not 33")
    void mostSpecificKeywordsWin() {
        // "seat tv" could match 'light' substring if naively ordered.
        // Verify specific-first ordering in the keyword index.
        assertEquals(44, AtaSpec100Catalog.classify("seat tv flashing"));
    }
}

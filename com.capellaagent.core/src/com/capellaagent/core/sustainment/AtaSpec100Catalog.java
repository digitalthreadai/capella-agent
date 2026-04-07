package com.capellaagent.core.sustainment;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * ATA Spec 100 chapter catalog for fault classification.
 * <p>
 * ATA Spec 100 is the industry-standard numbering for aircraft system
 * documentation. Sustainment engineers use these chapter numbers daily when
 * filing or looking up fault reports. This catalog provides the names, plus
 * a keyword index for natural-language lookups (the agent passes user
 * descriptions like "nose wheel steering" and gets "32 - Landing Gear" back).
 * <p>
 * Data is embedded as a Java constant (no external JSON file) so the plugin
 * works offline and the catalog is version-controlled alongside the code.
 * The catalog is intentionally conservative — only the ATA chapters that
 * apply to commercial aircraft are included. Extend as needed for military,
 * helicopter, or business jet fault trees.
 */
public final class AtaSpec100Catalog {

    private AtaSpec100Catalog() {}

    /** Immutable chapter map: number -> name. Insertion order = ATA order. */
    private static final Map<Integer, String> CHAPTERS;
    /**
     * Keyword -> chapter number index. Lower-cased keywords matched as
     * substrings against the user's fault text. First match wins by insertion
     * order so more-specific keywords should come first.
     */
    private static final Map<String, Integer> KEYWORD_INDEX;

    static {
        Map<Integer, String> c = new LinkedHashMap<>();
        c.put(21, "Air Conditioning");
        c.put(22, "Auto Flight");
        c.put(23, "Communications");
        c.put(24, "Electrical Power");
        c.put(25, "Equipment / Furnishings");
        c.put(26, "Fire Protection");
        c.put(27, "Flight Controls");
        c.put(28, "Fuel");
        c.put(29, "Hydraulic Power");
        c.put(30, "Ice and Rain Protection");
        c.put(31, "Indicating / Recording Systems");
        c.put(32, "Landing Gear");
        c.put(33, "Lights");
        c.put(34, "Navigation");
        c.put(35, "Oxygen");
        c.put(36, "Pneumatic");
        c.put(38, "Water / Waste");
        c.put(42, "Integrated Modular Avionics");
        c.put(44, "Cabin Systems");
        c.put(45, "Central Maintenance System");
        c.put(46, "Information Systems");
        c.put(49, "Auxiliary Power");
        c.put(71, "Power Plant");
        c.put(72, "Engine");
        c.put(73, "Engine Fuel and Control");
        c.put(74, "Ignition");
        c.put(75, "Engine Bleed Air");
        c.put(77, "Engine Indicating");
        c.put(78, "Engine Exhaust");
        c.put(79, "Engine Oil");
        c.put(80, "Engine Starting");
        CHAPTERS = Collections.unmodifiableMap(c);

        // Keyword -> chapter. Order matters: specific first.
        Map<String, Integer> k = new LinkedHashMap<>();
        // Cabin/IFE (matches the IFE demo model, most-specific first)
        k.put("seat tv", 44);
        k.put("seat-back display", 44);
        k.put("seatback display", 44);
        k.put("seat back display", 44);
        k.put("cabin video", 44);
        k.put("cabin management", 44);
        k.put("cabin terminal", 44);
        k.put("cabin screen", 44);
        k.put("vod", 44);
        k.put("video on demand", 44);
        k.put("audio broadcast", 44);
        k.put("imposed video", 44);
        k.put("passenger service", 44);
        k.put("in-flight entertainment", 44);
        k.put("ife", 44);
        k.put("cabin system", 44);
        k.put("cabin", 44);
        // Avionics / IMA / info
        k.put("integrated modular avionics", 42);
        k.put("ima", 42);
        k.put("applications server", 46);
        k.put("file server", 46);
        k.put("information system", 46);
        // Landing gear
        k.put("nose wheel", 32);
        k.put("landing gear", 32);
        k.put("brake", 32);
        // Flight controls
        k.put("elevator", 27);
        k.put("aileron", 27);
        k.put("rudder", 27);
        k.put("flight control", 27);
        k.put("autopilot", 22);
        k.put("auto flight", 22);
        // Engine
        k.put("engine oil", 79);
        k.put("engine bleed", 75);
        k.put("engine start", 80);
        k.put("engine fuel", 73);
        k.put("turbine", 72);
        k.put("engine", 72);
        k.put("apu", 49);
        k.put("auxiliary power", 49);
        // Electrical / hydraulic / pneumatic
        k.put("hydraulic", 29);
        k.put("electrical", 24);
        k.put("battery", 24);
        k.put("generator", 24);
        k.put("pneumatic", 36);
        // ECS / fire / ice
        k.put("air conditioning", 21);
        k.put("pressurization", 21);
        k.put("bleed air", 36);
        k.put("fire", 26);
        k.put("smoke", 26);
        k.put("anti-ice", 30);
        k.put("de-ice", 30);
        k.put("rain", 30);
        // Nav / comms
        k.put("navigation", 34);
        k.put("gps", 34);
        k.put("ils", 34);
        k.put("radio", 23);
        k.put("comms", 23);
        k.put("communication", 23);
        // Indication / lights / oxygen / water
        k.put("indicator", 31);
        k.put("recording", 31);
        k.put("light", 33);
        k.put("oxygen", 35);
        k.put("water", 38);
        k.put("waste", 38);
        // Fuel
        k.put("fuel", 28);
        // Fall-backs
        k.put("maintenance system", 45);
        KEYWORD_INDEX = Collections.unmodifiableMap(k);
    }

    /** Returns the name of an ATA chapter, or null if unknown. */
    public static String chapterName(int chapterNumber) {
        return CHAPTERS.get(chapterNumber);
    }

    /** Returns an immutable view of all known chapters. */
    public static Map<Integer, String> allChapters() {
        return CHAPTERS;
    }

    /**
     * Classifies a free-text fault description by matching it against the
     * keyword index. Returns the first matching chapter, or -1 if no
     * keyword matched.
     *
     * @param faultText the user's fault description (any case)
     * @return an ATA chapter number, or -1 if unknown
     */
    public static int classify(String faultText) {
        if (faultText == null || faultText.isEmpty()) return -1;
        String lower = faultText.toLowerCase(Locale.ROOT);
        for (var entry : KEYWORD_INDEX.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return -1;
    }

    /**
     * Returns a human-readable classification label for a fault text:
     * e.g. "44 - Cabin Systems" or "Unclassified" if no keyword matched.
     */
    public static String classifyAsLabel(String faultText) {
        int ch = classify(faultText);
        if (ch == -1) return "Unclassified";
        return ch + " - " + CHAPTERS.get(ch);
    }
}

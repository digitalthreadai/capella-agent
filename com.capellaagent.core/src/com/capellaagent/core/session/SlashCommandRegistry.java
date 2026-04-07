package com.capellaagent.core.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of slash commands for the chat input.
 * <p>
 * The UX architect recommended slash commands as the primary mechanism for
 * discovering modes, tools, and UI actions (Issue D8) because they replace
 * five of the seven keyboard shortcuts (three of which conflicted with
 * Eclipse defaults) with a single discoverable pattern.
 * <p>
 * This class holds the model (command name → description + handler tag).
 * The UI layer (Week 6) binds keyboard focus + an autocomplete popup on
 * top of it. Testing just the model here means no SWT dependency.
 *
 * <h2>Default commands</h2>
 * <ul>
 *   <li><code>/help</code> — show command cheatsheet</li>
 *   <li><code>/tools</code> — list all available tools</li>
 *   <li><code>/clear</code> — clear the current conversation</li>
 *   <li><code>/export</code> — export chat to HTML</li>
 *   <li><code>/general</code> — switch to General Assistant mode</li>
 *   <li><code>/sustainment</code> — switch to Sustainment Engineer mode</li>
 *   <li><code>/diff</code> — show changes made in the current turn</li>
 *   <li><code>/undo</code> — undo the last model change</li>
 *   <li><code>/cancel</code> — cancel the running tool job</li>
 * </ul>
 */
public final class SlashCommandRegistry {

    /** A registered slash command. */
    public record SlashCommand(
            String name,           // e.g., "/clear"
            String description,    // shown in autocomplete popup
            String actionTag,      // opaque id the UI binds to a handler
            List<String> aliases   // alternate names, e.g., "/help" aliased as "/?"
    ) {
        public SlashCommand {
            name = name == null ? "" : name;
            description = description == null ? "" : description;
            actionTag = actionTag == null ? "" : actionTag;
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }
    }

    private final Map<String, SlashCommand> byName = new LinkedHashMap<>();

    /** Creates an empty registry. Use {@link #defaults()} for the standard set. */
    public SlashCommandRegistry() {}

    /** Returns a registry populated with the default slash commands. */
    public static SlashCommandRegistry defaults() {
        SlashCommandRegistry r = new SlashCommandRegistry();
        r.register(new SlashCommand("/help",
            "Show the slash-command cheatsheet",
            "show_help",
            List.of("/?", "/h")));
        r.register(new SlashCommand("/tools",
            "List all available agent tools",
            "list_tools",
            List.of("/t")));
        r.register(new SlashCommand("/clear",
            "Clear the current conversation",
            "clear_chat",
            List.of("/cls")));
        r.register(new SlashCommand("/export",
            "Export the chat to HTML",
            "export_chat",
            List.of()));
        r.register(new SlashCommand("/general",
            "Switch to the General Assistant mode",
            "mode_general",
            List.of("/gen")));
        r.register(new SlashCommand("/sustainment",
            "Switch to the Sustainment Engineer mode",
            "mode_sustainment",
            List.of("/sust", "/sustain")));
        r.register(new SlashCommand("/diff",
            "Show changes made in the current turn",
            "show_diff",
            List.of()));
        r.register(new SlashCommand("/undo",
            "Undo the most recent model change",
            "undo_last",
            List.of()));
        r.register(new SlashCommand("/cancel",
            "Cancel the running tool job",
            "cancel",
            List.of()));
        return r;
    }

    /** Adds a command. Aliases are also registered as lookup keys. */
    public void register(SlashCommand cmd) {
        if (cmd == null || cmd.name().isEmpty()) return;
        byName.put(cmd.name(), cmd);
        for (String a : cmd.aliases()) {
            byName.put(a, cmd);
        }
    }

    /** Looks up a command by exact name or alias. */
    public Optional<SlashCommand> lookup(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(byName.get(name));
    }

    /**
     * Returns autocomplete suggestions for a prefix like {@code "/s"}.
     * The result is alphabetically sorted by name for stable ordering.
     * Aliases are deduplicated against their canonical command.
     */
    public List<SlashCommand> suggest(String prefix) {
        if (prefix == null || prefix.isEmpty() || !prefix.startsWith("/")) {
            return Collections.emptyList();
        }
        String lower = prefix.toLowerCase();
        // Use a LinkedHashMap keyed by canonical name to dedup alias hits
        Map<String, SlashCommand> hits = new LinkedHashMap<>();
        for (var entry : byName.entrySet()) {
            if (entry.getKey().toLowerCase().startsWith(lower)) {
                hits.put(entry.getValue().name(), entry.getValue());
            }
        }
        List<SlashCommand> list = new ArrayList<>(hits.values());
        list.sort(Comparator.comparing(SlashCommand::name));
        return list;
    }

    /** Returns all registered canonical commands (aliases deduplicated). */
    public List<SlashCommand> listAll() {
        Map<String, SlashCommand> canonical = new LinkedHashMap<>();
        for (SlashCommand cmd : byName.values()) {
            canonical.putIfAbsent(cmd.name(), cmd);
        }
        return new ArrayList<>(canonical.values());
    }

    /**
     * Checks if a message looks like a slash command invocation.
     * Returns the matching SlashCommand or empty.
     */
    public Optional<SlashCommand> parse(String userMessage) {
        if (userMessage == null) return Optional.empty();
        String trimmed = userMessage.trim();
        if (!trimmed.startsWith("/")) return Optional.empty();
        // First token only
        int space = trimmed.indexOf(' ');
        String cmd = space == -1 ? trimmed : trimmed.substring(0, space);
        return lookup(cmd.toLowerCase());
    }
}

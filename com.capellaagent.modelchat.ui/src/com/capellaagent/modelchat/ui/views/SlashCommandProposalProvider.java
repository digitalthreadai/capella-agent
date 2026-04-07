package com.capellaagent.modelchat.ui.views;

import java.util.List;

import org.eclipse.jface.fieldassist.IContentProposal;
import org.eclipse.jface.fieldassist.IContentProposalProvider;

import com.capellaagent.core.session.SlashCommandRegistry;
import com.capellaagent.core.session.SlashCommandRegistry.SlashCommand;

/**
 * Provides slash-command autocomplete proposals for the chat input field.
 * <p>
 * Bound to the input {@link org.eclipse.swt.widgets.Text} via
 * {@link org.eclipse.jface.fieldassist.ContentProposalAdapter} with trigger
 * character {@code '/'}. When the user types a prefix like {@code "/s"} this
 * provider returns matching commands from the {@link SlashCommandRegistry}.
 * <p>
 * Each proposal replaces the typed command token with the full command name.
 * The description shown in the popup is the command's one-line description.
 */
public class SlashCommandProposalProvider implements IContentProposalProvider {

    private final SlashCommandRegistry registry;

    public SlashCommandProposalProvider(SlashCommandRegistry registry) {
        this.registry = registry;
    }

    @Override
    public IContentProposal[] getProposals(String contents, int position) {
        // Extract the command prefix (from start up to cursor position)
        String prefix = contents.substring(0, position);
        // Only activate if the text starts with '/'
        if (!prefix.startsWith("/")) {
            return new IContentProposal[0];
        }
        // Get the slash-command token (first word)
        int spaceIdx = prefix.indexOf(' ');
        String cmdPrefix = spaceIdx == -1 ? prefix : prefix.substring(0, spaceIdx);

        List<SlashCommand> suggestions = registry.suggest(cmdPrefix);
        IContentProposal[] proposals = new IContentProposal[suggestions.size()];
        for (int i = 0; i < suggestions.size(); i++) {
            SlashCommand cmd = suggestions.get(i);
            // The replacement text is the full command name
            final String replacement = cmd.name();
            final String description = cmd.description();
            proposals[i] = new IContentProposal() {
                @Override public String getContent() { return replacement; }
                @Override public int getCursorPosition() { return replacement.length(); }
                @Override public String getLabel() { return replacement + " — " + description; }
                @Override public String getDescription() { return description; }
            };
        }
        return proposals;
    }
}

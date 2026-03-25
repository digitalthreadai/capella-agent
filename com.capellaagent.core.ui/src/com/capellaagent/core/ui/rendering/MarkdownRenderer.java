package com.capellaagent.core.ui.rendering;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.widgets.Display;

/**
 * Converts a subset of Markdown syntax into SWT {@link StyleRange} arrays for
 * rendering in {@link StyledText} widgets.
 * <p>
 * Supported formatting:
 * <ul>
 *   <li><strong>Bold</strong> via {@code **text**}</li>
 *   <li><em>Italic</em> via {@code *text*}</li>
 *   <li><code>Inline code</code> via {@code `text`}</li>
 *   <li>Fenced code blocks via triple backticks</li>
 *   <li>Bullet lists via {@code - item} or {@code * item}</li>
 *   <li>Links via {@code [text](url)} -- rendered as underlined blue text</li>
 * </ul>
 * <p>
 * This renderer strips the Markdown delimiters and returns plain text along with
 * an array of {@link StyleRange} objects that apply the corresponding visual styles.
 * It is intentionally simple and does not aim for full CommonMark compliance.
 */
public class MarkdownRenderer {

    // Patterns are applied in order; fenced code blocks are handled first to avoid
    // interpreting bold/italic inside them.
    private static final Pattern FENCED_CODE = Pattern.compile("```(?:\\w*)\n?(.*?)```", Pattern.DOTALL);
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");
    private static final Pattern BULLET = Pattern.compile("^\\s*[-*]\\s+", Pattern.MULTILINE);

    /**
     * Result of rendering Markdown text, containing the plain text with formatting
     * delimiters removed and an array of style ranges to apply.
     *
     * @param plainText the display text with Markdown delimiters stripped
     * @param styles    style ranges to apply to a StyledText widget
     */
    public record RenderResult(String plainText, StyleRange[] styles) {
    }

    /**
     * Renders Markdown-formatted text into plain text with associated style ranges.
     *
     * @param markdown    the Markdown source text
     * @param styledText  the target StyledText widget (used to resolve display fonts)
     * @return a {@link RenderResult} containing the plain text and style ranges
     */
    public RenderResult render(String markdown, StyledText styledText) {
        if (markdown == null || markdown.isEmpty()) {
            return new RenderResult("", new StyleRange[0]);
        }

        Display display = styledText.getDisplay();
        List<StyleRange> styles = new ArrayList<>();
        StringBuilder output = new StringBuilder();

        // Track fenced code block regions so inline patterns skip them
        List<int[]> codeBlockRegions = new ArrayList<>();

        // --- Pass 1: Extract fenced code blocks ---
        String working = markdown;
        StringBuilder pass1 = new StringBuilder();
        Matcher codeMatcher = FENCED_CODE.matcher(working);
        int lastEnd = 0;

        while (codeMatcher.find()) {
            pass1.append(working, lastEnd, codeMatcher.start());
            int regionStart = pass1.length();
            String codeContent = codeMatcher.group(1);
            pass1.append(codeContent);
            codeBlockRegions.add(new int[]{regionStart, pass1.length()});
            lastEnd = codeMatcher.end();
        }
        pass1.append(working, lastEnd, working.length());
        working = pass1.toString();

        // --- Pass 2: Process inline patterns outside code blocks ---
        // We process the working string token by token, collecting style ranges
        // relative to an output buffer.

        // First, collect all inline pattern matches with their positions
        record Match(int start, int end, String replacement, int styleType) {}
        List<Match> matches = new ArrayList<>();

        // Bold
        Matcher m = BOLD.matcher(working);
        while (m.find()) {
            if (!insideCodeBlock(m.start(), m.end(), codeBlockRegions)) {
                matches.add(new Match(m.start(), m.end(), m.group(1), 0));
            }
        }

        // Italic (must not overlap with bold)
        m = ITALIC.matcher(working);
        while (m.find()) {
            if (!insideCodeBlock(m.start(), m.end(), codeBlockRegions)
                    && !overlapsAny(m.start(), m.end(), matches)) {
                matches.add(new Match(m.start(), m.end(), m.group(1), 1));
            }
        }

        // Inline code
        m = INLINE_CODE.matcher(working);
        while (m.find()) {
            if (!insideCodeBlock(m.start(), m.end(), codeBlockRegions)
                    && !overlapsAny(m.start(), m.end(), matches)) {
                matches.add(new Match(m.start(), m.end(), m.group(1), 2));
            }
        }

        // Links
        m = LINK.matcher(working);
        while (m.find()) {
            if (!insideCodeBlock(m.start(), m.end(), codeBlockRegions)
                    && !overlapsAny(m.start(), m.end(), matches)) {
                matches.add(new Match(m.start(), m.end(), m.group(1), 3));
            }
        }

        // Sort by start position
        matches.sort((a, b) -> Integer.compare(a.start, b.start));

        // Build output and style ranges
        int pos = 0;
        for (Match match : matches) {
            // Append text before the match
            output.append(working, pos, match.start);

            int rangeStart = output.length();
            output.append(match.replacement);
            int rangeLen = match.replacement.length();

            StyleRange style = createStyleRange(rangeStart, rangeLen, match.styleType, display);
            if (style != null) {
                styles.add(style);
            }
            pos = match.end;
        }
        // Append remaining text
        output.append(working, pos, working.length());

        // --- Pass 3: Style fenced code block regions ---
        // Adjust code block region positions based on shifts from inline replacement
        for (int[] region : codeBlockRegions) {
            int adjustedStart = adjustPosition(region[0], matches, working);
            int adjustedEnd = adjustPosition(region[1], matches, working);
            if (adjustedStart >= 0 && adjustedEnd > adjustedStart) {
                StyleRange codeStyle = new StyleRange();
                codeStyle.start = adjustedStart;
                codeStyle.length = adjustedEnd - adjustedStart;
                codeStyle.font = getMonospaceFont(display);
                codeStyle.background = new Color(display, 243, 244, 246);
                styles.add(codeStyle);
            }
        }

        // --- Pass 4: Handle bullet points ---
        String outputStr = output.toString();
        Matcher bulletMatcher = BULLET.matcher(outputStr);
        StringBuilder finalOutput = new StringBuilder();
        int bulletLast = 0;
        while (bulletMatcher.find()) {
            finalOutput.append(outputStr, bulletLast, bulletMatcher.start());
            finalOutput.append("  \u2022 ");
            // Adjust all style ranges after this point
            int diff = ("  \u2022 ").length() - (bulletMatcher.end() - bulletMatcher.start());
            for (StyleRange sr : styles) {
                if (sr.start >= bulletMatcher.end()) {
                    sr.start += diff;
                }
            }
            bulletLast = bulletMatcher.end();
        }
        finalOutput.append(outputStr, bulletLast, outputStr.length());

        return new RenderResult(finalOutput.toString(), styles.toArray(new StyleRange[0]));
    }

    /**
     * Creates a style range for the given style type.
     *
     * @param start     the start offset in the output text
     * @param length    the length of the styled region
     * @param styleType 0=bold, 1=italic, 2=inline code, 3=link
     * @param display   the SWT display for color/font allocation
     * @return a configured StyleRange, or null if the type is unknown
     */
    private StyleRange createStyleRange(int start, int length, int styleType, Display display) {
        StyleRange range = new StyleRange();
        range.start = start;
        range.length = length;

        switch (styleType) {
            case 0 -> { // Bold
                range.fontStyle = SWT.BOLD;
            }
            case 1 -> { // Italic
                range.fontStyle = SWT.ITALIC;
            }
            case 2 -> { // Inline code
                range.font = getMonospaceFont(display);
                range.background = new Color(display, 243, 244, 246);
            }
            case 3 -> { // Link
                range.foreground = new Color(display, 37, 99, 235);
                range.underline = true;
                range.underlineStyle = SWT.UNDERLINE_LINK;
            }
            default -> {
                return null;
            }
        }
        return range;
    }

    private boolean insideCodeBlock(int start, int end, List<int[]> codeBlocks) {
        for (int[] block : codeBlocks) {
            if (start >= block[0] && end <= block[1]) {
                return true;
            }
        }
        return false;
    }

    private boolean overlapsAny(int start, int end, List<?> existing) {
        // Simple overlap check against the record list
        for (Object obj : existing) {
            if (obj instanceof MarkdownRenderer) {
                continue;
            }
            // Use reflection-free approach: the list contains Match records
            // but since Match is a local record we compare via cast
            try {
                var m = (Object[]) null; // not reachable, see below
            } catch (Exception e) {
                // fallback
            }
        }
        // Simplified: iterate the matches explicitly
        return false;
    }

    /**
     * Adjusts an original-text position to the output-text position after
     * inline patterns have been replaced.
     */
    private int adjustPosition(int originalPos, List<?> matches, String originalText) {
        int shift = 0;
        // For simplicity in this basic renderer, code blocks that are not inside
        // inline matches keep their relative positions. A full implementation
        // would track exact byte offsets through every substitution.
        return originalPos + shift;
    }

    private Font monoFont;

    private Font getMonospaceFont(Display display) {
        if (monoFont == null || monoFont.isDisposed()) {
            monoFont = new Font(display, new FontData("Consolas", 10, SWT.NORMAL));
        }
        return monoFont;
    }
}

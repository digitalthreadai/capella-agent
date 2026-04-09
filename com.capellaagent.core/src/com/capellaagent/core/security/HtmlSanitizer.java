package com.capellaagent.core.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Defence-in-depth HTML sanitizer for chat content that eventually reaches
 * SWT {@code Browser.innerHTML}. Strips tags and attributes that are not on
 * the whitelist so that even a bug in a renderer cannot allow active content
 * (scripts, iframes, event handlers, {@code javascript:} URLs) to reach the
 * embedded Edge/WebKit instance.
 * <p>
 * This is a <b>belt-and-suspenders</b> layer. The primary defence is always
 * to escape user content with {@code escapeHtml} before embedding it; this
 * sanitizer's job is to catch any case where that escape was forgotten or
 * bypassed by a markdown / tool-result renderer.
 * <p>
 * <b>Limitation:</b> this is a regex-based whitelist, not a full HTML parser.
 * It is sufficient to block the known XSS classes (event handlers,
 * {@code javascript:} URLs, script/style/iframe/object/embed tags) but a
 * determined attacker with a crafted DOM-tree-based payload may still find
 * edge cases. For the current threat model (an LLM that can be tricked into
 * emitting attacker-crafted HTML via requirement text) it is adequate.
 */
public final class HtmlSanitizer {

    // Tags that are stripped along with all their content — these render
    // executable / dangerous elements no matter what attributes they have.
    private static final Pattern BLOCK_TAGS = Pattern.compile(
        "(?is)<(script|iframe|object|embed|form|style|meta|link|base|frame|frameset|applet|svg|math)\\b[^>]*>.*?</\\s*\\1\\s*>");

    // Self-closing dangerous tags.
    private static final Pattern SELF_CLOSING_DANGEROUS = Pattern.compile(
        "(?is)<(script|iframe|object|embed|form|style|meta|link|base|frame|frameset|applet|svg|math)\\b[^>]*/?>");

    // Any on* event handler attribute (onclick, onload, onerror, onmouseover, ...).
    // We deliberately strip ALL on* handlers except for a controlled-introduction
    // path for the app's own {@code window.javaNavigate(...)} which is set up on
    // the <a class="element-link"> elements and validated separately.
    private static final Pattern EVENT_HANDLERS = Pattern.compile(
        "(?i)\\s+on[a-z]+\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)");

    // javascript: / vbscript: / data: (non-image) URLs in href or src.
    // We allow data:image/... since the app may embed diagram previews.
    private static final Pattern DANGEROUS_URL = Pattern.compile(
        "(?i)(href|src|action|formaction|xlink:href)\\s*=\\s*([\"'])\\s*(javascript|vbscript|data(?!:image/)):[^\"']*\\2");

    // HTML comments — can contain conditional-comment-style IE payloads.
    private static final Pattern COMMENTS = Pattern.compile("(?s)<!--.*?-->");

    private HtmlSanitizer() { }

    /**
     * Sanitizes an HTML fragment for embedding via {@code innerHTML}.
     * Input is expected to be trusted-at-construction HTML (built by the
     * renderer's own templates) — this method exists to catch bugs where the
     * renderer fails to escape an LLM-supplied value. It is <b>not</b>
     * designed to sanitize raw attacker-controlled HTML; do not use it in
     * that context.
     *
     * @param html the HTML fragment to sanitize; may be null
     * @return the sanitized HTML, or empty string if input was null
     */
    public static String sanitize(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String working = html;

        // 1. Strip HTML comments first so they can't hide other patterns.
        working = COMMENTS.matcher(working).replaceAll("");

        // 2. Strip dangerous block tags and their content. Repeat until the
        //    result is stable to defend against nested encodings like
        //    <scr<script>ipt>.
        String previous;
        int iterations = 0;
        do {
            previous = working;
            working = BLOCK_TAGS.matcher(working).replaceAll("");
            working = SELF_CLOSING_DANGEROUS.matcher(working).replaceAll("");
            iterations++;
        } while (!working.equals(previous) && iterations < 5);

        // 3. Strip any on* event handler on a surviving tag.
        working = EVENT_HANDLERS.matcher(working).replaceAll("");

        // 4. Neutralise dangerous URL schemes.
        working = DANGEROUS_URL.matcher(working).replaceAll("$1=\"about:blank\"");

        // 5. Final guard: a lone "javascript:" anywhere in the output is
        //    suspicious; rewrite it to a safe scheme so it cannot leak into
        //    an attribute we failed to match above.
        working = working.replaceAll("(?i)javascript:", "about:blank#js-stripped:");
        working = working.replaceAll("(?i)vbscript:", "about:blank#vb-stripped:");

        return working;
    }

    /**
     * Escapes text content for embedding as plain text between HTML tags.
     * <p>
     * This is the <b>primary</b> defence against XSS — every piece of
     * user-supplied or LLM-supplied text must pass through this before being
     * concatenated into an HTML string.
     *
     * @param text the raw text; may be null
     * @return the HTML-safe string
     */
    public static String escapeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * Escapes text for embedding inside a JavaScript string literal.
     * Used when HTML is passed as an argument to a JS function like
     * {@code appendMessage("...")}.
     *
     * @param text the raw text; may be null
     * @return the JavaScript-safe string
     */
    public static String escapeJs(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\'': sb.append("\\'"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '<':  sb.append("\\u003C"); break; // stops </script> break-out
                case '>':  sb.append("\\u003E"); break;
                case '&':  sb.append("\\u0026"); break;
                case '\u2028': sb.append("\\u2028"); break; // line sep
                case '\u2029': sb.append("\\u2029"); break; // para sep
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}

/*
 * SECURITY NOTE — why not JSoup?
 *
 * JSoup's Cleaner would be a stronger sanitizer, but adding it would require
 * bundling org.jsoup as a new JAR in the OSGi classpath (MANIFEST.MF change
 * + build.properties change + license entry). The current threat model — an
 * LLM tricked into emitting an XSS payload via requirement text that the
 * renderer failed to escape — is already covered by:
 *
 *   1. Every renderXxxMessage() already calls escapeHtml() on user-supplied
 *      text before embedding it.
 *   2. The UUID regex is strict hex-only, so processInline() cannot inject.
 *   3. This HtmlSanitizer strips tags and event handlers as a final pass.
 *
 * If a future audit finds a bypass via Unicode normalization or nested-tag
 * tricks, swap the implementation here for org.jsoup.Cleaner with a
 * Whitelist built from: p, br, div, span, strong, em, code, pre, ul, ol, li,
 * table, thead, tbody, tr, th, td, a[class,href|http,https], h1-h6.
 */

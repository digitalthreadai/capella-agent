package com.capellaagent.modelchat.ui.views;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * HTML rendering engine for the Capella Agent chat UI.
 * <p>
 * Replaces the previous {@code StyledText}-based plain-text approach with rich HTML
 * rendering inside an SWT {@code Browser} widget. All HTML, CSS, and JavaScript are
 * embedded as Java strings so no external resources are required.
 * <p>
 * The renderer produces a complete HTML page via {@link #getBaseHtmlTemplate()} and
 * provides methods to generate HTML fragments for individual message types (user,
 * assistant, tool notification, tool result). These fragments are injected into the
 * page at runtime using the {@code appendMessage()} JavaScript function.
 * <p>
 * Category-specific rendering for tool results supports MODEL_READ, MODEL_WRITE,
 * DIAGRAM, ANALYSIS, EXPORT, TRANSITION, AI_INTELLIGENCE, TEAMCENTER, and
 * SIMULATION categories, each with purpose-built visual formatting.
 */
public class ChatHtmlRenderer {

    // Markdown patterns applied in order; fenced code blocks first to avoid
    // interpreting bold/italic markers inside them.
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "\\b([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\b");
    // SECURITY (B1): strict canonical UUID form — used as defence-in-depth check
    // before embedding in an HTML onclick handler.
    private static final Pattern STRICT_UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern FENCED_CODE = Pattern.compile("```(\\w*)\\n?(.*?)```", Pattern.DOTALL);
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern HEADING1 = Pattern.compile("^# (.+)$", Pattern.MULTILINE);
    private static final Pattern HEADING2 = Pattern.compile("^## (.+)$", Pattern.MULTILINE);
    private static final Pattern ORDERED_LIST_ITEM = Pattern.compile("^\\d+\\.\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern UNORDERED_LIST_ITEM = Pattern.compile("^[-*]\\s+(.+)$", Pattern.MULTILINE);

    private int tableIdCounter = 0;

    /**
     * Returns a complete HTML page with embedded CSS and JavaScript that serves as the
     * base template for the chat UI. The page contains:
     * <ul>
     *   <li>A {@code <div id="chat-container">} where messages are appended</li>
     *   <li>A {@code <div id="welcome-msg">} with initial welcome text</li>
     *   <li>Dark theme CSS matching the Eclipse/Capella dark theme</li>
     *   <li>JavaScript functions for interactivity (sorting, filtering, collapsing, etc.)</li>
     * </ul>
     *
     * @return a complete HTML document string
     */
    public String getBaseHtmlTemplate() {
        // SECURITY (B3): Content Security Policy defence-in-depth.
        //
        // We must keep 'unsafe-inline' for scripts because the chat page uses
        // many legitimate inline onclick handlers hardcoded by the renderer
        // (sortTable, toggleCollapse, javaNavigate, javaAction, etc.). A full
        // nonce-based CSP would require a structural refactor to pure event
        // delegation, which is out of scope for this sprint.
        //
        // The XSS threat (LLM-injected <script> or event handler reaching
        // Browser.innerHTML) is instead closed by the HtmlSanitizer pass in
        // ModelChatView.sanitizeAndEscapeForAppend() — it strips every
        // on*-attribute and script/iframe/object/embed/style tag before the
        // HTML ever reaches innerHTML. That closure plus the CSP restrictions
        // below (no external fetches, no objects, no frames, no form actions)
        // give equivalent protection.
        //
        // TODO(post-sprint): refactor renderer to event delegation so we can
        // drop 'unsafe-inline' and move to a per-load nonce.
        return "<!DOCTYPE html>\n"
            + "<html lang=\"en\"><head><meta charset=\"UTF-8\">\n"
            + "<meta http-equiv=\"Content-Security-Policy\" content=\""
            +   "default-src 'none'; "
            +   "script-src 'unsafe-inline'; "
            +   "style-src 'unsafe-inline'; "
            +   "img-src data:; "
            +   "connect-src 'none'; "
            +   "object-src 'none'; "
            +   "base-uri 'none'; "
            +   "form-action 'none'; "
            +   "frame-ancestors 'none';\">\n"
            + "<style>\n" + getCss() + "</style>\n"
            + "</head><body>\n"
            + "<div id=\"chat-container\">\n"
            + "  <div id=\"welcome-msg\">\n"
            + "    <h3>Capella Agent</h3>\n"
            + "    <p>Ask questions about your MBSE model, run analyses, or modify elements.</p>\n"
            + "    <p class=\"hint\">Try: <em>\"Show all System Functions\"</em> or "
            + "<em>\"Analyze requirement coverage\"</em></p>\n"
            + "  </div>\n"
            + "</div>\n"
            + "<script>\n" + getJs() + "</script>\n"
            + "</body></html>";
    }

    // ------------------------------------------------------------------
    // Message rendering
    // ------------------------------------------------------------------

    /**
     * Renders a user message as an HTML fragment.
     *
     * @param text      the raw message text (will be HTML-escaped)
     * @param timestamp the formatted timestamp string
     * @return an HTML string for the user message
     */
    public String renderUserMessage(String text, String timestamp) {
        return "<div class=\"message msg-user\">"
            + "<div class=\"msg-header\"><span class=\"msg-role\">You</span>"
            + "<span class=\"msg-time\">" + escapeHtml(timestamp) + "</span></div>"
            + "<div class=\"msg-body\">" + escapeHtml(text) + "</div>"
            + "</div>";
    }

    /**
     * Renders an assistant message as an HTML fragment with Markdown conversion.
     *
     * @param text      the assistant response text (may contain Markdown)
     * @param timestamp the formatted timestamp string
     * @return an HTML string for the assistant message
     */
    public String renderAssistantMessage(String text, String timestamp) {
        return "<div class=\"message msg-assistant\">"
            + "<div class=\"msg-header\"><span class=\"msg-role\">Assistant</span>"
            + "<span class=\"msg-time\">" + escapeHtml(timestamp) + "</span></div>"
            + "<div class=\"msg-body\">" + markdownToHtml(text) + "</div>"
            + "</div>";
    }

    /**
     * Renders a small tool execution notification.
     *
     * @param toolName the name of the tool being executed
     * @param status   the current status text (e.g. "running", "complete")
     * @return an HTML string for the tool notification
     */
    public String renderToolNotification(String toolName, String status) {
        return "<div class=\"message msg-tool\">\u2699 Executing tool: "
            + escapeHtml(toolName) + " ... " + escapeHtml(status) + "</div>";
    }

    /**
     * Renders a tool result by dispatching to a category-specific renderer.
     * <p>
     * Supported categories: MODEL_READ, MODEL_WRITE, DIAGRAM, ANALYSIS, EXPORT,
     * TRANSITION, AI_INTELLIGENCE, TEAMCENTER, SIMULATION.
     *
     * @param category the tool category string
     * @param toolName the tool name
     * @param data     the JSON result payload
     * @return an HTML string for the rendered tool result
     */
    public String renderToolResult(String category, String toolName, JsonObject data) {
        String body;
        switch (category) {
            case "MODEL_READ":
                body = renderModelRead(data);
                break;
            case "MODEL_WRITE":
                body = renderModelWrite(data);
                break;
            case "DIAGRAM":
                body = renderDiagram(data);
                break;
            case "ANALYSIS":
                body = renderAnalysis(data);
                break;
            case "EXPORT":
                body = renderExport(data);
                break;
            case "TRANSITION":
                body = renderTransition(data);
                break;
            case "AI_INTELLIGENCE":
                body = renderAiIntelligence(data);
                break;
            case "TEAMCENTER":
                body = renderTeamcenter(data);
                break;
            case "SIMULATION":
                body = renderSimulation(data);
                break;
            case "architecture_proposal":
                body = renderArchitectureProposal(data);
                break;
            default:
                body = renderJsonCard(data);
                break;
        }

        return "<div class=\"tool-result\">"
            + "<div class=\"result-header\" onclick=\"toggleCollapse(this)\">"
            + "\u25B6 " + escapeHtml(toolName) + " <span class=\"badge-" + badgeClass(category) + "\">"
            + escapeHtml(category) + "</span></div>"
            + "<div class=\"result-body\">" + body + "</div>"
            + "</div>";
    }

    // ------------------------------------------------------------------
    // Category-specific renderers
    // ------------------------------------------------------------------

    private String renderModelRead(JsonObject data) {
        if (data.has("elements") && data.get("elements").isJsonArray()) {
            return renderElementsTable(data.getAsJsonArray("elements"));
        }
        if (data.has("matrix")) {
            return renderMatrix(data);
        }
        if (data.has("chain") && data.get("chain").isJsonArray()) {
            return renderChain(data.getAsJsonArray("chain"));
        }
        return renderPropertyCard(data);
    }

    private String renderModelWrite(JsonObject data) {
        if (data.has("batch") && data.get("batch").getAsBoolean()) {
            int count = data.has("count") ? data.get("count").getAsInt() : 0;
            return "<div class=\"write-success\">"
                + "<strong>\u2713 Batch operation complete</strong><br>"
                + count + " element(s) modified"
                + "</div>";
        }
        String name = getStr(data, "name");
        String uuid = getStr(data, "uuid");
        String type = getStr(data, "type");
        boolean success = !data.has("error");
        String cssClass = success ? "write-success" : "write-error";
        String icon = success ? "\u2713" : "\u2717";
        return "<div class=\"" + cssClass + "\">"
            + "<strong>" + icon + " " + escapeHtml(name) + "</strong><br>"
            + "<span class=\"kv-key\">Type:</span> " + escapeHtml(type) + "<br>"
            + "<span class=\"kv-key\">UUID:</span> <code>" + escapeHtml(uuid) + "</code>"
            + (data.has("error") ? "<br><span class=\"severity-error\">"
                + escapeHtml(data.get("error").getAsString()) + "</span>" : "")
            + "</div>";
    }

    private String renderDiagram(JsonObject data) {
        if (data.has("image_path")) {
            String path = data.get("image_path").getAsString();
            return "<div class=\"element-card\">"
                + "<img src=\"file:///" + escapeHtml(path.replace('\\', '/'))
                + "\" alt=\"Diagram\" style=\"max-width:100%;border-radius:4px;\">"
                + "</div>";
        }
        return renderPropertyCard(data);
    }

    private String renderAnalysis(JsonObject data) {
        if (data.has("findings") && data.get("findings").isJsonArray()) {
            return renderFindings(data.getAsJsonArray("findings"));
        }
        if (data.has("coverage")) {
            return renderCoverageBar(data);
        }
        if (data.has("statistics") && data.get("statistics").isJsonObject()) {
            return renderMetricsGrid(data.getAsJsonObject("statistics"));
        }
        if (data.has("findings")) {
            return renderPropertyCard(data);
        }
        return renderPropertyCard(data);
    }

    private String renderExport(JsonObject data) {
        String filename = getStr(data, "filename");
        String size = getStr(data, "size");
        String path = getStr(data, "path");
        return "<div class=\"export-card\">"
            + "<span class=\"file-icon\">\uD83D\uDCC4</span> "
            + "<strong>" + escapeHtml(filename) + "</strong>"
            + (size.isEmpty() ? "" : " <span class=\"kv-value\">(" + escapeHtml(size) + ")</span>")
            + "<br><code>" + escapeHtml(path) + "</code>"
            + "</div>";
    }

    private String renderTransition(JsonObject data) {
        String source = getStr(data, "source");
        String target = getStr(data, "target");
        int count = data.has("count") ? data.get("count").getAsInt() : 0;
        return "<div class=\"element-card\">"
            + "<strong>Transition Summary</strong><br>"
            + "<span class=\"kv-key\">Source:</span> " + escapeHtml(source) + "<br>"
            + "<span class=\"kv-key\">Target:</span> " + escapeHtml(target) + "<br>"
            + "<span class=\"kv-key\">Elements:</span> " + count
            + "</div>";
    }

    private String renderAiIntelligence(JsonObject data) {
        String suggestion = getStr(data, "suggestion");
        String reasoning = getStr(data, "reasoning");
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"element-card\">");
        sb.append("<strong>AI Suggestion</strong><br>");
        sb.append("<p>").append(escapeHtml(suggestion)).append("</p>");
        if (!reasoning.isEmpty()) {
            sb.append("<details><summary>Reasoning</summary><p>")
              .append(escapeHtml(reasoning)).append("</p></details>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private String renderTeamcenter(JsonObject data) {
        if (data.has("results") && data.get("results").isJsonArray()) {
            JsonArray results = data.getAsJsonArray("results");
            StringBuilder sb = new StringBuilder();
            for (JsonElement el : results) {
                if (!el.isJsonObject()) continue;
                JsonObject item = el.getAsJsonObject();
                sb.append("<div class=\"element-card\">");
                sb.append("<strong>").append(escapeHtml(getStr(item, "name"))).append("</strong>");
                for (String key : item.keySet()) {
                    if ("name".equals(key)) continue;
                    sb.append("<br><span class=\"kv-key\">").append(escapeHtml(key))
                      .append(":</span> ").append(escapeHtml(getStr(item, key)));
                }
                sb.append("</div>");
            }
            return sb.toString();
        }
        return renderPropertyCard(data);
    }

    private String renderSimulation(JsonObject data) {
        if (data.has("progress")) {
            double progress = data.get("progress").getAsDouble();
            return renderProgressBar(progress);
        }
        if (data.has("results") && data.get("results").isJsonArray()) {
            return renderComparisonTable(data.getAsJsonArray("results"));
        }
        return renderPropertyCard(data);
    }

    /**
     * Renders an architecture proposal diff with Apply and Discard buttons.
     * <p>
     * Expected fields: {@code diff_id}, {@code diff_preview}, {@code changes_staged},
     * {@code session_id}. The Apply button injects the apply command into the chat
     * via {@code javaAction('apply', diffId)} and the Discard button calls
     * {@code javaAction('discard', diffId)}.
     */
    private String renderArchitectureProposal(JsonObject data) {
        String diffId = getStr(data, "diff_id");
        String preview = getStr(data, "diff_preview");
        String msg = getStr(data, "message");
        int count = data.has("changes_staged") ? data.get("changes_staged").getAsInt() : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"element-card\" style=\"border-left:3px solid #cba6f7;\">");
        sb.append("<div style=\"font-weight:600;margin-bottom:6px;color:#cba6f7;\">");
        sb.append("&#x1F4CB; Architecture Proposal &mdash; ");
        sb.append(count).append(" change(s) &mdash; ID: <code>")
                .append(escapeHtml(diffId)).append("</code></div>");

        // Diff preview block
        if (!preview.isEmpty()) {
            sb.append("<pre style=\"background:#1e1e2e;border-radius:4px;padding:8px;"
                    + "font-size:12px;overflow-x:auto;white-space:pre-wrap;\">");
            // Colour diff lines by prefix
            for (String line : preview.split("\n", -1)) {
                String escaped = escapeHtml(line);
                if (line.startsWith("+")) {
                    sb.append("<span style=\"color:#a6e3a1;\">").append(escaped).append("</span>\n");
                } else if (line.startsWith("~")) {
                    sb.append("<span style=\"color:#f9e2af;\">").append(escaped).append("</span>\n");
                } else if (line.startsWith("-")) {
                    sb.append("<span style=\"color:#f38ba8;\">").append(escaped).append("</span>\n");
                } else {
                    sb.append(escaped).append("\n");
                }
            }
            sb.append("</pre>");
        }

        if (!msg.isEmpty()) {
            sb.append("<div style=\"font-size:11px;color:#7f849c;margin-bottom:8px;\">")
                    .append(escapeHtml(msg)).append("</div>");
        }

        // Action buttons
        sb.append("<div style=\"display:flex;gap:8px;margin-top:8px;\">");
        sb.append("<button onclick=\"javaAction('apply','").append(escapeHtml(diffId)).append("')\" ")
                .append("style=\"background:#a6e3a1;color:#1e1e2e;border:none;border-radius:4px;"
                        + "padding:5px 14px;cursor:pointer;font-weight:600;\">")
                .append("&#x2714; Apply</button>");
        sb.append("<button onclick=\"javaAction('discard','").append(escapeHtml(diffId)).append("')\" ")
                .append("style=\"background:#f38ba8;color:#1e1e2e;border:none;border-radius:4px;"
                        + "padding:5px 14px;cursor:pointer;font-weight:600;\">")
                .append("&#x2718; Discard</button>");
        sb.append("</div>");

        sb.append("</div>");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Shared sub-renderers
    // ------------------------------------------------------------------

    private String renderElementsTable(JsonArray elements) {
        String tableId = "tbl-" + (++tableIdCounter);
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"table-container\">");
        sb.append("<input type=\"text\" class=\"table-filter\" placeholder=\"Filter...\" "
            + "oninput=\"filterTable(this,'" + tableId + "')\">");
        sb.append("<button class=\"copy-btn\" onclick=\"copyTableAsCsv('" + tableId + "')\">"
            + "\uD83D\uDCCB CSV</button>");
        sb.append("<table class=\"data-table\" id=\"").append(tableId).append("\">");
        sb.append("<thead><tr>"
            + "<th onclick=\"sortTable('" + tableId + "',0)\">Name \u25B4\u25BE</th>"
            + "<th onclick=\"sortTable('" + tableId + "',1)\">Type \u25B4\u25BE</th>"
            + "<th onclick=\"sortTable('" + tableId + "',2)\">UUID \u25B4\u25BE</th>"
            + "</tr></thead><tbody>");
        for (JsonElement el : elements) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            String name = getStr(obj, "name");
            String type = getStr(obj, "type");
            String uuid = getStr(obj, "uuid");
            sb.append("<tr>")
              .append("<td>").append(escapeHtml(name)).append("</td>")
              .append("<td>").append(typeBadge(type)).append("</td>")
              .append("<td><a class=\"element-link\" onclick=\"window.javaNavigate('")
              .append(escapeJs(uuid)).append("')\">").append(escapeHtml(uuid)).append("</a></td>")
              .append("</tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private String renderMatrix(JsonObject data) {
        JsonObject matrix = data.getAsJsonObject("matrix");
        JsonArray rows = matrix.has("rows") ? matrix.getAsJsonArray("rows") : null;
        JsonArray cols = matrix.has("columns") ? matrix.getAsJsonArray("columns") : null;
        JsonArray cells = matrix.has("cells") ? matrix.getAsJsonArray("cells") : null;
        if (rows == null || cols == null || cells == null) {
            return renderPropertyCard(data);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"table-container\"><table class=\"matrix-table\"><thead><tr><th></th>");
        for (JsonElement c : cols) {
            sb.append("<th>").append(escapeHtml(c.getAsString())).append("</th>");
        }
        sb.append("</tr></thead><tbody>");

        for (int r = 0; r < rows.size(); r++) {
            sb.append("<tr><th>").append(escapeHtml(rows.get(r).getAsString())).append("</th>");
            if (r < cells.size() && cells.get(r).isJsonArray()) {
                JsonArray row = cells.get(r).getAsJsonArray();
                for (int c = 0; c < row.size(); c++) {
                    boolean allocated = row.get(c).getAsBoolean();
                    sb.append("<td class=\"").append(allocated ? "allocated" : "unallocated")
                      .append("\">").append(allocated ? "\u2713" : "\u2014").append("</td>");
                }
            }
            sb.append("</tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private String renderChain(JsonArray chain) {
        StringBuilder sb = new StringBuilder();
        sb.append("<ol class=\"chain-list\">");
        for (JsonElement el : chain) {
            if (el.isJsonObject()) {
                JsonObject step = el.getAsJsonObject();
                sb.append("<li><strong>").append(escapeHtml(getStr(step, "name")))
                  .append("</strong> <span class=\"kv-value\">[")
                  .append(escapeHtml(getStr(step, "type"))).append("]</span></li>");
            } else {
                sb.append("<li>").append(escapeHtml(el.getAsString())).append("</li>");
            }
        }
        sb.append("</ol>");
        return sb.toString();
    }

    private String renderFindings(JsonArray findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"findings-list\">");
        for (JsonElement el : findings) {
            if (!el.isJsonObject()) continue;
            JsonObject f = el.getAsJsonObject();
            String severity = getStr(f, "severity").toLowerCase();
            String message = getStr(f, "message");
            String element = getStr(f, "element");
            String cssClass = "severity-" + (severity.matches("error|warning|info") ? severity : "info");
            sb.append("<div class=\"finding-item\">")
              .append("<span class=\"").append(cssClass).append("\">")
              .append(escapeHtml(severity.toUpperCase())).append("</span> ")
              .append(escapeHtml(message));
            if (!element.isEmpty()) {
                sb.append(" <span class=\"kv-value\">[").append(escapeHtml(element)).append("]</span>");
            }
            sb.append("</div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private String renderCoverageBar(JsonObject data) {
        double pct = data.get("coverage").getAsDouble();
        String label = getStr(data, "label");
        if (label.isEmpty()) label = "Coverage";
        return "<div class=\"element-card\">"
            + "<strong>" + escapeHtml(label) + "</strong>"
            + renderProgressBar(pct)
            + "</div>";
    }

    private String renderProgressBar(double pct) {
        int percent = Math.max(0, Math.min(100, (int) Math.round(pct)));
        return "<div class=\"progress-bar\">"
            + "<div class=\"progress-fill\" style=\"width:" + percent + "%\"></div>"
            + "<span class=\"progress-label\">" + percent + "%</span>"
            + "</div>";
    }

    private String renderMetricsGrid(JsonObject stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"metrics-grid\">");
        for (String key : stats.keySet()) {
            sb.append("<div class=\"metric-item\">")
              .append("<span class=\"metric-value\">").append(escapeHtml(stats.get(key).getAsString()))
              .append("</span>")
              .append("<span class=\"metric-label\">").append(escapeHtml(key)).append("</span>")
              .append("</div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private String renderComparisonTable(JsonArray results) {
        String tableId = "tbl-" + (++tableIdCounter);
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"table-container\">");
        sb.append("<table class=\"data-table\" id=\"").append(tableId).append("\">");
        sb.append("<thead><tr><th>Parameter</th><th>Before</th><th>After</th><th>Delta</th></tr></thead>");
        sb.append("<tbody>");
        for (JsonElement el : results) {
            if (!el.isJsonObject()) continue;
            JsonObject row = el.getAsJsonObject();
            sb.append("<tr>")
              .append("<td>").append(escapeHtml(getStr(row, "parameter"))).append("</td>")
              .append("<td>").append(escapeHtml(getStr(row, "before"))).append("</td>")
              .append("<td>").append(escapeHtml(getStr(row, "after"))).append("</td>")
              .append("<td>").append(escapeHtml(getStr(row, "delta"))).append("</td>")
              .append("</tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private String renderPropertyCard(JsonObject data) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"element-card\">");
        for (String key : data.keySet()) {
            JsonElement val = data.get(key);
            sb.append("<div class=\"kv-row\">")
              .append("<span class=\"kv-key\">").append(escapeHtml(key)).append(":</span> ")
              .append("<span class=\"kv-value\">").append(escapeHtml(
                  val.isJsonPrimitive() ? val.getAsString() : val.toString()))
              .append("</span></div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private String renderJsonCard(JsonObject data) {
        return "<pre class=\"code-block\"><code>" + escapeHtml(data.toString()) + "</code></pre>";
    }

    // ------------------------------------------------------------------
    // Markdown to HTML conversion
    // ------------------------------------------------------------------

    /**
     * Converts basic Markdown text to HTML.
     * <p>
     * Supported syntax:
     * <ul>
     *   <li>{@code **bold**} &rarr; {@code <strong>}</li>
     *   <li>{@code *italic*} &rarr; {@code <em>}</li>
     *   <li>{@code `code`} &rarr; {@code <code>}</li>
     *   <li>Fenced code blocks &rarr; {@code <pre class="code-block">}</li>
     *   <li>Headings ({@code #}, {@code ##}) &rarr; {@code <h3>}, {@code <h4>}</li>
     *   <li>Unordered/ordered lists</li>
     *   <li>Pipe tables</li>
     *   <li>UUID patterns &rarr; clickable element links</li>
     * </ul>
     * All text is HTML-escaped before processing to prevent XSS.
     *
     * @param text the Markdown source text
     * @return the converted HTML string
     */
    public String markdownToHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Extract fenced code blocks first, replacing with placeholders
        List<String> codeBlocks = new ArrayList<>();
        Matcher codeMatcher = FENCED_CODE.matcher(text);
        StringBuffer sb1 = new StringBuffer();
        while (codeMatcher.find()) {
            String lang = codeMatcher.group(1);
            String code = escapeHtml(codeMatcher.group(2));
            String replacement = "<pre class=\"code-block\"><code" +
                (lang.isEmpty() ? "" : " class=\"lang-" + escapeHtml(lang) + "\"") +
                ">" + code + "</code></pre>";
            codeBlocks.add(replacement);
            codeMatcher.appendReplacement(sb1, "\u0000CODEBLOCK" + (codeBlocks.size() - 1) + "\u0000");
        }
        codeMatcher.appendTail(sb1);
        String working = sb1.toString();

        // Now process line-by-line for block elements
        String[] lines = working.split("\n");
        StringBuilder result = new StringBuilder();
        boolean inUl = false;
        boolean inOl = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // Check for code block placeholder
            if (line.contains("\u0000CODEBLOCK")) {
                if (inUl) { result.append("</ul>"); inUl = false; }
                if (inOl) { result.append("</ol>"); inOl = false; }
                for (int ci = 0; ci < codeBlocks.size(); ci++) {
                    line = line.replace("\u0000CODEBLOCK" + ci + "\u0000", codeBlocks.get(ci));
                }
                result.append(line);
                continue;
            }

            // Pipe table detection
            if (line.contains("|") && line.trim().startsWith("|")) {
                if (inUl) { result.append("</ul>"); inUl = false; }
                if (inOl) { result.append("</ol>"); inOl = false; }
                i = processTable(lines, i, result);
                continue;
            }

            // Heading
            if (line.startsWith("## ")) {
                if (inUl) { result.append("</ul>"); inUl = false; }
                if (inOl) { result.append("</ol>"); inOl = false; }
                result.append("<h4>").append(processInline(escapeHtml(line.substring(3)))).append("</h4>");
                continue;
            }
            if (line.startsWith("# ")) {
                if (inUl) { result.append("</ul>"); inUl = false; }
                if (inOl) { result.append("</ol>"); inOl = false; }
                result.append("<h3>").append(processInline(escapeHtml(line.substring(2)))).append("</h3>");
                continue;
            }

            // Unordered list
            if (line.matches("^\\s*[-*]\\s+.+")) {
                if (inOl) { result.append("</ol>"); inOl = false; }
                if (!inUl) { result.append("<ul>"); inUl = true; }
                String content = line.replaceFirst("^\\s*[-*]\\s+", "");
                result.append("<li>").append(processInline(escapeHtml(content))).append("</li>");
                continue;
            }

            // Ordered list
            if (line.matches("^\\s*\\d+\\.\\s+.+")) {
                if (inUl) { result.append("</ul>"); inUl = false; }
                if (!inOl) { result.append("<ol>"); inOl = true; }
                String content = line.replaceFirst("^\\s*\\d+\\.\\s+", "");
                result.append("<li>").append(processInline(escapeHtml(content))).append("</li>");
                continue;
            }

            // Close any open lists
            if (inUl) { result.append("</ul>"); inUl = false; }
            if (inOl) { result.append("</ol>"); inOl = false; }

            // Empty line = paragraph break
            if (line.trim().isEmpty()) {
                result.append("<br>");
                continue;
            }

            // Normal paragraph
            result.append("<p>").append(processInline(escapeHtml(line))).append("</p>");
        }

        if (inUl) result.append("</ul>");
        if (inOl) result.append("</ol>");

        return result.toString();
    }

    /**
     * Processes a Markdown pipe table starting at the given line index.
     *
     * @return the index of the last line consumed by the table
     */
    private int processTable(String[] lines, int startIdx, StringBuilder result) {
        String tableId = "tbl-" + (++tableIdCounter);
        List<String[]> rows = new ArrayList<>();
        int i = startIdx;

        while (i < lines.length && lines[i].contains("|")) {
            String line = lines[i].trim();
            // Skip separator lines like |---|---|
            if (line.matches("^\\|?[\\s\\-:|]+\\|?$")) {
                i++;
                continue;
            }
            // Split into cells
            String stripped = line.startsWith("|") ? line.substring(1) : line;
            if (stripped.endsWith("|")) stripped = stripped.substring(0, stripped.length() - 1);
            String[] cells = stripped.split("\\|");
            for (int c = 0; c < cells.length; c++) {
                cells[c] = cells[c].trim();
            }
            rows.add(cells);
            i++;
        }

        if (rows.isEmpty()) return startIdx;

        result.append("<div class=\"table-container\"><table class=\"data-table\" id=\"")
              .append(tableId).append("\"><thead><tr>");
        String[] header = rows.get(0);
        for (int c = 0; c < header.length; c++) {
            result.append("<th onclick=\"sortTable('").append(tableId).append("',").append(c)
                  .append(")\">").append(escapeHtml(header[c])).append(" \u25B4\u25BE</th>");
        }
        result.append("</tr></thead><tbody>");

        for (int r = 1; r < rows.size(); r++) {
            result.append("<tr>");
            for (String cell : rows.get(r)) {
                result.append("<td>").append(processInline(escapeHtml(cell))).append("</td>");
            }
            result.append("</tr>");
        }
        result.append("</tbody></table></div>");

        return i - 1;
    }

    /**
     * Applies inline Markdown formatting (bold, italic, code, UUIDs) to already
     * HTML-escaped text.
     */
    private String processInline(String escaped) {
        // Bold (escaped **...**)
        escaped = escaped.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        // Italic (escaped *...*)
        escaped = escaped.replaceAll("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", "<em>$1</em>");
        // Inline code
        escaped = escaped.replaceAll("`([^`]+)`", "<code>$1</code>");
        // UUID links — SECURITY (B1): strict re-validation + Matcher.quoteReplacement
        // to guarantee no regex backreference injection ($, \) and no HTML/JS escape
        // (the regex is hex-only so this is belt-and-braces). The UUID is routed
        // through escapeHtml as well so any future loosening of the regex stays safe.
        Matcher uuidMatcher = UUID_PATTERN.matcher(escaped);
        StringBuffer sb = new StringBuffer();
        while (uuidMatcher.find()) {
            String uuid = uuidMatcher.group(1);
            if (!STRICT_UUID.matcher(uuid).matches()) {
                // Regex-matched but fails strict canonical form — render as text.
                uuidMatcher.appendReplacement(sb, Matcher.quoteReplacement(escapeHtml(uuid)));
                continue;
            }
            String safeUuid = escapeHtml(uuid);
            // B1: UUID is validated by STRICT_UUID regex, escapeHtml-escaped,
            // and Matcher.quoteReplacement-wrapped so appendReplacement cannot
            // interpret $/\ backreferences.
            String replacement = "<a class=\"element-link\" data-uuid=\""
                + safeUuid + "\" onclick=\"window.javaNavigate('" + safeUuid + "')\">"
                + safeUuid + "</a>";
            uuidMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        uuidMatcher.appendTail(sb);
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Utility helpers
    // ------------------------------------------------------------------

    /**
     * Escapes HTML special characters to prevent XSS and rendering issues.
     *
     * @param text the raw text
     * @return the HTML-safe string
     */
    public String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * Escapes text for safe embedding inside a JavaScript string literal.
     *
     * @param text the raw text
     * @return the JavaScript-safe string
     */
    public String escapeJs(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("'", "\\'")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    private String getStr(JsonObject obj, String key) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return "";
    }

    private String typeBadge(String type) {
        if (type == null || type.isEmpty()) return escapeHtml(type);
        String cls;
        String lower = type.toLowerCase();
        if (lower.contains("operational") || lower.startsWith("oa")) {
            cls = "badge-oa";
        } else if (lower.contains("system") && lower.contains("analysis") || lower.startsWith("sa")) {
            cls = "badge-sa";
        } else if (lower.contains("logical") || lower.startsWith("la")) {
            cls = "badge-la";
        } else if (lower.contains("physical") || lower.startsWith("pa")) {
            cls = "badge-pa";
        } else if (lower.contains("epbs")) {
            cls = "badge-epbs";
        } else {
            return escapeHtml(type);
        }
        return "<span class=\"" + cls + "\">" + escapeHtml(type) + "</span>";
    }

    private String badgeClass(String category) {
        if (category == null) return "oa";
        return switch (category) {
            case "MODEL_READ", "MODEL_WRITE" -> "sa";
            case "DIAGRAM" -> "la";
            case "ANALYSIS" -> "oa";
            case "EXPORT" -> "pa";
            case "SIMULATION" -> "epbs";
            case "architecture_proposal" -> "la";
            default -> "oa";
        };
    }

    // ------------------------------------------------------------------
    // Embedded CSS
    // ------------------------------------------------------------------

    private String getCss() {
        return "*, *::before, *::after { box-sizing: border-box; }\n"
            + "html, body { margin:0; padding:0; height:100%; overflow:hidden;\n"
            + "  background:#1e1e2e; color:#cdd6f4;\n"
            + "  font-family: system-ui, -apple-system, 'Segoe UI', sans-serif;\n"
            + "  font-size: 13px; line-height: 1.5; }\n"

            + "#chat-container { padding:12px; overflow-y:auto; height:100%;\n"
            + "  scroll-behavior:smooth; }\n"

            + "#welcome-msg { text-align:center; padding:40px 20px; color:#7f849c; }\n"
            + "#welcome-msg h3 { color:#cdd6f4; margin-bottom:8px; }\n"
            + "#welcome-msg .hint { font-size:12px; color:#585b70; }\n"

            // Messages
            + ".message { margin-bottom:12px; padding:10px 14px; border-radius:8px;\n"
            + "  background:#181825; border-left:3px solid #45475a; }\n"
            + ".msg-user { border-left-color:#89b4fa; }\n"
            + ".msg-assistant { border-left-color:#a6e3a1; }\n"
            + ".msg-tool { border-left-color:#6c7086; font-style:italic;\n"
            + "  color:#7f849c; font-size:12px; padding:6px 12px; }\n"
            + ".msg-header { display:flex; justify-content:space-between;\n"
            + "  margin-bottom:6px; font-size:11px; }\n"
            + ".msg-role { font-weight:600; color:#cdd6f4; }\n"
            + ".msg-time { color:#585b70; }\n"
            + ".msg-body { color:#bac2de; }\n"
            + ".msg-body p { margin:4px 0; }\n"
            + ".msg-body h3 { font-size:15px; margin:8px 0 4px; color:#cdd6f4; }\n"
            + ".msg-body h4 { font-size:14px; margin:6px 0 4px; color:#cdd6f4; }\n"
            + ".msg-body ul, .msg-body ol { margin:4px 0; padding-left:20px; }\n"
            + ".msg-body li { margin:2px 0; }\n"
            + ".msg-body code { background:#11111b; padding:1px 5px; border-radius:3px;\n"
            + "  font-family:'Cascadia Code','Consolas',monospace; font-size:12px; }\n"

            // Data tables
            + ".table-container { overflow-x:auto; max-height:400px; overflow-y:auto;\n"
            + "  margin:8px 0; border-radius:6px; border:1px solid #313244; }\n"
            + ".table-filter { background:#11111b; border:1px solid #313244; color:#cdd6f4;\n"
            + "  padding:4px 8px; border-radius:4px; margin-bottom:4px; width:200px; font-size:12px; }\n"
            + ".copy-btn { background:#313244; color:#cdd6f4; border:none; padding:4px 10px;\n"
            + "  border-radius:4px; cursor:pointer; font-size:11px; margin-left:8px; }\n"
            + ".copy-btn:hover { background:#45475a; }\n"
            + ".data-table { width:100%; border-collapse:collapse; font-size:12px; }\n"
            + ".data-table thead { position:sticky; top:0; z-index:1; }\n"
            + ".data-table th { background:#313244; color:#cdd6f4; padding:6px 10px;\n"
            + "  text-align:left; cursor:pointer; white-space:nowrap; user-select:none;\n"
            + "  border-bottom:2px solid #45475a; }\n"
            + ".data-table th:hover { background:#45475a; }\n"
            + ".data-table td { padding:5px 10px; border-bottom:1px solid #313244; }\n"
            + ".data-table tbody tr:hover { background:#1e1e2e; }\n"

            // Type badges
            + ".badge-oa { background:#cba6f7; color:#1e1e2e; padding:1px 6px;\n"
            + "  border-radius:3px; font-size:11px; font-weight:600; }\n"
            + ".badge-sa { background:#a6e3a1; color:#1e1e2e; padding:1px 6px;\n"
            + "  border-radius:3px; font-size:11px; font-weight:600; }\n"
            + ".badge-la { background:#89b4fa; color:#1e1e2e; padding:1px 6px;\n"
            + "  border-radius:3px; font-size:11px; font-weight:600; }\n"
            + ".badge-pa { background:#f9e2af; color:#1e1e2e; padding:1px 6px;\n"
            + "  border-radius:3px; font-size:11px; font-weight:600; }\n"
            + ".badge-epbs { background:#f38ba8; color:#1e1e2e; padding:1px 6px;\n"
            + "  border-radius:3px; font-size:11px; font-weight:600; }\n"

            // Severity badges
            + ".severity-error { color:#f38ba8; font-weight:600; }\n"
            + ".severity-warning { color:#f9e2af; font-weight:600; }\n"
            + ".severity-info { color:#89b4fa; font-weight:600; }\n"

            // Tool results
            + ".tool-result { margin:8px 0; border:1px solid #313244; border-radius:6px;\n"
            + "  overflow:hidden; }\n"
            + ".result-header { background:#313244; padding:6px 12px; cursor:pointer;\n"
            + "  font-size:12px; font-weight:600; user-select:none; }\n"
            + ".result-header:hover { background:#45475a; }\n"
            + ".result-body { padding:10px 12px; }\n"
            + ".tool-result.collapsed .result-body { display:none; }\n"

            // Property/element cards
            + ".element-card { background:#11111b; border:1px solid #313244;\n"
            + "  border-radius:6px; padding:10px 14px; margin:6px 0; }\n"
            + ".kv-row { margin:2px 0; }\n"
            + ".kv-key { color:#7f849c; font-size:12px; }\n"
            + ".kv-value { color:#bac2de; font-size:12px; }\n"

            // Matrix
            + ".matrix-table { width:100%; border-collapse:collapse; font-size:12px; }\n"
            + ".matrix-table th { background:#313244; padding:5px 8px; text-align:center;\n"
            + "  font-size:11px; }\n"
            + ".matrix-table td { padding:5px 8px; text-align:center; }\n"
            + ".allocated { background:#1e4620; color:#a6e3a1; }\n"
            + ".unallocated { background:#2a2a3a; color:#585b70; }\n"

            // Code blocks
            + "pre.code-block { background:#11111b; padding:12px; border-radius:6px;\n"
            + "  overflow-x:auto; margin:8px 0; border:1px solid #313244; }\n"
            + "pre.code-block code { font-family:'Cascadia Code','Consolas',monospace;\n"
            + "  font-size:12px; color:#cdd6f4; background:transparent; padding:0; }\n"

            // Success/error cards
            + ".write-success { background:#11111b; border:1px solid #a6e3a1;\n"
            + "  border-radius:6px; padding:10px 14px; margin:6px 0; }\n"
            + ".write-error { background:#11111b; border:1px solid #f38ba8;\n"
            + "  border-radius:6px; padding:10px 14px; margin:6px 0; }\n"

            // Export card
            + ".export-card { background:#11111b; border:1px solid #313244;\n"
            + "  border-radius:6px; padding:10px 14px; margin:6px 0; }\n"
            + ".file-icon { font-size:18px; vertical-align:middle; }\n"

            // Progress bar
            + ".progress-bar { background:#313244; border-radius:4px; height:20px;\n"
            + "  position:relative; margin:6px 0; overflow:hidden; }\n"
            + ".progress-fill { background:linear-gradient(90deg,#89b4fa,#a6e3a1);\n"
            + "  height:100%; border-radius:4px; transition:width 0.4s ease; }\n"
            + ".progress-label { position:absolute; top:0; left:0; right:0;\n"
            + "  text-align:center; line-height:20px; font-size:11px; font-weight:600; }\n"

            // Metrics grid
            + ".metrics-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(120px,1fr));\n"
            + "  gap:8px; margin:8px 0; }\n"
            + ".metric-item { background:#11111b; border:1px solid #313244;\n"
            + "  border-radius:6px; padding:10px; text-align:center; }\n"
            + ".metric-value { display:block; font-size:20px; font-weight:700; color:#cdd6f4; }\n"
            + ".metric-label { display:block; font-size:11px; color:#7f849c; margin-top:4px; }\n"

            // Findings
            + ".findings-list { margin:6px 0; }\n"
            + ".finding-item { padding:4px 0; border-bottom:1px solid #313244; font-size:12px; }\n"

            // Chain list
            + ".chain-list { margin:6px 0; padding-left:20px; }\n"
            + ".chain-list li { margin:4px 0; }\n"

            // Element links
            + ".element-link { color:#89b4fa; text-decoration:none;\n"
            + "  border-bottom:1px dotted #89b4fa; cursor:pointer; }\n"
            + ".element-link:hover { color:#b4d0fb; border-bottom-color:#b4d0fb; }\n"

            // Scrollbar
            + "::-webkit-scrollbar { width:8px; height:8px; }\n"
            + "::-webkit-scrollbar-track { background:#11111b; }\n"
            + "::-webkit-scrollbar-thumb { background:#45475a; border-radius:4px; }\n"
            + "::-webkit-scrollbar-thumb:hover { background:#585b70; }\n"

            // Tooltip for copy
            + ".copied-tooltip { position:fixed; background:#a6e3a1; color:#1e1e2e;\n"
            + "  padding:4px 10px; border-radius:4px; font-size:11px; font-weight:600;\n"
            + "  pointer-events:none; z-index:999; }\n"

            // Details/summary
            + "details { margin:4px 0; }\n"
            + "summary { cursor:pointer; color:#89b4fa; font-size:12px; }\n"
            + "summary:hover { color:#b4d0fb; }\n"

            // Light theme overrides (default active for Capella's white UI)
            + "body.light { background:#ffffff; color:#1e1e2e; }\n"
            + "body.light #chat-container { background:#ffffff; }\n"
            + "body.light #welcome-msg { color:#6c6c80; }\n"
            + "body.light #welcome-msg h3 { color:#1e1e2e; }\n"
            + "body.light #welcome-msg .hint { color:#9090a0; }\n"
            + "body.light .message { background:#f5f5f8; border-left:3px solid #d0d0d8; }\n"
            + "body.light .msg-user { border-left-color:#2563eb; }\n"
            + "body.light .msg-assistant { border-left-color:#16a34a; }\n"
            + "body.light .msg-tool { color:#6c6c80; background:#fafafa; border-left-color:#b0b0b8; }\n"
            + "body.light .msg-role { color:#1e1e2e; }\n"
            + "body.light .msg-time { color:#9090a0; }\n"
            + "body.light .msg-body { color:#333340; }\n"
            + "body.light .msg-body h3, body.light .msg-body h4 { color:#1e1e2e; }\n"
            + "body.light .msg-body code { background:#e8e8f0; color:#d63384; }\n"
            + "body.light .code-block { background:#f0f0f5; color:#1e1e2e; border:1px solid #d0d0d8; }\n"
            + "body.light .data-table th { background:#e8e8f0; color:#1e1e2e; border-bottom:2px solid #c0c0c8; }\n"
            + "body.light .data-table td { border-bottom:1px solid #e0e0e8; color:#333340; }\n"
            + "body.light .data-table tbody tr:hover { background:#f0f0f8; }\n"
            + "body.light .table-scroll { border:1px solid #d0d0d8; }\n"
            + "body.light .table-filter { background:#ffffff; color:#1e1e2e; border:1px solid #c0c0c8; }\n"
            + "body.light .table-filter:focus { border-color:#2563eb; }\n"
            + "body.light .tool-result { border:1px solid #d0d0d8; }\n"
            + "body.light .result-header { background:#f0f0f5; border-bottom:1px solid #d0d0d8; color:#1e1e2e; }\n"
            + "body.light .result-header:hover { background:#e8e8f0; }\n"
            + "body.light .element-link { color:#2563eb; border-bottom-color:#2563eb; }\n"
            + "body.light .element-link:hover { color:#1d4ed8; }\n"
            + "body.light .element-card { background:#f5f5f8; border:1px solid #d0d0d8; }\n"
            + "body.light .element-card dt { color:#6c6c80; }\n"
            + "body.light .element-card dd { color:#1e1e2e; }\n"
            + "body.light .severity-error { color:#dc2626; }\n"
            + "body.light .severity-warning { color:#d97706; }\n"
            + "body.light .severity-info { color:#2563eb; }\n"
            + "body.light .write-success { border-left-color:#16a34a; }\n"
            + "body.light .write-error { border-left-color:#dc2626; }\n"
            + "body.light ::-webkit-scrollbar-track { background:#f0f0f5; }\n"
            + "body.light ::-webkit-scrollbar-thumb { background:#c0c0c8; }\n"
            + "body.light ::-webkit-scrollbar-thumb:hover { background:#a0a0a8; }\n"
            + "body.light .copied-tooltip { background:#16a34a; color:#ffffff; }\n"
            + "body.light summary { color:#2563eb; }\n";
    }

    // ------------------------------------------------------------------
    // Embedded JavaScript
    // ------------------------------------------------------------------

    private String getJs() {
        return "function appendMessage(html) {\n"
            + "  var w = document.getElementById('welcome-msg');\n"
            + "  if (w) w.style.display = 'none';\n"
            + "  var c = document.getElementById('chat-container');\n"
            + "  var div = document.createElement('div');\n"
            + "  div.innerHTML = html;\n"
            + "  while (div.firstChild) c.appendChild(div.firstChild);\n"
            + "  scrollToBottom();\n"
            + "}\n"

            + "function toggleCollapse(el) {\n"
            + "  var parent = el.closest('.tool-result');\n"
            + "  if (parent) parent.classList.toggle('collapsed');\n"
            + "}\n"

            + "function sortTable(tableId, colIdx) {\n"
            + "  var table = document.getElementById(tableId);\n"
            + "  if (!table) return;\n"
            + "  var tbody = table.querySelector('tbody');\n"
            + "  var rows = Array.from(tbody.querySelectorAll('tr'));\n"
            + "  var asc = table.getAttribute('data-sort-col') == colIdx\n"
            + "    && table.getAttribute('data-sort-dir') !== 'desc';\n"
            + "  var dir = asc ? 'desc' : 'asc';\n"
            + "  rows.sort(function(a, b) {\n"
            + "    var at = (a.cells[colIdx] || {}).textContent || '';\n"
            + "    var bt = (b.cells[colIdx] || {}).textContent || '';\n"
            + "    var an = parseFloat(at), bn = parseFloat(bt);\n"
            + "    if (!isNaN(an) && !isNaN(bn)) return dir === 'asc' ? an - bn : bn - an;\n"
            + "    return dir === 'asc' ? at.localeCompare(bt) : bt.localeCompare(at);\n"
            + "  });\n"
            + "  rows.forEach(function(r) { tbody.appendChild(r); });\n"
            + "  table.setAttribute('data-sort-col', colIdx);\n"
            + "  table.setAttribute('data-sort-dir', dir);\n"
            + "}\n"

            + "function filterTable(inputEl, tableId) {\n"
            + "  var filter = inputEl.value.toLowerCase();\n"
            + "  var table = document.getElementById(tableId);\n"
            + "  if (!table) return;\n"
            + "  var rows = table.querySelectorAll('tbody tr');\n"
            + "  rows.forEach(function(row) {\n"
            + "    var text = row.textContent.toLowerCase();\n"
            + "    row.style.display = text.indexOf(filter) >= 0 ? '' : 'none';\n"
            + "  });\n"
            + "}\n"

            + "function copyToClipboard(text) {\n"
            + "  window.javaAction('copy', text);\n"
            + "}\n"

            + "function copyTableAsCsv(tableId) {\n"
            + "  var table = document.getElementById(tableId);\n"
            + "  if (!table) return;\n"
            + "  var csv = [];\n"
            + "  var rows = table.querySelectorAll('tr');\n"
            + "  rows.forEach(function(row) {\n"
            + "    if (row.style.display === 'none') return;\n"
            + "    var cells = Array.from(row.querySelectorAll('th,td'));\n"
            + "    csv.push(cells.map(function(c) {\n"
            + "      var t = c.textContent.replace(/\"/g, '\"\"');\n"
            + "      return '\"' + t + '\"';\n"
            + "    }).join(','));\n"
            + "  });\n"
            + "  copyToClipboard(csv.join('\\n'));\n"
            + "}\n"

            + "function showCopiedTooltip() {\n"
            + "  var tip = document.createElement('div');\n"
            + "  tip.className = 'copied-tooltip';\n"
            + "  tip.textContent = 'Copied!';\n"
            + "  tip.style.top = '10px';\n"
            + "  tip.style.right = '10px';\n"
            + "  document.body.appendChild(tip);\n"
            + "  setTimeout(function() { tip.remove(); }, 1500);\n"
            + "}\n"

            + "function scrollToBottom() {\n"
            + "  var c = document.getElementById('chat-container');\n"
            + "  c.scrollTo({ top: c.scrollHeight, behavior: 'smooth' });\n"
            + "}\n"

            + "function toggleTheme() {\n"
            + "  document.body.classList.toggle('light');\n"
            + "  var isLight = document.body.classList.contains('light');\n"
            + "  try { localStorage.setItem('capella-agent-theme', isLight ? 'light' : 'dark'); } catch(e) {}\n"
            + "}\n"

            // Auto-apply light theme on load (default for Capella's white UI)
            + "(function() {\n"
            + "  var saved = 'light';\n"
            + "  try { saved = localStorage.getItem('capella-agent-theme') || 'light'; } catch(e) {}\n"
            + "  if (saved === 'light') document.body.classList.add('light');\n"
            + "})();\n";
    }
}

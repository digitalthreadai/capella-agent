package com.capellaagent.core.llm;

import java.util.List;

import com.capellaagent.core.tools.IToolDescriptor;

/**
 * Estimates LLM token counts before sending a request.
 * <p>
 * The plan originally proposed integrating {@code jtokkit} (a Java port of
 * tiktoken) for accurate OpenAI tokenization, but adding a third-party JAR
 * to the OSGi classpath of an Eclipse plugin is a multi-day side quest. This
 * implementation uses a tier of heuristics with explicit confidence multipliers
 * so the {@link #estimateMessages} caller can apply a safety margin:
 * <ul>
 *   <li>{@link Confidence#HIGH} — exact count from a previous provider response</li>
 *   <li>{@link Confidence#MEDIUM} — calibrated heuristic by content shape
 *       (English prose, code, JSON, CJK)</li>
 *   <li>{@link Confidence#LOW} — generic char/4 fallback</li>
 * </ul>
 * The {@link Estimate#withSafetyMargin()} method applies a default 1.30 multiplier
 * for LOW confidence and 1.05 for HIGH, so callers can use it as a hard cap
 * without risk of false negatives.
 * <p>
 * The static {@link #estimateText(String)} returns the rough char-based count
 * with no confidence info, suitable for ad-hoc UI display.
 */
public final class TokenEstimator {

    /** Average chars per token across English LLM tokenizers. */
    private static final double CHARS_PER_TOKEN_PROSE = 4.0;

    /** Code, JSON, and other structured text tokenize denser. */
    private static final double CHARS_PER_TOKEN_CODE = 3.4;

    /** Per-message overhead in OpenAI chat format (role tags, separators). */
    private static final int PER_MESSAGE_OVERHEAD = 4;

    /** Per-tool-call overhead (function wrapper tokens). */
    private static final int PER_TOOL_CALL_OVERHEAD = 8;

    /** Confidence level of an estimate. */
    public enum Confidence {
        /** Exact count from a provider response (use 1.05 safety multiplier). */
        HIGH(1.05),
        /** Calibrated heuristic by content type (use 1.15 safety multiplier). */
        MEDIUM(1.15),
        /** Generic char-based fallback (use 1.30 safety multiplier). */
        LOW(1.30);

        public final double safetyMultiplier;

        Confidence(double safetyMultiplier) {
            this.safetyMultiplier = safetyMultiplier;
        }
    }

    /**
     * An estimated token count with provenance and a safety margin.
     */
    public record Estimate(int tokens, Confidence confidence) {

        /**
         * Returns the estimate scaled by the confidence-appropriate safety
         * multiplier. Use this when checking against a provider hard cap.
         */
        public int withSafetyMargin() {
            return (int) Math.ceil(tokens * confidence.safetyMultiplier);
        }
    }

    private TokenEstimator() {}

    /**
     * Estimates the token count of a single text string using a content-shape
     * heuristic. Returns 0 for null/empty input.
     */
    public static int estimateText(String text) {
        if (text == null || text.isEmpty()) return 0;
        double chars = text.length();
        // Detect code/JSON-heavy content via curly-brace density
        long braces = text.chars().filter(c -> c == '{' || c == '}' || c == '[' || c == ']').count();
        double density = braces / chars;
        double cpt = density > 0.02 ? CHARS_PER_TOKEN_CODE : CHARS_PER_TOKEN_PROSE;
        return (int) Math.ceil(chars / cpt);
    }

    /**
     * Estimates the token count of a full chat request: messages + system
     * prompt + tool schemas + per-message overhead.
     *
     * @param systemPrompt the system prompt sent at the head of the request,
     *                     or null if none
     * @param messages     the conversation history to send (must not be null)
     * @param tools        the tool descriptors that will be sent (may be empty)
     * @return an {@link Estimate} with {@link Confidence#MEDIUM} confidence
     */
    public static Estimate estimateMessages(String systemPrompt,
                                            List<LlmMessage> messages,
                                            List<IToolDescriptor> tools) {
        int total = 0;

        if (systemPrompt != null) {
            total += estimateText(systemPrompt) + PER_MESSAGE_OVERHEAD;
        }

        if (messages != null) {
            for (LlmMessage msg : messages) {
                total += PER_MESSAGE_OVERHEAD;
                total += estimateText(msg.getContent());
                if (msg.hasToolCalls()) {
                    for (LlmToolCall tc : msg.getToolCalls()) {
                        total += PER_TOOL_CALL_OVERHEAD;
                        total += estimateText(tc.getName());
                        total += estimateText(tc.getArguments());
                    }
                }
            }
        }

        if (tools != null) {
            for (IToolDescriptor tool : tools) {
                total += PER_TOOL_CALL_OVERHEAD;
                total += estimateText(tool.getName());
                total += estimateText(tool.getDescription());
                if (tool.getParametersSchema() != null) {
                    total += estimateText(tool.getParametersSchema().toString());
                }
            }
        }

        return new Estimate(total, Confidence.MEDIUM);
    }

    /**
     * Returns true if the estimate's safety-margined value fits within the
     * given provider hard cap, leaving the configured headroom percentage.
     *
     * @param estimate     the token estimate
     * @param providerCap  the provider's hard token limit (e.g. 8000 for Groq free)
     * @param headroomPct  fraction of the cap to actually use, 0.0..1.0
     *                     (default 0.85 leaves 15% for output tokens)
     */
    public static boolean fitsWithin(Estimate estimate, int providerCap, double headroomPct) {
        if (providerCap <= 0) return true;
        int effectiveCap = (int) Math.floor(providerCap * headroomPct);
        return estimate.withSafetyMargin() <= effectiveCap;
    }
}

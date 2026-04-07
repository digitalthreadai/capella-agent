package com.capellaagent.core.llm;

import java.util.Objects;

/**
 * Normalized usage report from a single LLM call.
 * <p>
 * Each provider returns usage in a different JSON shape:
 * <ul>
 *   <li>OpenAI / Groq / GitHub Models / DeepSeek: {@code usage: {prompt_tokens, completion_tokens}}</li>
 *   <li>Anthropic: {@code usage: {input_tokens, output_tokens, cache_read_input_tokens}}</li>
 *   <li>Ollama: {@code prompt_eval_count, eval_count}</li>
 *   <li>Gemini: {@code usageMetadata: {promptTokenCount, candidatesTokenCount, ...}}</li>
 * </ul>
 * Each {@link ILlmProvider} implementation parses its own response shape and
 * returns this normalized record so {@code TokenUsageTracker} doesn't need to
 * know about provider-specific JSON.
 *
 * @param inputTokens  prompt / input token count
 * @param outputTokens completion / output token count
 * @param cachedInputTokens count of input tokens served from cache (0 if unknown
 *                          or not supported by the provider)
 * @param reasoningTokens   count of "thinking" tokens for o1/o3/Gemini-thinking
 *                          (0 if not supported)
 * @param source            whether the numbers came from the provider response
 *                          ({@link Source#EXACT}) or were estimated locally
 *                          ({@link Source#ESTIMATED})
 */
public record LlmUsage(
        int inputTokens,
        int outputTokens,
        int cachedInputTokens,
        int reasoningTokens,
        Source source) {

    /** How the usage numbers were obtained. */
    public enum Source {
        /** Numbers came directly from the provider's response body. */
        EXACT,
        /** Numbers were estimated locally (e.g., char/4 heuristic). */
        ESTIMATED
    }

    /** Returns total tokens used (input + output, excluding reasoning). */
    public int totalTokens() {
        return inputTokens + outputTokens;
    }

    /** Returns total tokens billed (input + output + reasoning). */
    public int billableTokens() {
        return inputTokens + outputTokens + reasoningTokens;
    }

    /** A zero-usage record. Convenient for "no data available" cases. */
    public static LlmUsage empty() {
        return new LlmUsage(0, 0, 0, 0, Source.ESTIMATED);
    }

    /** Convenience factory for an exact (input, output) pair with no caching/reasoning. */
    public static LlmUsage exact(int inputTokens, int outputTokens) {
        return new LlmUsage(inputTokens, outputTokens, 0, 0, Source.EXACT);
    }

    /** Convenience factory for an estimated (input, output) pair. */
    public static LlmUsage estimated(int inputTokens, int outputTokens) {
        return new LlmUsage(inputTokens, outputTokens, 0, 0, Source.ESTIMATED);
    }

    /** Adds two usage records (sums each field). */
    public LlmUsage plus(LlmUsage other) {
        Objects.requireNonNull(other, "other");
        return new LlmUsage(
            this.inputTokens + other.inputTokens,
            this.outputTokens + other.outputTokens,
            this.cachedInputTokens + other.cachedInputTokens,
            this.reasoningTokens + other.reasoningTokens,
            // If either was estimated, the sum is at-best estimated
            (this.source == Source.EXACT && other.source == Source.EXACT)
                ? Source.EXACT : Source.ESTIMATED);
    }
}

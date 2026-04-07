package com.capellaagent.core.metering;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.capellaagent.core.llm.LlmUsage;

/**
 * Thread-safe tracker for LLM token usage and estimated cost.
 * <p>
 * Architect review (Issue A1) extracted this out of the original
 * "TokenUsageTracker in core.llm" placement. It now lives in its own
 * {@code com.capellaagent.core.metering} package and does not depend on
 * workspace state. The chat job injects it via the controller builder;
 * the UI status bar widget subscribes to it via {@link #addListener}.
 *
 * <h2>Concurrency</h2>
 * <ul>
 *   <li>All counters use {@link LongAdder} so updates never block</li>
 *   <li>Per-(source, provider) buckets live in a {@link ConcurrentHashMap}</li>
 *   <li>Listeners are held in {@link CopyOnWriteArrayList} so reads during
 *       notification do not contend with adds</li>
 *   <li>Notification happens on the thread that called {@link #record}; the
 *       UI layer is responsible for dispatching to Display.asyncExec</li>
 * </ul>
 *
 * <h2>Buckets</h2>
 * Every usage record is tagged with a {@link Source}:
 * <ul>
 *   <li>{@link Source#CHAT_VIEW} — token spend from the in-workbench chat</li>
 *   <li>{@link Source#MCP_BRIDGE} — token spend when Claude Code calls
 *       Capella tools via the MCP bridge (counts the tool-execution side only;
 *       the LLM side is on Claude Code's bill)</li>
 * </ul>
 */
public final class TokenUsageTracker {

    // === Process-wide singleton ===
    private static final TokenUsageTracker INSTANCE = new TokenUsageTracker();

    /**
     * Returns the process-wide singleton instance.
     * <p>
     * Used by the status bar widget to subscribe to token updates without
     * requiring injection. The ChatJob and ChatSessionController continue to
     * use injected instances for testability.
     */
    public static TokenUsageTracker getInstance() {
        return INSTANCE;
    }

    /** Where the usage record came from. */
    public enum Source {
        CHAT_VIEW, MCP_BRIDGE
    }

    /** A single listener callback invoked after each {@link #record} call. */
    public interface Listener extends Consumer<Snapshot> {}

    /**
     * An immutable snapshot of aggregate usage at a point in time.
     * <p>
     * Returned by {@link #snapshot()} and passed to listeners. Use this type
     * on UI threads rather than reading the tracker's internal counters
     * directly, to avoid half-updated state.
     */
    public record Snapshot(
            long totalInputTokens,
            long totalOutputTokens,
            long totalCachedTokens,
            long totalReasoningTokens,
            int recordCount,
            long updatedAtEpochMs) {

        public long totalTokens() {
            return totalInputTokens + totalOutputTokens;
        }

        public long billableTokens() {
            return totalInputTokens + totalOutputTokens + totalReasoningTokens;
        }
    }

    /** A single bucket keyed by source+provider. */
    private static final class Bucket {
        final LongAdder input = new LongAdder();
        final LongAdder output = new LongAdder();
        final LongAdder cached = new LongAdder();
        final LongAdder reasoning = new LongAdder();
        final LongAdder count = new LongAdder();
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile long lastUpdateEpochMs = 0L;

    /** Records a usage report. Notifies all listeners after the update. */
    public void record(Source source, String providerId, LlmUsage usage) {
        if (usage == null || source == null) return;
        String key = source.name() + "|" + (providerId == null ? "unknown" : providerId);
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket());
        b.input.add(usage.inputTokens());
        b.output.add(usage.outputTokens());
        b.cached.add(usage.cachedInputTokens());
        b.reasoning.add(usage.reasoningTokens());
        b.count.increment();
        lastUpdateEpochMs = System.currentTimeMillis();

        // Fire listeners with a fresh snapshot. We keep this outside any
        // synchronization so listeners can safely take the tracker's monitor
        // themselves (none of the tracker's fields are guarded by it).
        Snapshot snap = snapshot();
        for (Listener l : listeners) {
            try {
                l.accept(snap);
            } catch (RuntimeException ignored) {
                // One listener's bug must not break another's notification
            }
        }
    }

    /** Returns the current aggregate snapshot. Safe to call from any thread. */
    public Snapshot snapshot() {
        long input = 0, output = 0, cached = 0, reasoning = 0;
        int count = 0;
        for (Bucket b : buckets.values()) {
            input += b.input.sum();
            output += b.output.sum();
            cached += b.cached.sum();
            reasoning += b.reasoning.sum();
            count += (int) b.count.sum();
        }
        return new Snapshot(input, output, cached, reasoning, count, lastUpdateEpochMs);
    }

    /** Returns an immutable view of aggregate usage per (source, provider). */
    public Map<String, Snapshot> snapshotByBucket() {
        Map<String, Snapshot> result = new java.util.LinkedHashMap<>();
        for (var e : buckets.entrySet()) {
            Bucket b = e.getValue();
            result.put(e.getKey(), new Snapshot(
                b.input.sum(), b.output.sum(),
                b.cached.sum(), b.reasoning.sum(),
                (int) b.count.sum(), lastUpdateEpochMs));
        }
        return result;
    }

    /** Adds a listener. Safe during concurrent {@link #record} calls. */
    public void addListener(Listener listener) {
        if (listener != null) listeners.add(listener);
    }

    /** Removes a listener. */
    public void removeListener(Listener listener) {
        if (listener != null) listeners.remove(listener);
    }

    /** Clears all counters (keeps listeners). For tests and manual reset. */
    public void reset() {
        buckets.clear();
        lastUpdateEpochMs = 0L;
    }

    /** Returns the number of registered listeners (public for tests). */
    public int listenerCount() {
        return listeners.size();
    }
}

package com.capellaagent.core.llm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Per-provider circuit breaker for LLM calls.
 * <p>
 * Transitions:
 * <pre>
 *   CLOSED ──(5 errors within 120s)──> OPEN
 *   OPEN   ──(5 minutes elapsed)────> HALF_OPEN
 *   HALF_OPEN ──(success)──> CLOSED
 *   HALF_OPEN ──(failure)──> OPEN (with exponential backoff, max 30 min)
 * </pre>
 * <p>
 * State is per-provider-id, so switching providers in Preferences immediately
 * unblocks the user. Process-wide singleton.
 * <p>
 * Guarded by per-entry locks ({@link AtomicReference#compareAndSet}) to avoid a
 * global monitor on every LLM call.
 */
public final class ProviderCircuitBreaker {

    /** Errors required to open the breaker. */
    public static final int ERROR_THRESHOLD = 5;

    /** Sliding window for counting errors. */
    public static final long WINDOW_MILLIS = 120_000L;

    /** Initial open duration before HALF_OPEN probe. */
    public static final long BASE_OPEN_MILLIS = 5 * 60_000L;

    /** Ceiling for exponential backoff. */
    public static final long MAX_OPEN_MILLIS = 30 * 60_000L;

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private static final ProviderCircuitBreaker INSTANCE = new ProviderCircuitBreaker();

    public static ProviderCircuitBreaker getInstance() {
        return INSTANCE;
    }

    private final Map<String, AtomicReference<Entry>> providers = new ConcurrentHashMap<>();

    private ProviderCircuitBreaker() { }

    private AtomicReference<Entry> entryFor(String providerId) {
        return providers.computeIfAbsent(providerId,
            k -> new AtomicReference<>(Entry.initial()));
    }

    /**
     * Checks whether a call should be allowed.
     * @throws CircuitOpenException if the breaker is open
     */
    public void checkOrThrow(String providerId) throws CircuitOpenException {
        AtomicReference<Entry> ref = entryFor(providerId);
        while (true) {
            Entry current = ref.get();
            long now = System.currentTimeMillis();
            if (current.state == State.OPEN) {
                if (now >= current.reopenAt) {
                    // Promote to HALF_OPEN probe.
                    Entry probe = current.toHalfOpen();
                    if (ref.compareAndSet(current, probe)) {
                        return; // allow one probe call
                    }
                    continue;
                }
                long remaining = (current.reopenAt - now) / 1000;
                throw new CircuitOpenException(
                    "Provider '" + providerId + "' temporarily unavailable "
                    + "— retrying automatically in " + Math.max(1, remaining) + "s.");
            }
            return; // CLOSED or HALF_OPEN — allow
        }
    }

    /** Record a successful LLM call. */
    public void recordSuccess(String providerId) {
        AtomicReference<Entry> ref = entryFor(providerId);
        while (true) {
            Entry current = ref.get();
            if (current.state == State.CLOSED
                    && current.errorCount == 0
                    && current.consecutiveOpens == 0) {
                return; // no state change needed
            }
            if (ref.compareAndSet(current, Entry.initial())) {
                return;
            }
        }
    }

    /** Record a failed LLM call (retriable errors: 429, 5xx, network). */
    public void recordFailure(String providerId) {
        AtomicReference<Entry> ref = entryFor(providerId);
        while (true) {
            Entry current = ref.get();
            long now = System.currentTimeMillis();

            if (current.state == State.HALF_OPEN) {
                // Probe failed — exponential backoff.
                int nextOpens = current.consecutiveOpens + 1;
                long openDuration = Math.min(
                    MAX_OPEN_MILLIS,
                    BASE_OPEN_MILLIS * (1L << Math.min(4, nextOpens - 1)));
                Entry next = new Entry(
                    State.OPEN, 0, now, now + openDuration, nextOpens);
                if (ref.compareAndSet(current, next)) return;
                continue;
            }

            // CLOSED: slide window + count.
            long windowStart = now - WINDOW_MILLIS;
            int newCount = (current.firstErrorAt >= windowStart)
                ? current.errorCount + 1 : 1;
            long firstAt = (current.firstErrorAt >= windowStart)
                ? current.firstErrorAt : now;

            Entry next;
            if (newCount >= ERROR_THRESHOLD) {
                next = new Entry(State.OPEN, 0, now,
                    now + BASE_OPEN_MILLIS, 1);
            } else {
                next = new Entry(State.CLOSED, newCount, firstAt, 0L,
                    current.consecutiveOpens);
            }
            if (ref.compareAndSet(current, next)) return;
        }
    }

    /** Test-only reset. */
    public void resetAll() {
        providers.clear();
    }

    /** Snapshot of breaker state for diagnostics. */
    public State stateOf(String providerId) {
        return entryFor(providerId).get().state;
    }

    /** Exception signalling the breaker rejected a call. */
    public static final class CircuitOpenException extends LlmException {
        private static final long serialVersionUID = 1L;
        public CircuitOpenException(String message) {
            super(LlmException.ERR_RATE_LIMITED, message);
        }
    }

    private static final class Entry {
        final State state;
        final int errorCount;
        final long firstErrorAt;
        final long reopenAt;
        final int consecutiveOpens;

        Entry(State state, int errorCount, long firstErrorAt,
              long reopenAt, int consecutiveOpens) {
            this.state = state;
            this.errorCount = errorCount;
            this.firstErrorAt = firstErrorAt;
            this.reopenAt = reopenAt;
            this.consecutiveOpens = consecutiveOpens;
        }

        static Entry initial() {
            return new Entry(State.CLOSED, 0, 0L, 0L, 0);
        }

        Entry toHalfOpen() {
            return new Entry(State.HALF_OPEN, 0, 0L, 0L, consecutiveOpens);
        }
    }
}

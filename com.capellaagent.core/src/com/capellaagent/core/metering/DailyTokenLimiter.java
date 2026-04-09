package com.capellaagent.core.metering;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.logging.Logger;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

/**
 * Per-day aggregate token cap across all LLM providers.
 * <p>
 * Backing store: Eclipse {@link InstanceScope} preferences under the
 * {@code com.capellaagent.core/daily-tokens} node. Resets at 00:00 UTC.
 * <p>
 * This is a cost protection against runaway loops or prompt-injection driven
 * token exhaustion. The default cap (10M tokens/day) is generous for real
 * workflows but low enough to limit damage from a compromised session.
 * <p>
 * Thread safety: writes are serialised on {@code this}. Reads are best-effort
 * but updated immediately after every LLM call via {@link #record(long)}.
 */
public final class DailyTokenLimiter {

    private static final Logger LOG = Logger.getLogger(DailyTokenLimiter.class.getName());

    private static final String PREF_NODE = "com.capellaagent.core";
    private static final String KEY_DATE = "dailyTokens.date";
    private static final String KEY_COUNT = "dailyTokens.count";
    private static final String KEY_CAP = "dailyTokens.cap";

    /** Default hard cap: 10 million tokens per UTC day. */
    public static final long DEFAULT_DAILY_CAP = 10_000_000L;

    private static volatile DailyTokenLimiter INSTANCE;

    public static synchronized DailyTokenLimiter getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DailyTokenLimiter();
        }
        return INSTANCE;
    }

    private DailyTokenLimiter() { }

    private Preferences node() {
        return InstanceScope.INSTANCE.getNode(PREF_NODE);
    }

    private String todayKey() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    /** Returns the configured cap, or {@link #DEFAULT_DAILY_CAP} if unset. */
    public synchronized long getCap() {
        return node().getLong(KEY_CAP, DEFAULT_DAILY_CAP);
    }

    /** Persists a new daily cap (takes effect immediately). */
    public synchronized void setCap(long cap) {
        if (cap < 0) cap = 0;
        node().putLong(KEY_CAP, cap);
        flush();
    }

    /** Current token total for the active UTC day (rolls over automatically). */
    public synchronized long getUsedToday() {
        Preferences n = node();
        String today = todayKey();
        String storedDate = n.get(KEY_DATE, null);
        if (!today.equals(storedDate)) {
            // New UTC day — reset counter.
            n.put(KEY_DATE, today);
            n.putLong(KEY_COUNT, 0L);
            flush();
            return 0L;
        }
        return n.getLong(KEY_COUNT, 0L);
    }

    /**
     * Checks whether {@code additionalTokens} can be added without exceeding
     * the cap. Does NOT record the usage.
     */
    public synchronized boolean canConsume(long additionalTokens) {
        long cap = getCap();
        if (cap <= 0) return true; // cap disabled
        return getUsedToday() + additionalTokens <= cap;
    }

    /**
     * Records token usage against the current UTC day. Callers should invoke
     * this after every LLM response, even if {@link #canConsume(long)} was
     * not checked first.
     */
    public synchronized void record(long tokens) {
        if (tokens <= 0) return;
        Preferences n = node();
        String today = todayKey();
        String storedDate = n.get(KEY_DATE, null);
        long current = today.equals(storedDate) ? n.getLong(KEY_COUNT, 0L) : 0L;
        n.put(KEY_DATE, today);
        n.putLong(KEY_COUNT, current + tokens);
        flush();
    }

    private void flush() {
        try {
            node().flush();
        } catch (BackingStoreException e) {
            LOG.fine("DailyTokenLimiter flush failed: " + e.getMessage());
        }
    }
}

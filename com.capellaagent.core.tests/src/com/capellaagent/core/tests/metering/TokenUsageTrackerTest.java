package com.capellaagent.core.tests.metering;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.capellaagent.core.llm.LlmUsage;
import com.capellaagent.core.metering.TokenUsageTracker;
import com.capellaagent.core.metering.TokenUsageTracker.Snapshot;
import com.capellaagent.core.metering.TokenUsageTracker.Source;

/**
 * Unit tests for {@link TokenUsageTracker}.
 */
class TokenUsageTrackerTest {

    @Test
    @DisplayName("new tracker has zero aggregate")
    void newTrackerZero() {
        TokenUsageTracker t = new TokenUsageTracker();
        Snapshot s = t.snapshot();
        assertEquals(0, s.totalInputTokens());
        assertEquals(0, s.totalOutputTokens());
        assertEquals(0, s.recordCount());
    }

    @Test
    @DisplayName("record adds to the aggregate")
    void recordAdds() {
        TokenUsageTracker t = new TokenUsageTracker();
        t.record(Source.CHAT_VIEW, "openai", LlmUsage.exact(100, 50));
        t.record(Source.CHAT_VIEW, "openai", LlmUsage.exact(200, 75));
        Snapshot s = t.snapshot();
        assertEquals(300, s.totalInputTokens());
        assertEquals(125, s.totalOutputTokens());
        assertEquals(425, s.totalTokens());
        assertEquals(2, s.recordCount());
    }

    @Test
    @DisplayName("record separates buckets by source and provider")
    void recordSeparatesBuckets() {
        TokenUsageTracker t = new TokenUsageTracker();
        t.record(Source.CHAT_VIEW, "openai", LlmUsage.exact(100, 50));
        t.record(Source.MCP_BRIDGE, "anthropic", LlmUsage.exact(200, 75));
        t.record(Source.CHAT_VIEW, "anthropic", LlmUsage.exact(300, 100));

        var byBucket = t.snapshotByBucket();
        assertEquals(3, byBucket.size(), "three distinct (source, provider) buckets");

        // aggregate snapshot merges all
        Snapshot agg = t.snapshot();
        assertEquals(600, agg.totalInputTokens());
        assertEquals(225, agg.totalOutputTokens());
    }

    @Test
    @DisplayName("listener is notified after each record")
    void listenerNotified() {
        TokenUsageTracker t = new TokenUsageTracker();
        AtomicInteger notifications = new AtomicInteger();
        t.addListener(snapshot -> notifications.incrementAndGet());

        t.record(Source.CHAT_VIEW, "openai", LlmUsage.exact(10, 5));
        t.record(Source.CHAT_VIEW, "openai", LlmUsage.exact(20, 10));

        assertEquals(2, notifications.get());
    }

    @Test
    @DisplayName("listener receives current snapshot, not previous")
    void listenerReceivesCurrent() {
        TokenUsageTracker t = new TokenUsageTracker();
        java.util.concurrent.atomic.AtomicLong lastSeen = new java.util.concurrent.atomic.AtomicLong();
        t.addListener(snap -> lastSeen.set(snap.totalInputTokens()));

        t.record(Source.CHAT_VIEW, "openai", LlmUsage.exact(100, 50));
        assertEquals(100L, lastSeen.get());
        t.record(Source.CHAT_VIEW, "openai", LlmUsage.exact(200, 75));
        assertEquals(300L, lastSeen.get(), "listener must see the aggregate, not just the delta");
    }

    @Test
    @DisplayName("one throwing listener does not break others")
    void oneThrowingListenerDoesNotBreakOthers() {
        TokenUsageTracker t = new TokenUsageTracker();
        AtomicInteger good = new AtomicInteger();
        t.addListener(s -> { throw new RuntimeException("boom"); });
        t.addListener(s -> good.incrementAndGet());

        t.record(Source.CHAT_VIEW, "openai", LlmUsage.exact(10, 5));
        assertEquals(1, good.get(),
            "second listener should still be called after first throws");
    }

    @Test
    @DisplayName("reset clears buckets but keeps listeners")
    void resetClearsButKeepsListeners() {
        TokenUsageTracker t = new TokenUsageTracker();
        t.addListener(s -> {});
        t.record(Source.CHAT_VIEW, "openai", LlmUsage.exact(100, 50));
        assertEquals(1, t.listenerCount());

        t.reset();
        assertEquals(0, t.snapshot().totalInputTokens());
        assertEquals(1, t.listenerCount(), "listeners survive reset");
    }

    @Test
    @DisplayName("null usage is safely ignored")
    void nullUsageIgnored() {
        TokenUsageTracker t = new TokenUsageTracker();
        t.record(Source.CHAT_VIEW, "openai", null);
        t.record(null, "openai", LlmUsage.exact(10, 5));
        assertEquals(0, t.snapshot().recordCount());
    }

    @Test
    @DisplayName("billableTokens includes reasoning")
    void billableIncludesReasoning() {
        TokenUsageTracker t = new TokenUsageTracker();
        t.record(Source.CHAT_VIEW, "openai", new LlmUsage(100, 50, 0, 30, LlmUsage.Source.EXACT));
        Snapshot s = t.snapshot();
        assertEquals(150, s.totalTokens());
        assertEquals(180, s.billableTokens());
    }

    @Test
    @DisplayName("concurrent records from many threads all count")
    void concurrentRecords() throws InterruptedException {
        TokenUsageTracker t = new TokenUsageTracker();
        int threadCount = 10;
        int perThread = 100;
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < perThread; j++) {
                    t.record(Source.CHAT_VIEW, "openai", LlmUsage.exact(1, 1));
                }
            });
        }
        for (Thread th : threads) th.start();
        for (Thread th : threads) th.join();

        Snapshot s = t.snapshot();
        assertEquals((long) threadCount * perThread, s.totalInputTokens());
        assertEquals((long) threadCount * perThread, s.recordCount());
    }
}

package com.capellaagent.core.tests.llm;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.capellaagent.core.llm.LlmUsage;
import com.capellaagent.core.llm.LlmUsage.Source;

/**
 * Unit tests for {@link LlmUsage}.
 */
class LlmUsageTest {

    @Test
    @DisplayName("totalTokens excludes reasoning tokens")
    void totalTokensExcludesReasoning() {
        LlmUsage u = new LlmUsage(100, 50, 0, 30, Source.EXACT);
        assertEquals(150, u.totalTokens());
        assertEquals(180, u.billableTokens());
    }

    @Test
    @DisplayName("empty() returns zero usage with ESTIMATED source")
    void emptyReturnsZeroEstimated() {
        LlmUsage u = LlmUsage.empty();
        assertEquals(0, u.totalTokens());
        assertEquals(Source.ESTIMATED, u.source());
    }

    @Test
    @DisplayName("exact() factory marks as EXACT")
    void exactMarksExact() {
        LlmUsage u = LlmUsage.exact(100, 50);
        assertEquals(Source.EXACT, u.source());
        assertEquals(100, u.inputTokens());
        assertEquals(50, u.outputTokens());
    }

    @Test
    @DisplayName("estimated() factory marks as ESTIMATED")
    void estimatedMarksEstimated() {
        LlmUsage u = LlmUsage.estimated(100, 50);
        assertEquals(Source.ESTIMATED, u.source());
    }

    @Test
    @DisplayName("plus() sums both records and downgrades source if either is ESTIMATED")
    void plusSumsAndDowngradesSource() {
        LlmUsage a = LlmUsage.exact(100, 50);
        LlmUsage b = LlmUsage.exact(200, 75);
        LlmUsage sum = a.plus(b);
        assertEquals(300, sum.inputTokens());
        assertEquals(125, sum.outputTokens());
        assertEquals(Source.EXACT, sum.source());

        LlmUsage est = LlmUsage.estimated(10, 10);
        assertEquals(Source.ESTIMATED, a.plus(est).source(),
            "EXACT + ESTIMATED must be ESTIMATED");
    }

    @Test
    @DisplayName("plus() sums cached and reasoning tokens")
    void plusSumsCachedAndReasoning() {
        LlmUsage a = new LlmUsage(100, 50, 30, 5, Source.EXACT);
        LlmUsage b = new LlmUsage(200, 75, 60, 10, Source.EXACT);
        LlmUsage sum = a.plus(b);
        assertEquals(90, sum.cachedInputTokens());
        assertEquals(15, sum.reasoningTokens());
    }

    @Test
    @DisplayName("plus() rejects null")
    void plusRejectsNull() {
        assertThrows(NullPointerException.class, () -> LlmUsage.empty().plus(null));
    }
}

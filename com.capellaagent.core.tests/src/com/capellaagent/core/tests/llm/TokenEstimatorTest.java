package com.capellaagent.core.tests.llm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.capellaagent.core.llm.LlmMessage;
import com.capellaagent.core.llm.LlmToolCall;
import com.capellaagent.core.llm.TokenEstimator;
import com.capellaagent.core.llm.TokenEstimator.Confidence;
import com.capellaagent.core.llm.TokenEstimator.Estimate;

/**
 * Unit tests for {@link TokenEstimator}.
 */
class TokenEstimatorTest {

    @Test
    @DisplayName("estimateText returns 0 for null and empty")
    void estimateTextZeroForNullAndEmpty() {
        assertEquals(0, TokenEstimator.estimateText(null));
        assertEquals(0, TokenEstimator.estimateText(""));
    }

    @Test
    @DisplayName("estimateText scales roughly with content length for prose")
    void estimateTextScalesWithLengthProse() {
        // English prose: ~4 chars per token
        String prose = "The quick brown fox jumps over the lazy dog. ".repeat(10);
        int tokens = TokenEstimator.estimateText(prose);
        // 450 chars / 4 cpt ~= 112 tokens, allow some tolerance
        assertTrue(tokens >= 100 && tokens <= 140,
            "expected ~112 prose tokens, got " + tokens);
    }

    @Test
    @DisplayName("estimateText counts code/JSON denser than prose")
    void estimateTextDenserForCode() {
        String prose = "abcdefgh".repeat(50); // 400 chars, no braces
        String json = "{\"a\":1,\"b\":2,\"c\":3}".repeat(20); // 400 chars, brace-heavy
        int proseTokens = TokenEstimator.estimateText(prose);
        int jsonTokens = TokenEstimator.estimateText(json);
        assertTrue(jsonTokens > proseTokens,
            "JSON should tokenize denser than prose; prose=" + proseTokens
            + " json=" + jsonTokens);
    }

    @Test
    @DisplayName("estimateMessages counts system prompt + messages + tools")
    void estimateMessagesCountsAllParts() {
        String systemPrompt = "You are a helpful assistant.";
        List<LlmMessage> msgs = List.of(
            LlmMessage.user("hello"),
            LlmMessage.assistant("hi there"));

        Estimate e = TokenEstimator.estimateMessages(systemPrompt, msgs, List.of());
        assertNotNull(e);
        assertTrue(e.tokens() > 0);
        assertEquals(Confidence.MEDIUM, e.confidence());
    }

    @Test
    @DisplayName("estimateMessages handles null systemPrompt and empty tools")
    void estimateMessagesHandlesNullSystem() {
        Estimate e = TokenEstimator.estimateMessages(null,
            List.of(LlmMessage.user("hi")), List.of());
        assertTrue(e.tokens() > 0);
    }

    @Test
    @DisplayName("withSafetyMargin applies the right multiplier per confidence")
    void withSafetyMarginApplyMultiplier() {
        Estimate low = new Estimate(1000, Confidence.LOW);
        Estimate medium = new Estimate(1000, Confidence.MEDIUM);
        Estimate high = new Estimate(1000, Confidence.HIGH);
        assertEquals((int) Math.ceil(1000 * 1.30), low.withSafetyMargin());
        assertEquals((int) Math.ceil(1000 * 1.15), medium.withSafetyMargin());
        assertEquals((int) Math.ceil(1000 * 1.05), high.withSafetyMargin());
    }

    @Test
    @DisplayName("fitsWithin returns false when estimate exceeds the headroom-adjusted cap")
    void fitsWithinRespectsHeadroom() {
        Estimate e = new Estimate(7000, Confidence.MEDIUM);
        // Effective cap = 8000 * 0.85 = 6800. Safety-margined = 7000 * 1.15 = 8050.
        assertFalse(TokenEstimator.fitsWithin(e, 8000, 0.85));

        Estimate small = new Estimate(3000, Confidence.MEDIUM);
        assertTrue(TokenEstimator.fitsWithin(small, 8000, 0.85));
    }

    @Test
    @DisplayName("fitsWithin treats providerCap <= 0 as 'no cap'")
    void fitsWithinNoCap() {
        Estimate huge = new Estimate(100000, Confidence.LOW);
        assertTrue(TokenEstimator.fitsWithin(huge, 0, 0.85));
        assertTrue(TokenEstimator.fitsWithin(huge, -1, 0.85));
    }

    @Test
    @DisplayName("estimateMessages includes tool call name + arguments")
    void estimateMessagesIncludesToolCalls() {
        LlmToolCall tc = new LlmToolCall("c1", "list_elements",
            "{\"layer\":\"pa\",\"element_type\":\"PhysicalFunction\"}");
        LlmMessage assistantMsg = LlmMessage.assistantWithToolCalls(List.of(tc));
        List<LlmMessage> noCalls = List.of(LlmMessage.user("hi"));
        List<LlmMessage> withCalls = List.of(LlmMessage.user("hi"), assistantMsg);

        int withoutTc = TokenEstimator.estimateMessages(null, noCalls, List.of()).tokens();
        int withTc = TokenEstimator.estimateMessages(null, withCalls, List.of()).tokens();
        assertTrue(withTc > withoutTc, "tool call should add tokens");
    }
}

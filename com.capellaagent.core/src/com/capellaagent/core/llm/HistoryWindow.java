package com.capellaagent.core.llm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Selects a token-budget-bounded subset of conversation history to send to
 * an LLM, while preserving the assistant tool-call → tool-result invariant.
 * <p>
 * Replaces the old fixed-message-count sliding window. The previous approach
 * ({@code MAX_HISTORY_MESSAGES = 8}) had two correctness bugs identified by
 * the AI Engineer review:
 * <ol>
 *   <li>Eight messages with verbose tool results blew past Groq's 8 K limit</li>
 *   <li>Dropping a {@code tool_result} whose matching assistant tool-call was
 *       also dropped left an orphaned {@code tool_use_id} → 400 error from
 *       every OpenAI-compatible provider</li>
 * </ol>
 * <p>
 * This implementation:
 * <ul>
 *   <li>Walks history newest to oldest, accumulating estimated tokens</li>
 *   <li>Stops when adding the next message would exceed the budget</li>
 *   <li>Always keeps the system prompt(s) at the front (system messages do
 *       not count against the budget)</li>
 *   <li>Never splits an assistant-with-tool-calls message from its
 *       corresponding {@code TOOL} role messages — if either side would be
 *       dropped, both are dropped together</li>
 * </ul>
 */
public final class HistoryWindow {

    private final int budgetTokens;

    /**
     * @param budgetTokens absolute token budget for the windowed result
     *                     (system prompt overhead is NOT included in this number)
     */
    public HistoryWindow(int budgetTokens) {
        if (budgetTokens < 100) {
            throw new IllegalArgumentException("budgetTokens must be at least 100, got " + budgetTokens);
        }
        this.budgetTokens = budgetTokens;
    }

    public int budgetTokens() {
        return budgetTokens;
    }

    /**
     * Returns a token-budget-bounded prefix of the messages, preserving
     * tool_call ↔ tool_result pairs and system messages.
     *
     * @param all all conversation messages, oldest first
     * @return the windowed list (oldest first), safe to send to a provider
     */
    public List<LlmMessage> select(List<LlmMessage> all) {
        if (all == null || all.isEmpty()) return Collections.emptyList();

        // 1. Separate system messages — they always go to the front and don't
        //    count against the budget (system prompt is small and critical).
        List<LlmMessage> systemMsgs = new ArrayList<>();
        List<LlmMessage> nonSystem = new ArrayList<>();
        for (LlmMessage m : all) {
            if (m.getRole() == LlmMessage.Role.SYSTEM) {
                systemMsgs.add(m);
            } else {
                nonSystem.add(m);
            }
        }

        if (nonSystem.isEmpty()) {
            return new ArrayList<>(systemMsgs);
        }

        // 2. Walk newest → oldest, accumulating tokens. When we encounter a
        //    TOOL message, we pull in its parent assistant-tool-call message
        //    atomically (we never split the pair).
        List<LlmMessage> kept = new ArrayList<>();
        int tokensUsed = 0;

        for (int i = nonSystem.size() - 1; i >= 0; i--) {
            LlmMessage m = nonSystem.get(i);
            int cost = TokenEstimator.estimateText(m.getContent()) + 4;
            if (m.hasToolCalls()) {
                for (LlmToolCall tc : m.getToolCalls()) {
                    cost += TokenEstimator.estimateText(tc.getName());
                    cost += TokenEstimator.estimateText(tc.getArguments());
                    cost += 8;
                }
            }

            if (tokensUsed + cost > budgetTokens && !kept.isEmpty()) {
                // No room for this message. Stop here.
                break;
            }

            kept.add(0, m); // prepend so result stays oldest-first
            tokensUsed += cost;
        }

        // 3. Tool-result orphan prevention pass.
        //    Walk the kept list and drop any TOOL message whose preceding
        //    assistant-tool-calls turn is missing (because it was outside the
        //    window). The opposite — assistant tool_calls without their
        //    results — is also illegal, so drop it the same way.
        kept = removeOrphans(kept);

        // 4. Splice system messages back in at the front.
        List<LlmMessage> result = new ArrayList<>(systemMsgs.size() + kept.size());
        result.addAll(systemMsgs);
        result.addAll(kept);
        return result;
    }

    /**
     * Removes orphaned tool_result and tool_call messages from a windowed list.
     * <p>
     * A {@code TOOL} message is orphaned if no preceding {@code ASSISTANT}
     * message in the same window carries a {@link LlmToolCall} with the same
     * id. Conversely, an {@code ASSISTANT} message with tool calls is orphaned
     * if at least one of its tool_call ids has no matching {@code TOOL} reply
     * in the rest of the window.
     * <p>
     * Iterates the removal until stable: dropping an assistant tool-call may
     * orphan its existing tool_results, which then need to be dropped too.
     * <p>
     * Public for testing — the orphan-prevention invariant is the central
     * correctness contract of this class and is asserted by HistoryWindowTest.
     */
    public static List<LlmMessage> removeOrphans(List<LlmMessage> msgs) {
        List<LlmMessage> current = new ArrayList<>(msgs);
        while (true) {
            List<LlmMessage> next = removeOrphansOnePass(current);
            if (next.size() == current.size()) {
                return next;
            }
            current = next;
        }
    }

    private static List<LlmMessage> removeOrphansOnePass(List<LlmMessage> msgs) {
        // Build the set of tool_call ids that have a matching TOOL reply
        java.util.Set<String> repliedIds = new java.util.HashSet<>();
        for (LlmMessage m : msgs) {
            if (m.getRole() == LlmMessage.Role.TOOL && m.getToolCallId().isPresent()) {
                repliedIds.add(m.getToolCallId().get());
            }
        }
        // Build the set of tool_call ids that an assistant message issued
        java.util.Set<String> issuedIds = new java.util.HashSet<>();
        for (LlmMessage m : msgs) {
            if (m.getRole() == LlmMessage.Role.ASSISTANT && m.hasToolCalls()) {
                for (LlmToolCall tc : m.getToolCalls()) {
                    issuedIds.add(tc.getId());
                }
            }
        }

        // Drop:
        //  - TOOL messages whose id is not in issuedIds (no matching call)
        //  - ASSISTANT-with-tool-calls messages where ANY of its calls is not
        //    in repliedIds (incomplete pair)
        List<LlmMessage> result = new ArrayList<>(msgs.size());
        for (LlmMessage m : msgs) {
            if (m.getRole() == LlmMessage.Role.TOOL) {
                String id = m.getToolCallId().orElse(null);
                if (id == null || !issuedIds.contains(id)) {
                    continue; // orphaned tool result
                }
                result.add(m);
            } else if (m.getRole() == LlmMessage.Role.ASSISTANT && m.hasToolCalls()) {
                boolean allReplied = true;
                for (LlmToolCall tc : m.getToolCalls()) {
                    if (!repliedIds.contains(tc.getId())) {
                        allReplied = false;
                        break;
                    }
                }
                if (!allReplied) {
                    continue; // orphaned assistant tool-call
                }
                result.add(m);
            } else {
                result.add(m);
            }
        }
        return result;
    }
}

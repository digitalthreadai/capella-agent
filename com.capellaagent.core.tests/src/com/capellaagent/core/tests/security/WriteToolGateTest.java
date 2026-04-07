package com.capellaagent.core.tests.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.capellaagent.core.security.WriteToolGate;
import com.capellaagent.core.security.WriteToolGate.Decision;

/**
 * Unit tests for {@link WriteToolGate}.
 */
class WriteToolGateTest {

    @Test
    @DisplayName("read-only mode blocks all write tools")
    void readOnlyBlocksAllWrites() {
        WriteToolGate gate = WriteToolGate.readOnly();
        assertEquals(Decision.BLOCKED_READ_ONLY, gate.decide("create_element", false));
        assertEquals(Decision.BLOCKED_READ_ONLY, gate.decide("delete_element", false));
    }

    @Test
    @DisplayName("read-only mode still allows pure read tools")
    void readOnlyAllowsReads() {
        WriteToolGate gate = WriteToolGate.readOnly();
        assertEquals(Decision.ALLOW, gate.decide("list_elements", false));
        assertEquals(Decision.ALLOW, gate.decide("get_traceability", false));
    }

    @Test
    @DisplayName("read-write mode allows benign writes")
    void readWriteAllowsBenignWrites() {
        WriteToolGate gate = WriteToolGate.readWrite(Set.of("create_element", "update_element"));
        assertEquals(Decision.ALLOW, gate.decide("create_element", false));
        assertEquals(Decision.ALLOW, gate.decide("update_element", false));
    }

    @Test
    @DisplayName("destructive tools always require consent, even without suspicious context")
    void destructiveAlwaysRequiresConsent() {
        WriteToolGate gate = WriteToolGate.readWrite(Set.of("delete_element"));
        assertEquals(Decision.REQUIRES_CONSENT, gate.decide("delete_element", false));
        assertEquals(Decision.REQUIRES_CONSENT, gate.decide("batch_rename", false));
        assertEquals(Decision.REQUIRES_CONSENT, gate.decide("merge_elements", false));
    }

    @Test
    @DisplayName("write tool with suspicious context requires consent")
    void writeWithSuspiciousContextRequiresConsent() {
        WriteToolGate gate = WriteToolGate.readWrite(Set.of("create_element"));
        assertEquals(Decision.REQUIRES_CONSENT, gate.decide("create_element", true));
    }

    @Test
    @DisplayName("admin blocked tools are always blocked")
    void adminBlockedToolsBlocked() {
        WriteToolGate gate = new WriteToolGate(false, Set.of("create_element"),
            WriteToolGate.DEFAULT_DESTRUCTIVE_TOOLS, Set.of("create_element"));
        assertEquals(Decision.BLOCKED_ADMIN, gate.decide("create_element", false));
    }

    @Test
    @DisplayName("null tool name is blocked")
    void nullToolBlocked() {
        WriteToolGate gate = WriteToolGate.readOnly();
        assertEquals(Decision.BLOCKED_ADMIN, gate.decide(null, false));
    }

    @Test
    @DisplayName("DEFAULT_DESTRUCTIVE_TOOLS includes all known destructive operations")
    void defaultDestructiveSetIsComplete() {
        Set<String> destructive = WriteToolGate.DEFAULT_DESTRUCTIVE_TOOLS;
        assertTrue(destructive.contains("delete_element"));
        assertTrue(destructive.contains("merge_elements"));
        assertTrue(destructive.contains("batch_rename"));
        assertTrue(destructive.contains("transition_oa_to_sa"));
    }

    @Test
    @DisplayName("consentMessageFor mentions the tool name")
    void consentMessageMentionsTool() {
        String msg = WriteToolGate.consentMessageFor("delete_element", false);
        assertTrue(msg.contains("delete_element"));
        assertTrue(msg.toLowerCase().contains("destructive")
            || msg.toLowerCase().contains("undo"),
            "destructive consent message should warn about reversibility");
    }

    @Test
    @DisplayName("consentMessageFor flags suspicious context distinctly")
    void consentMessageFlagsSuspicious() {
        String msg = WriteToolGate.consentMessageFor("create_element", true);
        assertTrue(msg.toLowerCase().contains("injection")
            || msg.toLowerCase().contains("prompt"));
    }
}

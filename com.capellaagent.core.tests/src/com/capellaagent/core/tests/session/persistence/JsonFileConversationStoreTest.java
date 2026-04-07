package com.capellaagent.core.tests.session.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.capellaagent.core.llm.LlmMessage;
import com.capellaagent.core.llm.LlmToolCall;
import com.capellaagent.core.session.ConversationSession;
import com.capellaagent.core.session.persistence.ConversationStore;
import com.capellaagent.core.session.persistence.ConversationStore.LoadedSession;
import com.capellaagent.core.session.persistence.JsonFileConversationStore;
import com.capellaagent.core.session.persistence.SessionState;

/**
 * Unit tests for {@link JsonFileConversationStore}.
 */
class JsonFileConversationStoreTest {

    private Path tmpDir;

    private static final String PROJECT = "DemoProject";

    @BeforeEach
    void setUp() throws IOException {
        tmpDir = Files.createTempDirectory("capella-agent-store-test-");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tmpDir != null && Files.exists(tmpDir)) {
            // Recursive delete.
            try (java.util.stream.Stream<Path> walk = Files.walk(tmpDir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // best-effort cleanup
                    }
                });
            }
        }
    }

    private JsonFileConversationStore newStore() {
        return new JsonFileConversationStore(tmpDir);
    }

    private static ConversationSession newSessionWithAllRoles() {
        ConversationSession s = new ConversationSession();
        s.addSystemMessage("you are a helpful agent");
        s.addUserMessage("hello there");
        s.addAssistantMessage("hi! how can I help?");
        List<LlmToolCall> calls = new ArrayList<>();
        calls.add(new LlmToolCall("c1", "list_elements", "{\"kind\":\"Function\"}"));
        s.addAssistantToolCalls(calls);
        com.google.gson.JsonObject result = new com.google.gson.JsonObject();
        result.addProperty("count", 42);
        s.addToolResult("c1", result);
        return s;
    }

    @Test
    @DisplayName("save+load round-trip preserves all message roles")
    void roundTripPreservesAllRoles() throws IOException {
        ConversationStore store = newStore();
        ConversationSession original = newSessionWithAllRoles();

        store.save(original, SessionState.CLEAN, PROJECT);
        Optional<LoadedSession> loadedOpt = store.loadLatestForProject(PROJECT);

        assertTrue(loadedOpt.isPresent(), "expected a loaded session");
        ConversationSession loaded = loadedOpt.get().session();

        assertEquals(original.getSessionId(), loaded.getSessionId());
        assertEquals(original.getSchemaVersion(), loaded.getSchemaVersion());
        assertEquals(original.getMessages().size(), loaded.getMessages().size());

        // Check each role roundtripped
        List<LlmMessage> msgs = loaded.getMessages();
        assertEquals(LlmMessage.Role.SYSTEM, msgs.get(0).getRole());
        assertEquals("you are a helpful agent", msgs.get(0).getContent());
        assertEquals(LlmMessage.Role.USER, msgs.get(1).getRole());
        assertEquals("hello there", msgs.get(1).getContent());
        assertEquals(LlmMessage.Role.ASSISTANT, msgs.get(2).getRole());
        assertEquals("hi! how can I help?", msgs.get(2).getContent());
        assertEquals(LlmMessage.Role.ASSISTANT, msgs.get(3).getRole());
        assertTrue(msgs.get(3).hasToolCalls());
        assertEquals(LlmMessage.Role.TOOL, msgs.get(4).getRole());
    }

    @Test
    @DisplayName("save+load preserves the SessionState")
    void preservesSessionState() throws IOException {
        ConversationStore store = newStore();
        ConversationSession s = newSessionWithAllRoles();

        store.save(s, SessionState.IN_FLIGHT_WITH_WRITES, PROJECT);
        Optional<LoadedSession> loaded = store.loadLatestForProject(PROJECT);

        assertTrue(loaded.isPresent());
        assertEquals(SessionState.IN_FLIGHT_WITH_WRITES, loaded.get().state());
        assertNotNull(loaded.get().savedAt());
    }

    @Test
    @DisplayName("atomic write: no .tmp file remains after a successful save")
    void atomicWriteLeavesNoTmp() throws IOException {
        ConversationStore store = newStore();
        store.save(newSessionWithAllRoles(), SessionState.CLEAN, PROJECT);

        Path projectDir = tmpDir.resolve(PROJECT);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(projectDir, "*.tmp")) {
            assertFalse(stream.iterator().hasNext(), "expected no .tmp files left behind");
        }
    }

    @Test
    @DisplayName("loadLatestForProject returns the most recently saved session")
    void loadLatestReturnsNewest() throws IOException {
        ConversationStore store = newStore();
        ConversationSession older = new ConversationSession();
        older.addUserMessage("older");
        store.save(older, SessionState.CLEAN, PROJECT);

        // Force the older file's mtime to be in the past so the comparison
        // is unambiguous on filesystems with low-res timestamps.
        Path olderFile = tmpDir.resolve(PROJECT).resolve(older.getSessionId() + ".json");
        Files.setLastModifiedTime(olderFile,
                java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - 60_000L));

        ConversationSession newer = new ConversationSession();
        newer.addUserMessage("newer");
        store.save(newer, SessionState.CLEAN, PROJECT);

        Optional<LoadedSession> loaded = store.loadLatestForProject(PROJECT);
        assertTrue(loaded.isPresent());
        assertEquals(newer.getSessionId(), loaded.get().session().getSessionId());
    }

    @Test
    @DisplayName("loadLatestForProject returns Optional.empty() for nonexistent project")
    void loadLatestEmptyForUnknownProject() throws IOException {
        ConversationStore store = newStore();
        Optional<LoadedSession> loaded = store.loadLatestForProject("NoSuchProject");
        assertFalse(loaded.isPresent());
    }

    @Test
    @DisplayName("listSessionIds returns all saved session IDs for a project")
    void listSessionIdsReturnsAll() throws IOException {
        ConversationStore store = newStore();
        ConversationSession a = new ConversationSession();
        a.addUserMessage("a");
        ConversationSession b = new ConversationSession();
        b.addUserMessage("b");
        ConversationSession c = new ConversationSession();
        c.addUserMessage("c");
        store.save(a, SessionState.CLEAN, PROJECT);
        store.save(b, SessionState.CLEAN, PROJECT);
        store.save(c, SessionState.CLEAN, PROJECT);

        List<String> ids = store.listSessionIds(PROJECT);
        assertEquals(3, ids.size());
        assertTrue(ids.contains(a.getSessionId()));
        assertTrue(ids.contains(b.getSessionId()));
        assertTrue(ids.contains(c.getSessionId()));
    }

    @Test
    @DisplayName("projectName sanitization: '..' cannot escape the base dir")
    void sanitizationPreventsTraversal() throws IOException {
        ConversationStore store = newStore();
        ConversationSession s = new ConversationSession();
        s.addUserMessage("attempt");

        // Try the classic traversal pattern; must stay under tmpDir.
        store.save(s, SessionState.CLEAN, "../evil");

        // The sanitized folder should exist under tmpDir, NOT escape it.
        String sanitized = JsonFileConversationStore.sanitize("../evil");
        Path resolved = tmpDir.resolve(sanitized).normalize();
        assertTrue(resolved.startsWith(tmpDir.normalize()),
                "sanitized path must stay under baseDir, was " + resolved);
        assertTrue(Files.isDirectory(resolved),
                "expected sanitized project dir under baseDir");

        // And the saved file should be found by listSessionIds under the
        // sanitized project name (proves the save landed in the right place).
        List<String> ids = store.listSessionIds("../evil");
        assertEquals(1, ids.size());
        assertEquals(s.getSessionId(), ids.get(0));
    }

    @Test
    @DisplayName("two projects' sessions are isolated")
    void projectsAreIsolated() throws IOException {
        ConversationStore store = newStore();
        ConversationSession a = new ConversationSession();
        a.addUserMessage("alpha");
        ConversationSession b = new ConversationSession();
        b.addUserMessage("beta");
        store.save(a, SessionState.CLEAN, "ProjectA");
        store.save(b, SessionState.CLEAN, "ProjectB");

        List<String> projAIds = store.listSessionIds("ProjectA");
        List<String> projBIds = store.listSessionIds("ProjectB");

        assertEquals(1, projAIds.size());
        assertEquals(1, projBIds.size());
        assertTrue(projAIds.contains(a.getSessionId()));
        assertTrue(projBIds.contains(b.getSessionId()));
        assertFalse(projAIds.contains(b.getSessionId()));
        assertFalse(projBIds.contains(a.getSessionId()));
    }

    @Test
    @DisplayName("schemaVersion mismatch on load returns Optional.empty() and does not throw")
    void schemaMismatchReturnsEmpty() throws IOException {
        // Hand-craft a JSON file with a future schemaVersion.
        Path projectDir = tmpDir.resolve(PROJECT);
        Files.createDirectories(projectDir);
        String fakeId = "00000000-0000-0000-0000-000000000001";
        String json = "{\n"
                + "  \"schemaVersion\": 99,\n"
                + "  \"sessionId\": \"" + fakeId + "\",\n"
                + "  \"state\": \"CLEAN\",\n"
                + "  \"savedAt\": \"2026-04-07T13:45:00Z\",\n"
                + "  \"messages\": []\n"
                + "}";
        Files.writeString(projectDir.resolve(fakeId + ".json"), json, StandardCharsets.UTF_8);

        ConversationStore store = newStore();
        Optional<LoadedSession> loaded = store.loadLatestForProject(PROJECT);
        assertFalse(loaded.isPresent(), "future schema version must not crash, must return empty");
    }

    @Test
    @DisplayName("save preserves toolCallId for TOOL messages")
    void preservesToolCallIdForToolMessage() throws IOException {
        ConversationStore store = newStore();
        ConversationSession s = new ConversationSession();
        s.addUserMessage("query");
        com.google.gson.JsonObject result = new com.google.gson.JsonObject();
        result.addProperty("ok", true);
        s.addToolResult("call-xyz-123", result);
        store.save(s, SessionState.CLEAN, PROJECT);

        ConversationSession loaded = store.loadLatestForProject(PROJECT).get().session();
        LlmMessage toolMsg = loaded.getMessages().get(loaded.getMessages().size() - 1);
        assertEquals(LlmMessage.Role.TOOL, toolMsg.getRole());
        assertTrue(toolMsg.getToolCallId().isPresent());
        assertEquals("call-xyz-123", toolMsg.getToolCallId().get());
        assertTrue(toolMsg.getContent().contains("\"ok\""));
    }

    @Test
    @DisplayName("save preserves tool_calls list on assistant messages")
    void preservesAssistantToolCalls() throws IOException {
        ConversationStore store = newStore();
        ConversationSession s = new ConversationSession();
        s.addUserMessage("do stuff");
        List<LlmToolCall> calls = new ArrayList<>();
        calls.add(new LlmToolCall("c1", "list_elements", "{\"kind\":\"Function\"}"));
        calls.add(new LlmToolCall("c2", "describe_element", "{\"id\":\"abc\"}"));
        s.addAssistantToolCalls(calls);
        store.save(s, SessionState.CLEAN, PROJECT);

        ConversationSession loaded = store.loadLatestForProject(PROJECT).get().session();
        LlmMessage asstMsg = loaded.getMessages().get(loaded.getMessages().size() - 1);
        assertEquals(LlmMessage.Role.ASSISTANT, asstMsg.getRole());
        assertTrue(asstMsg.hasToolCalls());
        assertEquals(2, asstMsg.getToolCalls().size());
        LlmToolCall c1 = asstMsg.getToolCalls().get(0);
        assertEquals("c1", c1.getId());
        assertEquals("list_elements", c1.getName());
        assertEquals("{\"kind\":\"Function\"}", c1.getArguments());
        LlmToolCall c2 = asstMsg.getToolCalls().get(1);
        assertEquals("c2", c2.getId());
        assertEquals("describe_element", c2.getName());
        assertEquals("{\"id\":\"abc\"}", c2.getArguments());
    }

    @Test
    @DisplayName("listSessionIds is empty for nonexistent project")
    void listSessionIdsEmptyForUnknownProject() throws IOException {
        ConversationStore store = newStore();
        assertTrue(store.listSessionIds("DoesNotExist").isEmpty());
    }

    @Test
    @DisplayName("save creates the project directory under baseDir")
    void saveCreatesProjectDir() throws IOException {
        ConversationStore store = newStore();
        ConversationSession s = new ConversationSession();
        s.addUserMessage("hi");
        store.save(s, SessionState.CLEAN, PROJECT);

        Path projectDir = tmpDir.resolve(PROJECT);
        assertTrue(Files.isDirectory(projectDir));
        assertTrue(Files.isRegularFile(projectDir.resolve(s.getSessionId() + ".json")));
    }
}

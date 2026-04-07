package com.capellaagent.core.session.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.capellaagent.core.llm.LlmMessage;
import com.capellaagent.core.llm.LlmToolCall;
import com.capellaagent.core.session.ConversationSession;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Filesystem-backed {@link ConversationStore} implementation.
 * <p>
 * Sessions are written as pretty-printed JSON files at
 * {@code {baseDir}/{projectName}/{sessionId}.json}. Writes are atomic — the
 * payload is first written to {@code {sessionId}.json.tmp} and then renamed
 * via {@link StandardCopyOption#ATOMIC_MOVE} so a crash mid-write can never
 * leave a half-written file in place of a previously valid session.
 * <p>
 * Project names are sanitized: any segment containing a path separator,
 * {@code ..}, {@code :}, or other unsafe characters is replaced with
 * {@code _} so that a malicious or buggy caller cannot escape the base
 * directory.
 */
public class JsonFileConversationStore implements ConversationStore {

    private static final Logger LOG = Logger.getLogger(JsonFileConversationStore.class.getName());

    private static final String FILE_SUFFIX = ".json";
    private static final String TMP_SUFFIX = ".json.tmp";

    private final Path baseDir;
    private final Gson gson;

    /**
     * Creates a new store rooted at the supplied base directory.
     *
     * @param baseDir the directory under which {@code {projectName}/} folders
     *                will be created; must not be null
     */
    public JsonFileConversationStore(Path baseDir) {
        this.baseDir = Objects.requireNonNull(baseDir, "baseDir must not be null");
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    /**
     * Returns the base directory this store is writing into. Public for
     * testing.
     *
     * @return the base directory path
     */
    public Path getBaseDir() {
        return baseDir;
    }

    @Override
    public void save(ConversationSession session, SessionState state, String projectName) throws IOException {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(projectName, "projectName must not be null");

        Path projectDir = projectDir(projectName);
        Files.createDirectories(projectDir);

        Path target = projectDir.resolve(session.getSessionId() + FILE_SUFFIX);
        Path tmp = projectDir.resolve(session.getSessionId() + TMP_SUFFIX);

        JsonObject root = serialize(session, state, Instant.now());
        String payload = gson.toJson(root);

        Files.writeString(tmp, payload, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicFailed) {
            // Some filesystems (e.g. cross-device) don't support ATOMIC_MOVE.
            // Fall back to a non-atomic replace and log so we know.
            LOG.log(Level.FINE, "ATOMIC_MOVE not supported, falling back to REPLACE_EXISTING", atomicFailed);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            // Defensive: if anything left the tmp file behind, clean it up.
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    public Optional<LoadedSession> loadLatestForProject(String projectName) throws IOException {
        Objects.requireNonNull(projectName, "projectName must not be null");
        Path projectDir = projectDir(projectName);
        if (!Files.isDirectory(projectDir)) {
            return Optional.empty();
        }

        List<Path> candidates = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(projectDir, "*" + FILE_SUFFIX)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(FILE_SUFFIX)
                        && !p.getFileName().toString().endsWith(TMP_SUFFIX)) {
                    candidates.add(p);
                }
            }
        }

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        candidates.sort(Comparator.comparing((Path p) -> {
            try {
                return Files.getLastModifiedTime(p);
            } catch (IOException e) {
                return FileTime.fromMillis(0L);
            }
        }).reversed());

        Path newest = candidates.get(0);
        return loadFile(newest);
    }

    @Override
    public List<String> listSessionIds(String projectName) throws IOException {
        Objects.requireNonNull(projectName, "projectName must not be null");
        Path projectDir = projectDir(projectName);
        List<String> ids = new ArrayList<>();
        if (!Files.isDirectory(projectDir)) {
            return ids;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(projectDir, "*" + FILE_SUFFIX)) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if (name.endsWith(FILE_SUFFIX) && !name.endsWith(TMP_SUFFIX)) {
                    ids.add(name.substring(0, name.length() - FILE_SUFFIX.length()));
                }
            }
        }
        return ids;
    }

    // -- internals --

    private Optional<LoadedSession> loadFile(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        JsonObject root;
        try {
            root = JsonParser.parseString(text).getAsJsonObject();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Failed to parse session file " + file + "; ignoring", e);
            return Optional.empty();
        }

        int schemaVersion = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : -1;
        if (schemaVersion != ConversationSession.SCHEMA_VERSION) {
            LOG.warning("Session file " + file + " has schemaVersion=" + schemaVersion
                    + " but current is " + ConversationSession.SCHEMA_VERSION + "; skipping (migration deferred)");
            return Optional.empty();
        }

        String sessionId = root.get("sessionId").getAsString();
        SessionState state = SessionState.valueOf(root.get("state").getAsString());
        Instant savedAt = Instant.parse(root.get("savedAt").getAsString());

        List<LlmMessage> messages = new ArrayList<>();
        JsonArray msgs = root.getAsJsonArray("messages");
        if (msgs != null) {
            for (JsonElement el : msgs) {
                messages.add(deserializeMessage(el.getAsJsonObject()));
            }
        }

        ConversationSession session = ConversationSession.restore(sessionId, schemaVersion, messages);
        return Optional.of(new LoadedSession(session, state, savedAt));
    }

    private JsonObject serialize(ConversationSession session, SessionState state, Instant savedAt) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", session.getSchemaVersion());
        root.addProperty("sessionId", session.getSessionId());
        root.addProperty("state", state.name());
        root.addProperty("savedAt", savedAt.toString());

        JsonArray msgs = new JsonArray();
        for (LlmMessage m : session.getMessages()) {
            msgs.add(serializeMessage(m));
        }
        root.add("messages", msgs);
        return root;
    }

    private JsonObject serializeMessage(LlmMessage m) {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", m.getRole().name());
        obj.addProperty("content", m.getContent());
        m.getToolCallId().ifPresent(id -> obj.addProperty("toolCallId", id));
        m.getName().ifPresent(n -> obj.addProperty("name", n));
        if (m.hasToolCalls()) {
            JsonArray arr = new JsonArray();
            for (LlmToolCall tc : m.getToolCalls()) {
                JsonObject tcObj = new JsonObject();
                tcObj.addProperty("id", tc.getId());
                tcObj.addProperty("name", tc.getName());
                tcObj.addProperty("arguments", tc.getArguments());
                arr.add(tcObj);
            }
            obj.add("toolCalls", arr);
        }
        return obj;
    }

    private LlmMessage deserializeMessage(JsonObject obj) {
        LlmMessage.Role role = LlmMessage.Role.valueOf(obj.get("role").getAsString());
        String content = obj.has("content") ? obj.get("content").getAsString() : "";
        String toolCallId = obj.has("toolCallId") ? obj.get("toolCallId").getAsString() : null;

        if (obj.has("toolCalls")) {
            JsonArray arr = obj.getAsJsonArray("toolCalls");
            List<LlmToolCall> calls = new ArrayList<>(arr.size());
            for (JsonElement el : arr) {
                JsonObject tc = el.getAsJsonObject();
                calls.add(new LlmToolCall(
                        tc.get("id").getAsString(),
                        tc.get("name").getAsString(),
                        tc.get("arguments").getAsString()));
            }
            // Only the assistantWithToolCalls factory takes a list; it
            // forces ASSISTANT role + empty content, which matches how we
            // serialized it on the way out.
            return LlmMessage.assistantWithToolCalls(calls);
        }

        switch (role) {
            case SYSTEM:
                return LlmMessage.system(content);
            case USER:
                return LlmMessage.user(content);
            case ASSISTANT:
                return LlmMessage.assistant(content);
            case TOOL:
                return LlmMessage.toolResult(toolCallId, content);
            default:
                throw new IllegalStateException("Unknown role " + role);
        }
    }

    private Path projectDir(String projectName) {
        return baseDir.resolve(sanitize(projectName));
    }

    /**
     * Sanitizes a project-name segment so it cannot escape {@link #baseDir}.
     * Replaces path separators, drive letters, and {@code ..} traversal with
     * underscores. Public for testing.
     *
     * @param projectName the raw project name
     * @return a single safe path segment
     */
    public static String sanitize(String projectName) {
        if (projectName == null || projectName.isEmpty()) {
            return "_";
        }
        String trimmed = projectName.trim();
        if (trimmed.isEmpty() || trimmed.equals(".") || trimmed.equals("..")) {
            return "_";
        }
        StringBuilder out = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '/' || c == '\\' || c == ':' || c == '\0' || c == '*'
                    || c == '?' || c == '"' || c == '<' || c == '>' || c == '|') {
                out.append('_');
            } else {
                out.append(c);
            }
        }
        String result = out.toString();
        // Belt-and-braces: collapse any ".." that survived (it can't, after
        // the separator scrub above, but be paranoid).
        result = result.replace("..", "_");
        return result;
    }
}

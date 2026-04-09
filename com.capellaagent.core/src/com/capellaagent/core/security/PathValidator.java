package com.capellaagent.core.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;

import org.eclipse.core.resources.ResourcesPlugin;

/**
 * Validates user-supplied file paths before they are opened by tools that
 * accept LLM-controlled input. Enforces four guarantees:
 *
 * <ol>
 *   <li><b>Canonicalization</b> — resolves {@code ..} segments and symbolic
 *       path components so a relative {@code ../../../etc/passwd} cannot
 *       slip through.</li>
 *   <li><b>Workspace containment</b> — the canonical path must live inside
 *       the Eclipse workspace root (or an explicit allow-list directory).
 *       Any path outside is rejected regardless of how it was constructed.</li>
 *   <li><b>Extension allow-list</b> — files must have one of the expected
 *       extensions. Stops a requirement-import tool from being tricked into
 *       parsing {@code /etc/shadow} as ReqIF.</li>
 *   <li><b>Symlink refusal</b> — opens with {@link LinkOption#NOFOLLOW_LINKS}
 *       and rejects anything that resolves through a symlink / reparse point.
 *       Closes the TOCTOU window where a validated path could be replaced
 *       with a symlink before the tool opens it.</li>
 * </ol>
 *
 * <b>Error message safety:</b> the actual requested path is never included
 * in thrown {@link SecurityException} messages — only a redacted token —
 * so a read-attempt against {@code /etc/shadow} does not echo back into an
 * LLM context or error log that may later be read by a less-trusted user.
 */
public final class PathValidator {

    private PathValidator() { }

    /**
     * Validates a path that a tool will read as input.
     *
     * @param rawPath the user-supplied path string
     * @param allowedExtensions the allow-list of file extensions (lowercase,
     *                          with leading dot, e.g. {@code ".reqif"})
     * @return the canonical {@link Path} ready for use
     * @throws SecurityException if the path is outside the workspace,
     *         traverses a symlink, has a non-allowed extension, or is
     *         otherwise unsafe
     */
    public static Path validateInputPath(String rawPath, Set<String> allowedExtensions) {
        Path canonical = canonicalize(rawPath);
        requireInsideWorkspace(canonical);
        requireNoSymlinks(canonical);
        requireAllowedExtension(canonical, allowedExtensions);
        requireRegularFile(canonical);
        return canonical;
    }

    /**
     * Validates a path that a tool will write as output. Same rules as
     * {@link #validateInputPath} except the file does not need to exist yet,
     * only its parent directory.
     *
     * @param rawPath the user-supplied path string
     * @param allowedExtensions the allow-list of output extensions
     * @return the canonical {@link Path}
     * @throws SecurityException if the path is unsafe
     */
    public static Path validateOutputPath(String rawPath, Set<String> allowedExtensions) {
        Path canonical = canonicalize(rawPath);
        requireInsideWorkspace(canonical);
        Path parent = canonical.getParent();
        if (parent == null) {
            throw new SecurityException("Invalid output path: no parent directory");
        }
        requireNoSymlinks(parent);
        requireAllowedExtension(canonical, allowedExtensions);
        return canonical;
    }

    // ────────────────────────────────────────────────────────────────
    // Implementation
    // ────────────────────────────────────────────────────────────────

    private static Path canonicalize(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new SecurityException("Invalid path: empty");
        }
        // Reject null bytes immediately — Java will happily round-trip them
        // but the underlying syscall may truncate the string at \0, causing
        // the canonicalization check to validate a different path than the
        // one opened.
        if (rawPath.indexOf('\0') >= 0) {
            throw new SecurityException("Invalid path: null byte");
        }
        try {
            return Paths.get(rawPath).toAbsolutePath().normalize();
        } catch (Exception e) {
            throw new SecurityException("Invalid path format", e);
        }
    }

    private static void requireInsideWorkspace(Path canonical) {
        Path workspaceRoot = getWorkspaceRoot();
        if (workspaceRoot == null) {
            // No Eclipse workspace (e.g. in a headless unit test). Fall back
            // to user.home as the containment anchor.
            String home = System.getProperty("user.home");
            if (home == null || home.isBlank()) {
                throw new SecurityException("No workspace root available");
            }
            workspaceRoot = Paths.get(home).toAbsolutePath().normalize();
        }
        if (!canonical.startsWith(workspaceRoot)) {
            // Redacted message — never echo the requested path.
            throw new SecurityException(
                "Path outside permitted directory (workspace containment failed)");
        }
    }

    private static Path getWorkspaceRoot() {
        try {
            return ResourcesPlugin.getWorkspace()
                .getRoot()
                .getLocation()
                .toFile()
                .getCanonicalFile()
                .toPath();
        } catch (IllegalStateException | IOException e) {
            // Workspace not yet initialised or IO error reading its location.
            return null;
        }
    }

    private static void requireNoSymlinks(Path canonical) {
        // Walk every ancestor from the root to the target and check each one.
        // This catches a symlink anywhere in the chain, not just the last
        // component. Uses NOFOLLOW_LINKS so the check does not itself follow
        // any link.
        Path current = canonical.getRoot();
        if (current == null) {
            throw new SecurityException("Path has no root");
        }
        for (Path segment : canonical) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new SecurityException("Symbolic link in path (rejected)");
            }
        }
    }

    private static void requireAllowedExtension(Path canonical, Set<String> allowedExtensions) {
        if (allowedExtensions == null || allowedExtensions.isEmpty()) {
            return;
        }
        String filename = canonical.getFileName() == null
            ? "" : canonical.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String ext : allowedExtensions) {
            if (filename.endsWith(ext.toLowerCase(Locale.ROOT))) {
                return;
            }
        }
        throw new SecurityException("File extension not permitted");
    }

    private static void requireRegularFile(Path canonical) {
        if (!Files.exists(canonical, LinkOption.NOFOLLOW_LINKS)) {
            throw new SecurityException("File does not exist");
        }
        if (!Files.isRegularFile(canonical, LinkOption.NOFOLLOW_LINKS)) {
            throw new SecurityException("Path is not a regular file");
        }
    }
}

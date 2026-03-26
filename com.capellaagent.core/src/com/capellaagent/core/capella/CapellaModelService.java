package com.capellaagent.core.capella;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.business.api.session.SessionManager;
import org.polarsys.capella.common.data.modellingcore.ModelElement;
import org.polarsys.capella.core.data.capellamodeller.Project;
import org.polarsys.capella.core.data.capellamodeller.SystemEngineering;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.ctx.SystemAnalysis;
import org.polarsys.capella.core.data.la.LogicalArchitecture;
import org.polarsys.capella.core.data.oa.OperationalAnalysis;
import org.polarsys.capella.core.data.pa.PhysicalArchitecture;

/**
 * Central service for all Capella model access operations.
 * <p>
 * Provides session management, element resolution with O(1) UUID lookup,
 * architecture navigation, and transactional execution. All tool implementations
 * should delegate model access through this service rather than accessing
 * Sirius/Capella APIs directly.
 * <p>
 * This is a thread-safe singleton. Obtain the instance via {@link #getInstance()}.
 */
public final class CapellaModelService {

    private static final Logger LOG = Logger.getLogger(CapellaModelService.class.getName());

    private static final CapellaModelService INSTANCE = new CapellaModelService();

    /**
     * Lazy UUID-to-EObject index, keyed by session identity hash.
     * Invalidated when the model changes.
     */
    private final ConcurrentHashMap<Integer, Map<String, EObject>> uuidCaches = new ConcurrentHashMap<>();

    private CapellaModelService() {
    }

    /**
     * Returns the singleton instance.
     */
    public static CapellaModelService getInstance() {
        return INSTANCE;
    }

    // ========================================================================
    // Session Management
    // ========================================================================

    /**
     * Finds a Sirius Session by project name.
     * <p>
     * If {@code projectName} is null, returns the first open session.
     * If multiple sessions are open and no project name is specified, throws
     * an exception listing available projects so the caller can disambiguate.
     *
     * @param projectName the Capella project name, or null for the default session
     * @return the matching open Session
     * @throws IllegalStateException if no open session is found or ambiguity exists
     */
    public Session getSession(String projectName) {
        Collection<Session> allSessions = SessionManager.INSTANCE.getSessions();

        // Filter to open sessions only
        java.util.List<Session> openSessions = allSessions.stream()
                .filter(Session::isOpen)
                .collect(java.util.stream.Collectors.toList());

        if (openSessions.isEmpty()) {
            throw new IllegalStateException(
                    "No open Capella sessions found. Please open a Capella project first.");
        }

        // If a specific project name is requested, find it
        if (projectName != null && !projectName.isBlank()) {
            for (Session session : openSessions) {
                String sessionProjectName = extractProjectName(session);
                if (projectName.equalsIgnoreCase(sessionProjectName)) {
                    return session;
                }
            }
            // Build available project list for error message
            StringBuilder available = new StringBuilder();
            for (Session session : openSessions) {
                if (available.length() > 0) available.append(", ");
                available.append(extractProjectName(session));
            }
            throw new IllegalStateException(
                    "No open session found for project '" + projectName
                    + "'. Available projects: " + available);
        }

        // No project name specified
        if (openSessions.size() == 1) {
            return openSessions.get(0);
        }

        // Multiple sessions open, require disambiguation
        StringBuilder available = new StringBuilder();
        for (Session session : openSessions) {
            if (available.length() > 0) available.append(", ");
            available.append(extractProjectName(session));
        }
        throw new IllegalStateException(
                "Multiple Capella projects are open. Please specify which project: "
                + available);
    }

    /**
     * Extracts the project name from a Sirius session by examining its
     * semantic resources for a Capella Project element.
     */
    private String extractProjectName(Session session) {
        try {
            for (Resource resource : session.getSemanticResources()) {
                for (EObject root : resource.getContents()) {
                    if (root instanceof Project) {
                        String name = ((Project) root).getName();
                        if (name != null && !name.isBlank()) {
                            return name;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not extract project name from session", e);
        }
        // Fallback: use the session resource URI
        return session.getSessionResource().getURI().lastSegment();
    }

    // ========================================================================
    // Element Resolution
    // ========================================================================

    /**
     * Resolves a model element by its Capella UUID with O(1) lookup.
     * <p>
     * First attempts {@link Resource#getEObject(String)} on each semantic resource.
     * If that fails, builds a lazy {@code Map<String, EObject>} index using
     * {@link ModelElement#getId()} and looks up from the cache.
     *
     * @param uuid    the element UUID (Capella ID)
     * @param session the Sirius session to search in
     * @return the resolved EObject, or null if not found
     */
    public EObject resolveElement(String uuid, Session session) {
        if (uuid == null || uuid.isBlank() || session == null) {
            return null;
        }

        // Strategy 1: Direct resource lookup via URI fragment
        for (Resource resource : session.getSemanticResources()) {
            try {
                EObject obj = resource.getEObject(uuid);
                if (obj != null) {
                    return obj;
                }
            } catch (Exception e) {
                // Some resources may not support fragment lookup; continue
                LOG.log(Level.FINE, "Fragment lookup failed on resource: " + resource.getURI(), e);
            }
        }

        // Strategy 2: Lazy UUID index using ModelElement.getId()
        int sessionKey = System.identityHashCode(session);
        Map<String, EObject> cache = uuidCaches.computeIfAbsent(sessionKey,
                k -> buildUuidIndex(session));

        EObject result = cache.get(uuid);
        if (result != null) {
            // Verify the element is still valid (not detached)
            if (result.eResource() != null) {
                return result;
            }
            // Element was detached; rebuild cache
            uuidCaches.remove(sessionKey);
            cache = uuidCaches.computeIfAbsent(sessionKey, k -> buildUuidIndex(session));
            return cache.get(uuid);
        }

        return null;
    }

    /**
     * Builds the full UUID index by traversing all semantic resources.
     */
    private Map<String, EObject> buildUuidIndex(Session session) {
        Map<String, EObject> index = new HashMap<>();
        long start = System.currentTimeMillis();

        for (Resource resource : session.getSemanticResources()) {
            try {
                var iterator = resource.getAllContents();
                while (iterator.hasNext()) {
                    EObject obj = iterator.next();
                    if (obj instanceof ModelElement) {
                        String id = ((ModelElement) obj).getId();
                        if (id != null && !id.isEmpty()) {
                            index.put(id, obj);
                        }
                    }
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Error indexing resource: " + resource.getURI(), e);
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        LOG.info("Built UUID index with " + index.size() + " elements in " + elapsed + "ms");
        return index;
    }

    // ========================================================================
    // Model Navigation
    // ========================================================================

    /**
     * Navigates from the session's semantic resources to the SystemEngineering element.
     *
     * @param session the Sirius session
     * @return the SystemEngineering element
     * @throws IllegalStateException if no SystemEngineering is found
     */
    public SystemEngineering getSystemEngineering(Session session) {
        for (Resource resource : session.getSemanticResources()) {
            for (EObject root : resource.getContents()) {
                if (root instanceof Project) {
                    Project project = (Project) root;
                    for (EObject child : project.getOwnedModelRoots()) {
                        if (child instanceof SystemEngineering) {
                            return (SystemEngineering) child;
                        }
                    }
                }
            }
        }
        throw new IllegalStateException(
                "No SystemEngineering element found in the active Capella session. "
                + "Ensure a valid Capella model is loaded.");
    }

    /**
     * Gets the BlockArchitecture for the specified ARCADIA layer.
     *
     * @param session the Sirius session
     * @param layer   the layer identifier: "oa", "sa", "la", or "pa"
     * @return the BlockArchitecture for the layer
     * @throws IllegalArgumentException if the layer is invalid or not found
     */
    public BlockArchitecture getArchitecture(Session session, String layer) {
        SystemEngineering se = getSystemEngineering(session);

        for (EObject child : se.getOwnedArchitectures()) {
            switch (layer.toLowerCase()) {
                case "oa":
                    if (child instanceof OperationalAnalysis) return (BlockArchitecture) child;
                    break;
                case "sa":
                    if (child instanceof SystemAnalysis) return (BlockArchitecture) child;
                    break;
                case "la":
                    if (child instanceof LogicalArchitecture) return (BlockArchitecture) child;
                    break;
                case "pa":
                    if (child instanceof PhysicalArchitecture) return (BlockArchitecture) child;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Invalid architecture layer: '" + layer
                            + "'. Must be one of: oa, sa, la, pa");
            }
        }

        throw new IllegalStateException(
                "Architecture layer '" + layer + "' not found in the SystemEngineering element. "
                + "The model may not contain this architecture level.");
    }

    /**
     * Detects which ARCADIA layer a given element belongs to by walking
     * up the containment hierarchy.
     *
     * @param element the model element
     * @return the layer identifier ("oa", "sa", "la", "pa") or "unknown"
     */
    public String detectLayer(EObject element) {
        EObject current = element;
        while (current != null) {
            if (current instanceof OperationalAnalysis) return "oa";
            if (current instanceof SystemAnalysis) return "sa";
            if (current instanceof LogicalArchitecture) return "la";
            if (current instanceof PhysicalArchitecture) return "pa";
            current = current.eContainer();
        }
        return "unknown";
    }

    // ========================================================================
    // Transaction Support
    // ========================================================================

    /**
     * Executes a runnable within an EMF RecordingCommand for proper
     * transactional handling and undo support.
     *
     * @param session the Sirius session
     * @param label   the command label (for undo history)
     * @param action  the action to execute within the transaction
     * @throws IllegalStateException if the session has no editing domain
     */
    public void executeInTransaction(Session session, String label, Runnable action) {
        TransactionalEditingDomain domain = session.getTransactionalEditingDomain();
        if (domain == null) {
            throw new IllegalStateException(
                    "No transactional editing domain available for the session.");
        }
        domain.getCommandStack().execute(new RecordingCommand(domain, label) {
            @Override
            protected void doExecute() {
                action.run();
            }
        });
    }

    // ========================================================================
    // Cache Management
    // ========================================================================

    /**
     * Invalidates the UUID cache for the given session. Should be called
     * after model modifications to ensure the cache reflects the current state.
     *
     * @param session the session whose cache should be invalidated
     */
    public void invalidateCache(Session session) {
        if (session != null) {
            int key = System.identityHashCode(session);
            uuidCaches.remove(key);
            LOG.fine("UUID cache invalidated for session: " + extractProjectName(session));
        }
    }

    /**
     * Invalidates all UUID caches. Use when the overall session state is uncertain.
     */
    public void invalidateAllCaches() {
        uuidCaches.clear();
        LOG.fine("All UUID caches invalidated");
    }
}

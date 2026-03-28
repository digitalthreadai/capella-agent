package com.capellaagent.core.util;

import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.business.api.session.SessionManager;

/**
 * Static utility methods for working with Capella/Sirius sessions.
 * <p>
 * Provides convenience methods for session lifecycle management, editing
 * domain access, and element lookup. Many methods contain PLACEHOLDER
 * implementations because the exact API calls depend on the Capella version.
 */
public final class CapellaSessionUtil {

    private static final Logger LOG = Logger.getLogger(CapellaSessionUtil.class.getName());

    private CapellaSessionUtil() {
        // Utility class
    }

    /**
     * Gets (or opens) the Sirius session for a project.
     * <p>
     * Searches for a {@code .aird} file in the project root and opens the
     * associated Sirius session if it is not already open.
     *
     * @param project the Eclipse project containing a Capella model
     * @return the Sirius Session
     * @throws IllegalStateException if no .aird file is found or session cannot be opened
     */
    public static Session getSession(IProject project) {
        IFile airdFile = null;
        try {
            for (IResource resource : project.members()) {
                if (resource instanceof IFile && resource.getFileExtension() != null
                        && "aird".equals(resource.getFileExtension())) {
                    airdFile = (IFile) resource;
                    break;
                }
            }
        } catch (CoreException e) {
            throw new IllegalStateException("Cannot list project members", e);
        }

        if (airdFile == null) {
            throw new IllegalStateException("No .aird file found in project: " + project.getName());
        }

        URI sessionResourceURI = URI.createPlatformResourceURI(
                airdFile.getFullPath().toOSString(), true);
        Session session = SessionManager.INSTANCE.getSession(sessionResourceURI, new NullProgressMonitor());

        if (!session.isOpen()) {
            session.open(new NullProgressMonitor());
        }

        return session;
    }

    /**
     * Gets the transactional editing domain from a Sirius session.
     *
     * @param session the Sirius Session
     * @return the TransactionalEditingDomain
     * @throws IllegalArgumentException if the session is null or invalid
     */
    public static TransactionalEditingDomain getEditingDomain(Session session) {
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }
        return session.getTransactionalEditingDomain();
    }

    /**
     * Gets the semantic root element from a Sirius session.
     * <p>
     * The semantic root is typically the top-level {@code Project} or
     * {@code SystemEngineering} element of the Capella model.
     *
     * @param session the Sirius Session
     * @return the root EObject of the semantic model
     * @throws IllegalStateException if the root cannot be obtained
     */
    public static EObject getSemanticRoot(Session session) {
        Collection<Resource> semanticResources = session.getSemanticResources();
        for (Resource resource : semanticResources) {
            if (!resource.getContents().isEmpty()) {
                return resource.getContents().get(0);
            }
        }
        throw new IllegalStateException("No semantic root found in session");
    }

    /**
     * Finds a model element by its UUID across all resources in the session.
     * <p>
     * Traverses all semantic resources in the session, searching for an element
     * whose EMF URI fragment matches the given UUID.
     *
     * @param session the Sirius Session
     * @param uuid    the UUID to search for
     * @return the matching EObject, or null if not found
     */
    public static EObject findElementByUuid(Session session, String uuid) {
        for (Resource resource : session.getSemanticResources()) {
            TreeIterator<EObject> iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                EObject element = iterator.next();
                String fragment = resource.getURIFragment(element);
                if (uuid.equals(fragment)) {
                    return element;
                }
            }
        }
        return null;
    }
}

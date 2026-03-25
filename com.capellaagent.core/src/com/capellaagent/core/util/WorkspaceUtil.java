package com.capellaagent.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * Static utility methods for Eclipse workspace operations.
 * <p>
 * Provides convenience methods for finding and managing Capella projects
 * in the Eclipse workspace.
 */
public final class WorkspaceUtil {

    private static final Logger LOG = Logger.getLogger(WorkspaceUtil.class.getName());

    /**
     * PLACEHOLDER: The Capella project nature ID.
     * <p>
     * The actual value depends on the Capella version. Common values:
     * <ul>
     *   <li>Capella 5.x/6.x: {@code org.polarsys.capella.project.nature}</li>
     *   <li>Capella 7.x: may differ</li>
     * </ul>
     */
    // PLACEHOLDER: Update this constant for your Capella version
    private static final String CAPELLA_NATURE_ID = "org.polarsys.capella.project.nature";

    private WorkspaceUtil() {
        // Utility class
    }

    /**
     * Returns all projects in the workspace that contain a {@code .aird} file.
     * <p>
     * This is a heuristic: any project with a .aird file is considered a Capella
     * project, even if it does not have the Capella nature.
     *
     * @return a list of projects containing .aird files
     */
    public static List<IProject> getCapellaProjects() {
        List<IProject> capellaProjects = new ArrayList<>();

        IProject[] allProjects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        for (IProject project : allProjects) {
            if (!project.isOpen()) {
                continue;
            }
            try {
                if (hasAirdFile(project)) {
                    capellaProjects.add(project);
                }
            } catch (CoreException e) {
                LOG.log(Level.WARNING,
                        "Error checking project " + project.getName() + " for .aird files", e);
            }
        }

        return capellaProjects;
    }

    /**
     * Ensures a project is open, opening it if necessary.
     *
     * @param project the project to ensure is open
     * @param monitor a progress monitor, or null
     * @throws CoreException if the project cannot be opened
     */
    public static void ensureOpen(IProject project, IProgressMonitor monitor) throws CoreException {
        if (!project.isOpen()) {
            LOG.info("Opening project: " + project.getName());
            project.open(monitor);
        }
    }

    /**
     * Finds a project by name in the workspace.
     *
     * @param name the project name
     * @return the project, or null if not found
     */
    public static IProject findProjectByName(String name) {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
        if (project != null && project.exists()) {
            return project;
        }
        return null;
    }

    /**
     * Checks whether a project has the Capella nature.
     * <p>
     * PLACEHOLDER: The nature ID constant must be updated for your Capella version.
     *
     * @param project the project to check
     * @return true if the project has the Capella nature
     */
    public static boolean hasCapellaNature(IProject project) {
        if (!project.isOpen()) {
            return false;
        }
        try {
            // PLACEHOLDER: Verify CAPELLA_NATURE_ID matches your Capella version
            return project.hasNature(CAPELLA_NATURE_ID);
        } catch (CoreException e) {
            LOG.log(Level.WARNING,
                    "Error checking Capella nature on project " + project.getName(), e);
            return false;
        }
    }

    /**
     * Checks whether a project contains at least one {@code .aird} file.
     *
     * @param project the project to check
     * @return true if the project has a .aird file
     * @throws CoreException if the project members cannot be listed
     */
    private static boolean hasAirdFile(IProject project) throws CoreException {
        for (IResource resource : project.members()) {
            if (resource.getType() == IResource.FILE &&
                    resource.getFileExtension() != null &&
                    "aird".equals(resource.getFileExtension())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns all open projects in the workspace.
     *
     * @return a list of open projects
     */
    public static List<IProject> getOpenProjects() {
        List<IProject> openProjects = new ArrayList<>();
        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (project.isOpen()) {
                openProjects.add(project);
            }
        }
        return openProjects;
    }
}

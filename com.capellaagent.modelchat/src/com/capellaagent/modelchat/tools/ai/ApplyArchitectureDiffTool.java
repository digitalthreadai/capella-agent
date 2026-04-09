package com.capellaagent.modelchat.tools.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.sirius.business.api.session.Session;

import com.capellaagent.core.staging.InMemoryStagingArea;
import com.capellaagent.core.staging.PendingDiff;
import com.capellaagent.core.staging.ProposedChange;
import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * WRITE-category tool that applies a previously staged architecture diff.
 * <p>
 * All operations are executed inside a single {@link RecordingCommand} so that
 * one Ctrl+Z in Eclipse undoes the entire proposal. This is the commit step
 * of the propose→review→apply workflow.
 * <p>
 * <b>Safety checks:</b>
 * <ul>
 *   <li>Diff must exist in {@link InMemoryStagingArea} (10-minute TTL)</li>
 *   <li>Project name at apply time must match project name at staging time</li>
 * </ul>
 */
public class ApplyArchitectureDiffTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "apply_architecture_diff";
    private static final String DESCRIPTION =
            "Applies a previously staged architecture diff to the model. "
            + "ALL changes are executed in one undoable command (single Ctrl+Z). "
            + "Requires the user to have confirmed the diff_id returned by propose_architecture_changes.";

    public ApplyArchitectureDiffTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("session_id",
                "The session ID used when propose_architecture_changes was called"));
        params.add(ToolParameter.requiredString("diff_id",
                "The diff_id returned by propose_architecture_changes"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String sessionId = getRequiredString(parameters, "session_id");
        String diffId = getRequiredString(parameters, "diff_id");

        // Look up staged diff
        Optional<PendingDiff> pending = InMemoryStagingArea.getInstance().get(sessionId, diffId);
        if (pending.isEmpty()) {
            return ToolResult.error(
                    "Proposal not found or expired (10-minute TTL). "
                    + "Re-run propose_architecture_changes to generate a new proposal.");
        }

        PendingDiff diff = pending.get();

        if (diff.isExpired()) {
            InMemoryStagingArea.getInstance().discard(sessionId, diffId);
            return ToolResult.error(
                    "Proposal expired (10-minute TTL). "
                    + "Re-run propose_architecture_changes to generate a new proposal.");
        }

        // Verify project name matches
        try {
            Session siriusSession = getActiveSession();
            String currentProject = siriusSession != null
                    ? siriusSession.getSessionResource().getURI().lastSegment() : "unknown";
            if (!"unknown".equals(diff.projectName())
                    && !currentProject.equals(diff.projectName())) {
                return ToolResult.error(
                        "Project mismatch: diff was staged for project '"
                        + diff.projectName() + "' but current project is '"
                        + currentProject + "'.");
            }
        } catch (Exception e) {
            // Non-fatal — proceed
        }

        // Execute all changes in one RecordingCommand
        List<String> applied = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        try {
            Session siriusSession = getActiveSession();
            TransactionalEditingDomain domain = siriusSession.getTransactionalEditingDomain();

            RecordingCommand cmd = new RecordingCommand(domain,
                    "Apply Architecture Diff " + diffId) {
                @Override
                protected void doExecute() {
                    for (ProposedChange change : diff.changes()) {
                        try {
                            applyChange(change, siriusSession);
                            applied.add(change.operation() + " " + change.elementType()
                                    + " \"" + change.name() + "\"");
                        } catch (Exception e) {
                            failed.add(change.operation() + " " + change.name()
                                    + ": " + e.getMessage());
                        }
                    }
                }
            };

            domain.getCommandStack().execute(cmd);

        } catch (Exception e) {
            return ToolResult.error("Failed to execute recording command: " + e.getMessage());
        }

        // Discard the staging entry
        InMemoryStagingArea.getInstance().discard(sessionId, diffId);

        JsonObject result = new JsonObject();
        result.addProperty("diff_id", diffId);
        result.addProperty("applied_count", applied.size());
        result.addProperty("failed_count", failed.size());

        if (!applied.isEmpty()) {
            JsonArray appliedArr = new JsonArray();
            applied.forEach(appliedArr::add);
            result.add("applied", appliedArr);
        }
        if (!failed.isEmpty()) {
            JsonArray failedArr = new JsonArray();
            failed.forEach(failedArr::add);
            result.add("failed", failedArr);
        }

        String message = applied.size() + " change(s) applied successfully. "
                + "Use Ctrl+Z (Edit \u2192 Undo) to undo all changes at once.";
        if (!failed.isEmpty()) {
            message += " Warning: model is in a partially applied state ("
                    + failed.size() + " change(s) failed). "
                    + "Use Ctrl+Z to undo the entire operation and start over.";
        }
        result.addProperty("message", message);

        return ToolResult.success(result);
    }

    private void applyChange(ProposedChange change, Session session) throws Exception {
        switch (change.operation().toUpperCase()) {
            case "CREATE":
                createElement(change, session);
                break;
            case "MODIFY":
                modifyElement(change, session);
                break;
            case "DELETE":
                deleteElement(change, session);
                break;
            default:
                throw new IllegalArgumentException("Unknown op: " + change.operation());
        }
    }

    private void createElement(ProposedChange change, Session session) throws Exception {
        // Find parent
        EObject parent = null;
        if (change.parentUuid() != null && !change.parentUuid().isEmpty()) {
            parent = resolveElementByUuid(change.parentUuid());
            if (parent == null) {
                throw new IllegalArgumentException(
                        "Parent UUID '" + change.parentUuid() + "' not found");
            }
        } else {
            parent = getRootElement(session);
        }

        // Create element by type name via EFactory
        EObject newElement = createElementByTypeName(change.elementType());

        // Set name
        EStructuralFeature nameFeature = newElement.eClass().getEStructuralFeature("name");
        if (nameFeature != null) {
            newElement.eSet(nameFeature, change.name());
        }

        // Add to parent via first suitable containment reference
        for (EReference ref : parent.eClass().getEAllContainments()) {
            if (ref.getEReferenceType().isSuperTypeOf(newElement.eClass())) {
                if (ref.isMany()) {
                    @SuppressWarnings("unchecked")
                    List<EObject> list = (List<EObject>) parent.eGet(ref);
                    list.add(newElement);
                } else {
                    parent.eSet(ref, newElement);
                }
                return;
            }
        }
        throw new IllegalArgumentException(
                "No suitable containment found in parent " + parent.eClass().getName()
                + " for type " + change.elementType());
    }

    private void modifyElement(ProposedChange change, Session session) throws Exception {
        if (change.targetUuid() == null || change.targetUuid().isEmpty()) {
            throw new IllegalArgumentException("MODIFY requires target_uuid");
        }
        EObject target = resolveElementByUuid(change.targetUuid());
        if (target == null) {
            throw new IllegalArgumentException("Target UUID not found: " + change.targetUuid());
        }
        EStructuralFeature nameFeature = target.eClass().getEStructuralFeature("name");
        if (nameFeature != null && !change.name().isEmpty()) {
            target.eSet(nameFeature, change.name());
        }
    }

    private void deleteElement(ProposedChange change, Session session) throws Exception {
        if (change.targetUuid() == null || change.targetUuid().isEmpty()) {
            throw new IllegalArgumentException("DELETE requires target_uuid");
        }
        EObject target = resolveElementByUuid(change.targetUuid());
        if (target == null) {
            throw new IllegalArgumentException("Target UUID not found: " + change.targetUuid());
        }
        EcoreUtil.delete(target, true);
    }

    private EObject getRootElement(Session session) {
        for (org.eclipse.emf.ecore.resource.Resource r : session.getSemanticResources()) {
            if (!r.getContents().isEmpty()) return r.getContents().get(0);
        }
        throw new IllegalStateException("No semantic resources in session");
    }

    private EObject createElementByTypeName(String typeName) throws Exception {
        // Search for EClass by name in the registry
        for (Object pkg : EPackage.Registry.INSTANCE.values()) {
            if (pkg instanceof EPackage) {
                EPackage ePackage = (EPackage) pkg;
                EClassifier cl = ePackage.getEClassifier(typeName);
                if (cl instanceof EClass) {
                    EClass eClass = (EClass) cl;
                    if (!eClass.isAbstract() && !eClass.isInterface()) {
                        return ePackage.getEFactoryInstance().create(eClass);
                    }
                }
            }
        }
        throw new IllegalArgumentException(
                "Unknown element type: '" + typeName + "'. "
                + "Use a valid Capella EClass name like LogicalComponent, SystemFunction, etc.");
    }
}

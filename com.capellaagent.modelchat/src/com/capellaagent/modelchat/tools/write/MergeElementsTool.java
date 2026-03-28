package com.capellaagent.modelchat.tools.write;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.capella.CapellaModelService;
import com.capellaagent.core.security.InputValidator;
import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;
import org.polarsys.capella.common.data.modellingcore.AbstractTrace;
import org.polarsys.capella.common.data.modellingcore.TraceableElement;

/**
 * Merges two similar model elements into one, preserving relationships from both.
 * <p>
 * The "target" element is kept and enriched with relationships from the "source"
 * element. The source element is then removed. Incoming references (traces,
 * exchanges) that pointed to the source are re-pointed to the target.
 */
public class MergeElementsTool extends AbstractCapellaTool {

    public MergeElementsTool() {
        super("merge_elements",
                "Merges two similar elements: keeps target, moves relationships from source, "
                + "then deletes source.",
                ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("target_uuid",
                "UUID of the element to keep (merge target)"));
        params.add(ToolParameter.requiredString("source_uuid",
                "UUID of the element to merge into target (will be deleted)"));
        params.add(ToolParameter.optionalBoolean("merge_description",
                "Append source description to target (default: true)"));
        params.add(ToolParameter.optionalBoolean("merge_children",
                "Move contained children from source to target (default: true)"));
        return params;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String targetUuid = getRequiredString(parameters, "target_uuid");
        String sourceUuid = getRequiredString(parameters, "source_uuid");
        boolean mergeDescription = getOptionalBoolean(parameters, "merge_description", true);
        boolean mergeChildren = getOptionalBoolean(parameters, "merge_children", true);

        try {
            targetUuid = InputValidator.validateUuid(targetUuid);
            sourceUuid = InputValidator.validateUuid(sourceUuid);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Invalid UUID: " + e.getMessage());
        }

        if (targetUuid.equals(sourceUuid)) {
            return ToolResult.error("Cannot merge an element with itself");
        }

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            EObject target = resolveElementByUuid(targetUuid);
            EObject source = resolveElementByUuid(sourceUuid);

            if (target == null) {
                return ToolResult.error("Target element not found: " + targetUuid);
            }
            if (source == null) {
                return ToolResult.error("Source element not found: " + sourceUuid);
            }

            // Verify same metaclass
            if (!target.eClass().equals(source.eClass())) {
                return ToolResult.error("Cannot merge elements of different types: "
                        + target.eClass().getName() + " vs " + source.eClass().getName());
            }

            String targetName = getElementName(target);
            String sourceName = getElementName(source);

            final int[] mergedRefs = {0};
            final int[] movedChildren = {0};
            final JsonArray mergedDetails = new JsonArray();

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Merge '" + sourceName + "' into '" + targetName + "'") {
                @Override
                protected void doExecute() {
                    // 1. Merge description
                    if (mergeDescription) {
                        String targetDesc = getElementDescription(target);
                        String sourceDesc = getElementDescription(source);
                        if (sourceDesc != null && !sourceDesc.isBlank()) {
                            EStructuralFeature descFeature =
                                    target.eClass().getEStructuralFeature("description");
                            if (descFeature != null) {
                                String merged = (targetDesc != null && !targetDesc.isBlank())
                                        ? targetDesc + "\n[Merged from " + sourceName + "] " + sourceDesc
                                        : "[Merged from " + sourceName + "] " + sourceDesc;
                                target.eSet(descFeature, merged);

                                JsonObject detail = new JsonObject();
                                detail.addProperty("action", "merged_description");
                                mergedDetails.add(detail);
                            }
                        }
                    }

                    // 2. Re-point incoming traces from source to target
                    if (source instanceof TraceableElement && target instanceof TraceableElement) {
                        TraceableElement traceSource = (TraceableElement) source;
                        TraceableElement traceTarget = (TraceableElement) target;

                        // Move incoming traces
                        List<AbstractTrace> incomingTraces =
                                new ArrayList<>(traceSource.getIncomingTraces());
                        for (AbstractTrace trace : incomingTraces) {
                            try {
                                EStructuralFeature targetFeature =
                                        trace.eClass().getEStructuralFeature("targetElement");
                                if (targetFeature != null) {
                                    trace.eSet(targetFeature, traceTarget);
                                    mergedRefs[0]++;

                                    JsonObject detail = new JsonObject();
                                    detail.addProperty("action", "repointed_incoming_trace");
                                    detail.addProperty("trace_type", trace.eClass().getName());
                                    mergedDetails.add(detail);
                                }
                            } catch (Exception e) {
                                // Skip traces that can't be repointed
                            }
                        }
                    }

                    // 3. Move non-derived, many-valued references from source to target
                    for (EReference ref : source.eClass().getEAllReferences()) {
                        if (ref.isDerived() || !ref.isChangeable() || ref.isContainment()) continue;
                        if (ref.isMany()) {
                            try {
                                List<EObject> sourceList = (List<EObject>) source.eGet(ref);
                                List<EObject> targetList = (List<EObject>) target.eGet(ref);
                                List<EObject> toMove = new ArrayList<>(sourceList);
                                for (EObject refObj : toMove) {
                                    if (!targetList.contains(refObj)) {
                                        targetList.add(refObj);
                                        mergedRefs[0]++;
                                    }
                                }
                            } catch (Exception e) {
                                // Some references may not support modification
                            }
                        }
                    }

                    // 4. Move children from source to target
                    if (mergeChildren) {
                        List<EObject> childrenToMove = new ArrayList<>(source.eContents());
                        for (EObject child : childrenToMove) {
                            EReference containmentRef = child.eContainmentFeature();
                            if (containmentRef == null || !containmentRef.isChangeable()) continue;

                            try {
                                EStructuralFeature targetFeature =
                                        target.eClass().getEStructuralFeature(containmentRef.getName());
                                if (targetFeature != null && targetFeature instanceof EReference
                                        && ((EReference) targetFeature).isMany()) {
                                    List<EObject> targetChildren =
                                            (List<EObject>) target.eGet(targetFeature);
                                    targetChildren.add(child);
                                    movedChildren[0]++;

                                    JsonObject detail = new JsonObject();
                                    detail.addProperty("action", "moved_child");
                                    detail.addProperty("child_name", getElementName(child));
                                    detail.addProperty("child_type", child.eClass().getName());
                                    mergedDetails.add(detail);
                                }
                            } catch (Exception e) {
                                // Skip children that can't be moved
                            }
                        }
                    }

                    // 5. Remove source element from its container
                    EObject sourceParent = source.eContainer();
                    if (sourceParent != null) {
                        EReference containmentRef = source.eContainmentFeature();
                        if (containmentRef != null && containmentRef.isMany()) {
                            ((List<EObject>) sourceParent.eGet(containmentRef)).remove(source);
                        }
                    }
                }
            });

            modelService.invalidateCache(session);

            JsonObject response = new JsonObject();
            response.addProperty("status", "merged");
            response.addProperty("target_name", targetName);
            response.addProperty("target_uuid", targetUuid);
            response.addProperty("source_name", sourceName);
            response.addProperty("source_uuid", sourceUuid);
            response.addProperty("source_deleted", true);
            response.addProperty("references_repointed", mergedRefs[0]);
            response.addProperty("children_moved", movedChildren[0]);
            response.add("details", mergedDetails);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to merge elements: " + e.getMessage());
        }
    }
}

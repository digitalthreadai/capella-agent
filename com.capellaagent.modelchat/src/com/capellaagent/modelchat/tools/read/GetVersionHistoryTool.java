package com.capellaagent.modelchat.tools.read;

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

import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.change.ChangeDescription;
import org.eclipse.emf.ecore.change.FeatureChange;
import org.eclipse.emf.ecore.util.EContentAdapter;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;
import org.polarsys.capella.common.data.modellingcore.AbstractTrace;
import org.polarsys.capella.common.data.modellingcore.TraceableElement;
import org.polarsys.capella.common.data.modellingcore.ModelElement;

/**
 * Retrieves version/change history information for a model element by
 * inspecting its EMF resource metadata, modification timestamps,
 * and realization/trace references that record evolution across layers.
 * <p>
 * Since Capella does not maintain a built-in audit trail within the model,
 * this tool assembles a "change profile" from:
 * <ul>
 *   <li>Element metadata (creation date, summary if stored in description)</li>
 *   <li>Incoming/outgoing traces (realizations, refinements) that document evolution</li>
 *   <li>Resource-level modification state</li>
 *   <li>Command stack state indicating unsaved changes</li>
 * </ul>
 */
public class GetVersionHistoryTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "get_version_history";
    private static final String DESCRIPTION =
            "Gets change/version history for a model element from traces, resource state, "
            + "and element metadata.";

    public GetVersionHistoryTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("element_uuid",
                "UUID of the element to get history for"));
        params.add(ToolParameter.optionalBoolean("include_traces",
                "Include realization/refinement traces as history entries (default: true)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String elementUuid = getRequiredString(parameters, "element_uuid");
        boolean includeTraces = getOptionalBoolean(parameters, "include_traces", true);

        try {
            elementUuid = InputValidator.validateUuid(elementUuid);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Invalid UUID: " + e.getMessage());
        }

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            EObject element = resolveElementByUuid(elementUuid);
            if (element == null) {
                return ToolResult.error("Element not found: " + elementUuid);
            }

            JsonArray historyEntries = new JsonArray();

            // 1. Element creation/identity info
            JsonObject creationEntry = new JsonObject();
            creationEntry.addProperty("event", "created");
            creationEntry.addProperty("element_name", getElementName(element));
            creationEntry.addProperty("element_type", element.eClass().getName());
            creationEntry.addProperty("element_id", getElementId(element));
            creationEntry.addProperty("layer", modelService.detectLayer(element));

            // Check for summary/description that may contain version notes
            String description = getElementDescription(element);
            if (description != null && !description.isBlank()) {
                creationEntry.addProperty("description_preview", truncate(description, 300));
            }

            // Check container hierarchy
            EObject container = element.eContainer();
            if (container != null) {
                creationEntry.addProperty("parent_name", getElementName(container));
                creationEntry.addProperty("parent_type", container.eClass().getName());
            }
            historyEntries.add(creationEntry);

            // 2. Resource modification state
            Resource resource = element.eResource();
            if (resource != null) {
                JsonObject resourceEntry = new JsonObject();
                resourceEntry.addProperty("event", "resource_state");
                resourceEntry.addProperty("resource_uri", resource.getURI().toString());
                resourceEntry.addProperty("is_modified", resource.isModified());
                resourceEntry.addProperty("is_loaded", resource.isLoaded());
                historyEntries.add(resourceEntry);
            }

            // 3. Command stack state (unsaved changes indicator)
            try {
                TransactionalEditingDomain domain = getEditingDomain(session);
                boolean canUndo = domain.getCommandStack().canUndo();
                boolean canRedo = domain.getCommandStack().canRedo();
                JsonObject stackEntry = new JsonObject();
                stackEntry.addProperty("event", "session_state");
                stackEntry.addProperty("has_unsaved_changes", canUndo);
                stackEntry.addProperty("can_redo", canRedo);
                historyEntries.add(stackEntry);
            } catch (Exception e) {
                // Domain may not be available
            }

            // 4. Trace-based history (realizations, refinements)
            if (includeTraces && element instanceof TraceableElement) {
                TraceableElement traceable = (TraceableElement) element;

                // Outgoing traces: this element realizes/refines something
                List<AbstractTrace> outTraces = traceable.getOutgoingTraces();
                for (AbstractTrace trace : outTraces) {
                    TraceableElement target = trace.getTargetElement();
                    if (target != null) {
                        JsonObject traceEntry = new JsonObject();
                        traceEntry.addProperty("event", "outgoing_trace");
                        traceEntry.addProperty("trace_type", trace.eClass().getName());
                        traceEntry.addProperty("target_name", getElementName(target));
                        traceEntry.addProperty("target_type", target.eClass().getName());
                        traceEntry.addProperty("target_id", getElementId(target));
                        traceEntry.addProperty("target_layer", modelService.detectLayer(target));
                        traceEntry.addProperty("interpretation",
                                "This element realizes/refines '" + getElementName(target) + "'");
                        historyEntries.add(traceEntry);
                    }
                }

                // Incoming traces: something realizes/refines this element
                List<AbstractTrace> inTraces = traceable.getIncomingTraces();
                for (AbstractTrace trace : inTraces) {
                    TraceableElement source = trace.getSourceElement();
                    if (source != null) {
                        JsonObject traceEntry = new JsonObject();
                        traceEntry.addProperty("event", "incoming_trace");
                        traceEntry.addProperty("trace_type", trace.eClass().getName());
                        traceEntry.addProperty("source_name", getElementName(source));
                        traceEntry.addProperty("source_type", source.eClass().getName());
                        traceEntry.addProperty("source_id", getElementId(source));
                        traceEntry.addProperty("source_layer", modelService.detectLayer(source));
                        traceEntry.addProperty("interpretation",
                                "'" + getElementName(source) + "' realizes/refines this element");
                        historyEntries.add(traceEntry);
                    }
                }
            }

            // 5. Structural feature summary (what attributes/references are set)
            JsonArray populatedFeatures = new JsonArray();
            for (EStructuralFeature feature : element.eClass().getEAllStructuralFeatures()) {
                if (element.eIsSet(feature) && !feature.isDerived()) {
                    JsonObject feat = new JsonObject();
                    feat.addProperty("feature", feature.getName());
                    feat.addProperty("type", feature.getEType().getName());
                    feat.addProperty("is_many", feature.isMany());
                    if (feature.isMany()) {
                        Object val = element.eGet(feature);
                        if (val instanceof Collection) {
                            feat.addProperty("count", ((Collection<?>) val).size());
                        }
                    }
                    populatedFeatures.add(feat);
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("element_name", getElementName(element));
            response.addProperty("element_uuid", elementUuid);
            response.addProperty("element_type", element.eClass().getName());
            response.addProperty("history_entry_count", historyEntries.size());
            response.add("history", historyEntries);
            response.addProperty("populated_feature_count", populatedFeatures.size());
            response.add("populated_features", populatedFeatures);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to get version history: " + e.getMessage());
        }
    }
}

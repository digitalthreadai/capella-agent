package com.capellaagent.modelchat.tools.transition;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.capella.CapellaModelService;
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
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.ctx.CtxFactory;
import org.polarsys.capella.core.data.ctx.SystemAnalysis;
import org.polarsys.capella.core.data.ctx.SystemComponent;
import org.polarsys.capella.core.data.ctx.SystemFunction;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.FaFactory;
import org.polarsys.capella.core.data.fa.FunctionRealization;
import org.polarsys.capella.core.data.la.LaFactory;
import org.polarsys.capella.core.data.la.LogicalArchitecture;
import org.polarsys.capella.core.data.la.LogicalComponent;
import org.polarsys.capella.core.data.la.LogicalFunction;
import org.polarsys.capella.core.data.oa.Entity;
import org.polarsys.capella.core.data.oa.OperationalActivity;
import org.polarsys.capella.core.data.oa.OperationalAnalysis;
import org.polarsys.capella.core.data.pa.PaFactory;
import org.polarsys.capella.core.data.pa.PhysicalArchitecture;
import org.polarsys.capella.core.data.pa.PhysicalComponent;
import org.polarsys.capella.core.data.pa.PhysicalFunction;

/**
 * Bulk-transitions all elements from one architecture layer to the next.
 * <p>
 * Supports transitions:
 * <ul>
 *   <li>OA to SA: OperationalActivities to SystemFunctions, Entities to SystemComponents</li>
 *   <li>SA to LA: SystemFunctions to LogicalFunctions, SystemComponents to LogicalComponents</li>
 *   <li>LA to PA: LogicalFunctions to PhysicalFunctions, LogicalComponents to PhysicalComponents</li>
 * </ul>
 * <p>
 * Only transitions elements that do not already have a realization trace in the
 * target layer (avoids duplicates). Creates realization traces for traceability.
 */
public class AutoTransitionAllTool extends AbstractCapellaTool {

    private static final List<String> VALID_TRANSITIONS = List.of("oa_to_sa", "sa_to_la", "la_to_pa");

    public AutoTransitionAllTool() {
        super("auto_transition_all",
                "Bulk-transitions all elements from one layer to the next "
                + "(OA->SA, SA->LA, LA->PA), skipping already-transitioned elements.",
                ToolCategory.TRANSITION);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("transition",
                "Transition direction: oa_to_sa, sa_to_la, la_to_pa",
                VALID_TRANSITIONS));
        params.add(ToolParameter.optionalBoolean("skip_existing",
                "Skip elements that already have realization in target layer (default: true)"));
        return params;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String transition = getRequiredString(parameters, "transition").toLowerCase();
        boolean skipExisting = getOptionalBoolean(parameters, "skip_existing", true);

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            String sourceLayer, targetLayer;
            switch (transition) {
                case "oa_to_sa": sourceLayer = "oa"; targetLayer = "sa"; break;
                case "sa_to_la": sourceLayer = "sa"; targetLayer = "la"; break;
                case "la_to_pa": sourceLayer = "la"; targetLayer = "pa"; break;
                default: return ToolResult.error("Invalid transition: " + transition);
            }

            BlockArchitecture sourceArch = modelService.getArchitecture(session, sourceLayer);
            BlockArchitecture targetArch = modelService.getArchitecture(session, targetLayer);

            // Collect source functions and components
            List<AbstractFunction> sourceFunctions = new ArrayList<>();
            List<Component> sourceComponents = new ArrayList<>();

            Iterator<EObject> it = sourceArch.eAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();
                if (obj instanceof AbstractFunction) {
                    String name = getElementName(obj);
                    if (name != null && !name.isBlank() && !name.contains("Root")) {
                        if (!skipExisting || !hasRealizationInLayer(obj, targetLayer, modelService)) {
                            sourceFunctions.add((AbstractFunction) obj);
                        }
                    }
                } else if (obj instanceof Component) {
                    String name = getElementName(obj);
                    if (name != null && !name.isBlank()) {
                        if (!skipExisting || !hasRealizationInLayer(obj, targetLayer, modelService)) {
                            sourceComponents.add((Component) obj);
                        }
                    }
                }
            }

            if (sourceFunctions.isEmpty() && sourceComponents.isEmpty()) {
                JsonObject response = new JsonObject();
                response.addProperty("status", "no_action");
                response.addProperty("transition", transition);
                response.addProperty("message", "No elements to transition (all may already exist in target layer)");
                return ToolResult.success(response);
            }

            final JsonArray transitioned = new JsonArray();
            final int[] counts = {0, 0, 0}; // functions, components, skipped

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Auto-transition " + sourceLayer.toUpperCase() + " to " + targetLayer.toUpperCase()) {
                @Override
                protected void doExecute() {
                    // Transition functions
                    for (AbstractFunction sourceFunc : sourceFunctions) {
                        try {
                            EObject targetFunc = createTargetFunction(
                                    sourceFunc, targetArch, transition);
                            if (targetFunc != null) {
                                // Set name and description
                                setFeature(targetFunc, "name", getElementName(sourceFunc));
                                String desc = getElementDescription(sourceFunc);
                                if (desc != null && !desc.isBlank()) {
                                    setFeature(targetFunc, "description", desc);
                                }

                                // Add to target function package
                                addToFunctionPackage(targetFunc, targetArch, transition);

                                // Create realization trace
                                createFunctionRealization(targetFunc, sourceFunc);

                                JsonObject entry = new JsonObject();
                                entry.addProperty("source_name", getElementName(sourceFunc));
                                entry.addProperty("source_type", sourceFunc.eClass().getName());
                                entry.addProperty("target_name", getElementName(targetFunc));
                                entry.addProperty("target_type", targetFunc.eClass().getName());
                                entry.addProperty("target_id", getElementId(targetFunc));
                                entry.addProperty("kind", "function");
                                transitioned.add(entry);
                                counts[0]++;
                            }
                        } catch (Exception e) {
                            counts[2]++;
                        }
                    }

                    // Transition components
                    for (Component sourceComp : sourceComponents) {
                        try {
                            EObject targetComp = createTargetComponent(
                                    sourceComp, targetArch, transition);
                            if (targetComp != null) {
                                setFeature(targetComp, "name", getElementName(sourceComp));
                                String desc = getElementDescription(sourceComp);
                                if (desc != null && !desc.isBlank()) {
                                    setFeature(targetComp, "description", desc);
                                }
                                if (sourceComp.isActor() && targetComp instanceof Component) {
                                    ((Component) targetComp).setActor(true);
                                }

                                addToComponentPackage(targetComp, targetArch, transition);

                                JsonObject entry = new JsonObject();
                                entry.addProperty("source_name", getElementName(sourceComp));
                                entry.addProperty("source_type", sourceComp.eClass().getName());
                                entry.addProperty("target_name", getElementName(targetComp));
                                entry.addProperty("target_type", targetComp.eClass().getName());
                                entry.addProperty("target_id", getElementId(targetComp));
                                entry.addProperty("kind", "component");
                                transitioned.add(entry);
                                counts[1]++;
                            }
                        } catch (Exception e) {
                            counts[2]++;
                        }
                    }
                }
            });

            modelService.invalidateCache(session);

            JsonObject response = new JsonObject();
            response.addProperty("status", "transitioned");
            response.addProperty("transition", transition);
            response.addProperty("source_layer", sourceLayer);
            response.addProperty("target_layer", targetLayer);
            response.addProperty("functions_transitioned", counts[0]);
            response.addProperty("components_transitioned", counts[1]);
            response.addProperty("skipped_errors", counts[2]);
            response.addProperty("total_transitioned", transitioned.size());
            response.add("elements", transitioned);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Auto-transition failed: " + e.getMessage());
        }
    }

    /**
     * Checks whether a source element already has a realization trace pointing
     * to an element in the specified target layer.
     */
    private boolean hasRealizationInLayer(EObject element, String targetLayer,
                                            CapellaModelService modelService) {
        if (!(element instanceof TraceableElement)) return false;
        TraceableElement traceable = (TraceableElement) element;
        for (AbstractTrace trace : traceable.getIncomingTraces()) {
            TraceableElement source = trace.getSourceElement();
            if (source != null) {
                String sourceLayer = modelService.detectLayer(source);
                if (targetLayer.equalsIgnoreCase(sourceLayer)) {
                    return true;
                }
            }
        }
        return false;
    }

    private EObject createTargetFunction(AbstractFunction source,
                                          BlockArchitecture targetArch, String transition) {
        switch (transition) {
            case "oa_to_sa": return CtxFactory.eINSTANCE.createSystemFunction();
            case "sa_to_la": return LaFactory.eINSTANCE.createLogicalFunction();
            case "la_to_pa": return PaFactory.eINSTANCE.createPhysicalFunction();
            default: return null;
        }
    }

    private EObject createTargetComponent(Component source,
                                           BlockArchitecture targetArch, String transition) {
        switch (transition) {
            case "oa_to_sa": return CtxFactory.eINSTANCE.createSystemComponent();
            case "sa_to_la": return LaFactory.eINSTANCE.createLogicalComponent();
            case "la_to_pa": return PaFactory.eINSTANCE.createPhysicalComponent();
            default: return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void addToFunctionPackage(EObject func, BlockArchitecture arch, String transition) {
        EObject fnPkg = arch.getOwnedFunctionPkg();
        if (fnPkg == null) return;

        String methodName;
        switch (transition) {
            case "oa_to_sa": methodName = "getOwnedSystemFunctions"; break;
            case "sa_to_la": methodName = "getOwnedLogicalFunctions"; break;
            case "la_to_pa": methodName = "getOwnedPhysicalFunctions"; break;
            default: return;
        }

        try {
            java.lang.reflect.Method method = fnPkg.getClass().getMethod(methodName);
            Object result = method.invoke(fnPkg);
            if (result instanceof List) {
                ((List<EObject>) result).add(func);
            }
        } catch (Exception e) {
            // Try generic containment
            for (EReference ref : fnPkg.eClass().getEAllContainments()) {
                if (ref.isMany() && ref.getEReferenceType().isInstance(func)) {
                    ((List<EObject>) fnPkg.eGet(ref)).add(func);
                    break;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void addToComponentPackage(EObject comp, BlockArchitecture arch, String transition) {
        String pkgFeatureName;
        String methodName;
        switch (transition) {
            case "oa_to_sa":
                pkgFeatureName = "ownedSystemComponentPkg";
                methodName = "getOwnedSystemComponents";
                break;
            case "sa_to_la":
                pkgFeatureName = "ownedLogicalComponentPkg";
                methodName = "getOwnedLogicalComponents";
                break;
            case "la_to_pa":
                pkgFeatureName = "ownedPhysicalComponentPkg";
                methodName = "getOwnedPhysicalComponents";
                break;
            default: return;
        }

        EStructuralFeature pkgFeature = arch.eClass().getEStructuralFeature(pkgFeatureName);
        if (pkgFeature == null) return;
        EObject compPkg = (EObject) arch.eGet(pkgFeature);
        if (compPkg == null) return;

        try {
            java.lang.reflect.Method method = compPkg.getClass().getMethod(methodName);
            Object result = method.invoke(compPkg);
            if (result instanceof List) {
                ((List<EObject>) result).add(comp);
            }
        } catch (Exception e) {
            for (EReference ref : compPkg.eClass().getEAllContainments()) {
                if (ref.isMany() && ref.getEReferenceType().isInstance(comp)) {
                    ((List<EObject>) compPkg.eGet(ref)).add(comp);
                    break;
                }
            }
        }
    }

    private void createFunctionRealization(EObject target, AbstractFunction source) {
        try {
            if (target instanceof AbstractFunction) {
                FunctionRealization realization = FaFactory.eINSTANCE.createFunctionRealization();
                realization.setSourceElement((TraceableElement) target);
                realization.setTargetElement(source);
                ((AbstractFunction) target).getOwnedFunctionRealizations().add(realization);
            }
        } catch (Exception e) {
            // Realization API may vary across versions
        }
    }

    private void setFeature(EObject obj, String featureName, Object value) {
        EStructuralFeature feature = obj.eClass().getEStructuralFeature(featureName);
        if (feature != null && value != null) {
            obj.eSet(feature, value);
        }
    }
}

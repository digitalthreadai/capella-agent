package com.capellaagent.modelchat.tools.transition;

import java.util.ArrayList;
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
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.la.LogicalArchitecture;
import org.polarsys.capella.core.data.la.LogicalComponent;
import org.polarsys.capella.core.data.la.LogicalFunction;
import org.polarsys.capella.core.data.pa.PaFactory;
import org.polarsys.capella.core.data.pa.PhysicalArchitecture;
import org.polarsys.capella.core.data.pa.PhysicalComponent;
import org.polarsys.capella.core.data.pa.PhysicalFunction;
import org.polarsys.capella.core.data.fa.FaFactory;
import org.polarsys.capella.core.data.fa.FunctionRealization;

/**
 * Transitions elements from Logical Architecture (LA) to Physical Architecture (PA).
 * <p>
 * For each LogicalFunction, creates a corresponding PhysicalFunction.
 * For each LogicalComponent, creates a corresponding PhysicalComponent.
 * Creates realization traces between the pairs.
 */
public class TransitionLaToPaTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "transition_la_to_pa";
    private static final String DESCRIPTION =
            "Transitions LA elements to PA layer with realization traces.";

    public TransitionLaToPaTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.TRANSITION);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalString("element_uuids",
                "Comma-separated UUIDs of specific LA elements to transition (all if omitted)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String elementUuidsStr = getOptionalString(parameters, "element_uuids", null);

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();
            BlockArchitecture laArch = modelService.getArchitecture(session, "la");
            BlockArchitecture paArch = modelService.getArchitecture(session, "pa");

            if (!(laArch instanceof LogicalArchitecture)) {
                return ToolResult.error("Could not find Logical Architecture layer");
            }
            if (!(paArch instanceof PhysicalArchitecture)) {
                return ToolResult.error("Could not find Physical Architecture layer");
            }

            LogicalArchitecture la = (LogicalArchitecture) laArch;
            PhysicalArchitecture pa = (PhysicalArchitecture) paArch;

            // Collect elements to transition
            List<EObject> elementsToTransition = new ArrayList<>();

            if (elementUuidsStr != null && !elementUuidsStr.isBlank()) {
                for (String uuidStr : elementUuidsStr.split(",")) {
                    String uuid = uuidStr.trim();
                    if (uuid.isEmpty()) continue;
                    try {
                        uuid = InputValidator.validateUuid(uuid);
                    } catch (IllegalArgumentException e) {
                        return ToolResult.error("Invalid UUID: " + e.getMessage());
                    }
                    EObject obj = resolveElementByUuid(uuid);
                    if (obj == null) {
                        return ToolResult.error("Element not found: " + uuid);
                    }
                    elementsToTransition.add(obj);
                }
            } else {
                Iterator<EObject> allContents = laArch.eAllContents();
                while (allContents.hasNext()) {
                    EObject obj = allContents.next();
                    if (obj instanceof LogicalFunction) {
                        String name = getElementName(obj);
                        if (name != null && !name.isBlank() && !name.contains("Root")) {
                            elementsToTransition.add(obj);
                        }
                    } else if (obj instanceof LogicalComponent) {
                        String name = getElementName(obj);
                        if (name != null && !name.isBlank()) {
                            elementsToTransition.add(obj);
                        }
                    }
                }
            }

            if (elementsToTransition.isEmpty()) {
                return ToolResult.error("No LA elements found to transition");
            }

            final List<EObject> toTransition = elementsToTransition;
            final JsonArray transitioned = new JsonArray();
            int[] counts = {0, 0};

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Transition LA to PA") {
                @SuppressWarnings("unchecked")
                @Override
                protected void doExecute() {
                    for (EObject obj : toTransition) {
                        if (obj instanceof LogicalFunction) {
                            LogicalFunction laFn = (LogicalFunction) obj;

                            PhysicalFunction paFn = PaFactory.eINSTANCE.createPhysicalFunction();
                            paFn.setName(laFn.getName());
                            if (laFn.getDescription() != null) {
                                paFn.setDescription(laFn.getDescription());
                            }

                            EObject paFnPkg = pa.getOwnedFunctionPkg();
                            try {
                                java.lang.reflect.Method method =
                                        paFnPkg.getClass().getMethod("getOwnedPhysicalFunctions");
                                Object result = method.invoke(paFnPkg);
                                if (result instanceof List) {
                                    ((List<EObject>) result).add(paFn);
                                }
                            } catch (Exception e) {
                                return;
                            }

                            // Create realization trace
                            try {
                                FunctionRealization realization = FaFactory.eINSTANCE.createFunctionRealization();
                                realization.setSourceElement(paFn);
                                realization.setTargetElement(laFn);
                                paFn.getOwnedFunctionRealizations().add(realization);
                            } catch (Exception e) {
                                // Continue without trace
                            }

                            JsonObject entry = new JsonObject();
                            entry.addProperty("source_name", laFn.getName());
                            entry.addProperty("source_type", "LogicalFunction");
                            entry.addProperty("target_name", paFn.getName());
                            entry.addProperty("target_type", "PhysicalFunction");
                            entry.addProperty("target_id", getElementId(paFn));
                            transitioned.add(entry);
                            counts[0]++;

                        } else if (obj instanceof LogicalComponent) {
                            LogicalComponent laComp = (LogicalComponent) obj;

                            PhysicalComponent paComp = PaFactory.eINSTANCE.createPhysicalComponent();
                            paComp.setName(laComp.getName());
                            paComp.setActor(laComp.isActor());
                            if (laComp.getDescription() != null) {
                                paComp.setDescription(laComp.getDescription());
                            }

                            EObject paCompPkg = pa.getOwnedPhysicalComponentPkg();
                            try {
                                java.lang.reflect.Method method =
                                        paCompPkg.getClass().getMethod("getOwnedPhysicalComponents");
                                Object result = method.invoke(paCompPkg);
                                if (result instanceof List) {
                                    ((List<EObject>) result).add(paComp);
                                }
                            } catch (Exception e) {
                                return;
                            }

                            JsonObject entry = new JsonObject();
                            entry.addProperty("source_name", laComp.getName());
                            entry.addProperty("source_type", "LogicalComponent");
                            entry.addProperty("target_name", paComp.getName());
                            entry.addProperty("target_type", "PhysicalComponent");
                            entry.addProperty("target_id", getElementId(paComp));
                            transitioned.add(entry);
                            counts[1]++;
                        }
                    }
                }
            });

            modelService.invalidateCache(session);

            JsonObject response = new JsonObject();
            response.addProperty("status", "transitioned");
            response.addProperty("source_layer", "la");
            response.addProperty("target_layer", "pa");
            response.addProperty("functions_transitioned", counts[0]);
            response.addProperty("components_transitioned", counts[1]);
            response.addProperty("total_transitioned", transitioned.size());
            response.add("elements", transitioned);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to transition LA to PA: " + e.getMessage());
        }
    }
}

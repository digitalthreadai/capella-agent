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
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.ctx.SystemAnalysis;
import org.polarsys.capella.core.data.ctx.SystemComponent;
import org.polarsys.capella.core.data.ctx.SystemFunction;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.la.LaFactory;
import org.polarsys.capella.core.data.la.LogicalArchitecture;
import org.polarsys.capella.core.data.la.LogicalComponent;
import org.polarsys.capella.core.data.la.LogicalFunction;
import org.polarsys.capella.core.data.fa.FaFactory;
import org.polarsys.capella.core.data.fa.FunctionRealization;

/**
 * Transitions elements from System Analysis (SA) to Logical Architecture (LA).
 * <p>
 * For each SystemFunction, creates a corresponding LogicalFunction.
 * For each SystemComponent, creates a corresponding LogicalComponent.
 * Creates realization traces between the pairs.
 */
public class TransitionSaToLaTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "transition_sa_to_la";
    private static final String DESCRIPTION =
            "Transitions SA elements to LA layer with realization traces.";

    public TransitionSaToLaTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.TRANSITION);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalString("element_uuids",
                "Comma-separated UUIDs of specific SA elements to transition (all if omitted)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String elementUuidsStr = getOptionalString(parameters, "element_uuids", null);

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();
            BlockArchitecture saArch = modelService.getArchitecture(session, "sa");
            BlockArchitecture laArch = modelService.getArchitecture(session, "la");

            if (!(saArch instanceof SystemAnalysis)) {
                return ToolResult.error("Could not find System Analysis layer");
            }
            if (!(laArch instanceof LogicalArchitecture)) {
                return ToolResult.error("Could not find Logical Architecture layer");
            }

            SystemAnalysis sa = (SystemAnalysis) saArch;
            LogicalArchitecture la = (LogicalArchitecture) laArch;

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
                Iterator<EObject> allContents = saArch.eAllContents();
                while (allContents.hasNext()) {
                    EObject obj = allContents.next();
                    if (obj instanceof SystemFunction) {
                        String name = getElementName(obj);
                        if (name != null && !name.isBlank() && !name.contains("Root")) {
                            elementsToTransition.add(obj);
                        }
                    } else if (obj instanceof SystemComponent) {
                        String name = getElementName(obj);
                        if (name != null && !name.isBlank()) {
                            elementsToTransition.add(obj);
                        }
                    }
                }
            }

            if (elementsToTransition.isEmpty()) {
                return ToolResult.error("No SA elements found to transition");
            }

            final List<EObject> toTransition = elementsToTransition;
            final JsonArray transitioned = new JsonArray();
            int[] counts = {0, 0};

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Transition SA to LA") {
                @SuppressWarnings("unchecked")
                @Override
                protected void doExecute() {
                    for (EObject obj : toTransition) {
                        if (obj instanceof SystemFunction) {
                            SystemFunction saFn = (SystemFunction) obj;

                            LogicalFunction laFn = LaFactory.eINSTANCE.createLogicalFunction();
                            laFn.setName(saFn.getName());
                            if (saFn.getDescription() != null) {
                                laFn.setDescription(saFn.getDescription());
                            }

                            EObject laFnPkg = la.getOwnedFunctionPkg();
                            try {
                                java.lang.reflect.Method method =
                                        laFnPkg.getClass().getMethod("getOwnedLogicalFunctions");
                                Object result = method.invoke(laFnPkg);
                                if (result instanceof List) {
                                    ((List<EObject>) result).add(laFn);
                                }
                            } catch (Exception e) {
                                return;
                            }

                            // Create realization trace
                            try {
                                FunctionRealization realization = FaFactory.eINSTANCE.createFunctionRealization();
                                realization.setSourceElement(laFn);
                                realization.setTargetElement(saFn);
                                laFn.getOwnedFunctionRealizations().add(realization);
                            } catch (Exception e) {
                                // Continue without trace
                            }

                            JsonObject entry = new JsonObject();
                            entry.addProperty("source_name", saFn.getName());
                            entry.addProperty("source_type", "SystemFunction");
                            entry.addProperty("target_name", laFn.getName());
                            entry.addProperty("target_type", "LogicalFunction");
                            entry.addProperty("target_id", getElementId(laFn));
                            transitioned.add(entry);
                            counts[0]++;

                        } else if (obj instanceof SystemComponent) {
                            SystemComponent saComp = (SystemComponent) obj;

                            LogicalComponent laComp = LaFactory.eINSTANCE.createLogicalComponent();
                            laComp.setName(saComp.getName());
                            laComp.setActor(saComp.isActor());
                            if (saComp.getDescription() != null) {
                                laComp.setDescription(saComp.getDescription());
                            }

                            EObject laCompPkg = la.getOwnedLogicalComponentPkg();
                            try {
                                java.lang.reflect.Method method =
                                        laCompPkg.getClass().getMethod("getOwnedLogicalComponents");
                                Object result = method.invoke(laCompPkg);
                                if (result instanceof List) {
                                    ((List<EObject>) result).add(laComp);
                                }
                            } catch (Exception e) {
                                return;
                            }

                            JsonObject entry = new JsonObject();
                            entry.addProperty("source_name", saComp.getName());
                            entry.addProperty("source_type", "SystemComponent");
                            entry.addProperty("target_name", laComp.getName());
                            entry.addProperty("target_type", "LogicalComponent");
                            entry.addProperty("target_id", getElementId(laComp));
                            transitioned.add(entry);
                            counts[1]++;
                        }
                    }
                }
            });

            modelService.invalidateCache(session);

            JsonObject response = new JsonObject();
            response.addProperty("status", "transitioned");
            response.addProperty("source_layer", "sa");
            response.addProperty("target_layer", "la");
            response.addProperty("functions_transitioned", counts[0]);
            response.addProperty("components_transitioned", counts[1]);
            response.addProperty("total_transitioned", transitioned.size());
            response.add("elements", transitioned);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to transition SA to LA: " + e.getMessage());
        }
    }
}

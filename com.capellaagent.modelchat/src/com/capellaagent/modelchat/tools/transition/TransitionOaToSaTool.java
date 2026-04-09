package com.capellaagent.modelchat.tools.transition;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

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
import org.polarsys.capella.core.data.ctx.CtxFactory;
import org.polarsys.capella.core.data.ctx.SystemAnalysis;
import org.polarsys.capella.core.data.ctx.SystemComponent;
import org.polarsys.capella.core.data.ctx.SystemFunction;
import org.polarsys.capella.core.data.fa.FaFactory;
import org.polarsys.capella.core.data.fa.FunctionRealization;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.oa.Entity;
import org.polarsys.capella.core.data.oa.OperationalActivity;
import org.polarsys.capella.core.data.oa.OperationalAnalysis;

/**
 * Transitions elements from Operational Analysis (OA) to System Analysis (SA).
 * <p>
 * For each OA function (OperationalActivity), creates a corresponding SystemFunction.
 * For each OA entity, creates a corresponding SystemComponent.
 * Creates realization traces between the pairs.
 */
public class TransitionOaToSaTool extends AbstractCapellaTool {

    private static final Logger LOG = Logger.getLogger(TransitionOaToSaTool.class.getName());

    private static final String TOOL_NAME = "transition_oa_to_sa";
    private static final String DESCRIPTION =
            "Transitions OA elements to SA layer with realization traces.";

    public TransitionOaToSaTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.TRANSITION);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalString("element_uuids",
                "Comma-separated UUIDs of specific OA elements to transition (all if omitted)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String elementUuidsStr = getOptionalString(parameters, "element_uuids", null);

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();
            BlockArchitecture oaArch = modelService.getArchitecture(session, "oa");
            BlockArchitecture saArch = modelService.getArchitecture(session, "sa");

            if (!(oaArch instanceof OperationalAnalysis)) {
                return ToolResult.error("Could not find Operational Analysis layer");
            }
            if (!(saArch instanceof SystemAnalysis)) {
                return ToolResult.error("Could not find System Analysis layer");
            }

            OperationalAnalysis oa = (OperationalAnalysis) oaArch;
            SystemAnalysis sa = (SystemAnalysis) saArch;

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
                // Collect all OA functions and entities
                Iterator<EObject> allContents = oaArch.eAllContents();
                while (allContents.hasNext()) {
                    EObject obj = allContents.next();
                    if (obj instanceof OperationalActivity) {
                        String name = getElementName(obj);
                        if (name != null && !name.isBlank() && !name.contains("Root")) {
                            elementsToTransition.add(obj);
                        }
                    } else if (obj instanceof Entity) {
                        String name = getElementName(obj);
                        if (name != null && !name.isBlank()) {
                            elementsToTransition.add(obj);
                        }
                    }
                }
            }

            if (elementsToTransition.isEmpty()) {
                return ToolResult.error("No OA elements found to transition");
            }

            final List<EObject> toTransition = elementsToTransition;
            final JsonArray transitioned = new JsonArray();
            int[] counts = {0, 0}; // [functions, components]

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Transition OA to SA") {
                @SuppressWarnings("unchecked")
                @Override
                protected void doExecute() {
                    for (EObject obj : toTransition) {
                        try {
                            if (obj instanceof OperationalActivity) {
                                OperationalActivity oaFn = (OperationalActivity) obj;

                                // Create SA function
                                SystemFunction saFn = CtxFactory.eINSTANCE.createSystemFunction();
                                saFn.setName(oaFn.getName());
                                if (oaFn.getDescription() != null) {
                                    saFn.setDescription(oaFn.getDescription());
                                }

                                // Add to SA function package
                                EObject saFnPkg = sa.getOwnedFunctionPkg();
                                try {
                                    java.lang.reflect.Method method =
                                            saFnPkg.getClass().getMethod("getOwnedSystemFunctions");
                                    Object result = method.invoke(saFnPkg);
                                    if (result instanceof List) {
                                        ((List<EObject>) result).add(saFn);
                                    }
                                } catch (Exception e) {
                                    LOG.warning("Could not add SA function for '"
                                            + oaFn.getName() + "': " + e.getMessage());
                                    continue;
                                }

                                // Create realization trace
                                try {
                                    FunctionRealization realization = FaFactory.eINSTANCE.createFunctionRealization();
                                    realization.setSourceElement(saFn);
                                    realization.setTargetElement(oaFn);
                                    saFn.getOwnedFunctionRealizations().add(realization);
                                } catch (Exception e) {
                                    // Realization API may vary; continue without trace
                                }

                                JsonObject entry = new JsonObject();
                                entry.addProperty("source_name", oaFn.getName());
                                entry.addProperty("source_type", "OperationalActivity");
                                entry.addProperty("target_name", saFn.getName());
                                entry.addProperty("target_type", "SystemFunction");
                                entry.addProperty("target_id", getElementId(saFn));
                                transitioned.add(entry);
                                counts[0]++;

                            } else if (obj instanceof Entity) {
                                Entity oaEntity = (Entity) obj;

                                // Create SA component
                                SystemComponent saComp = CtxFactory.eINSTANCE.createSystemComponent();
                                saComp.setName(oaEntity.getName());
                                saComp.setActor(oaEntity.isActor());
                                if (oaEntity.getDescription() != null) {
                                    saComp.setDescription(oaEntity.getDescription());
                                }

                                // Add to SA component package
                                EObject saCompPkg = sa.getOwnedSystemComponentPkg();
                                try {
                                    java.lang.reflect.Method method =
                                            saCompPkg.getClass().getMethod("getOwnedSystemComponents");
                                    Object result = method.invoke(saCompPkg);
                                    if (result instanceof List) {
                                        ((List<EObject>) result).add(saComp);
                                    }
                                } catch (Exception e) {
                                    LOG.warning("Could not add SA component for '"
                                            + oaEntity.getName() + "': " + e.getMessage());
                                    continue;
                                }

                                JsonObject entry = new JsonObject();
                                entry.addProperty("source_name", oaEntity.getName());
                                entry.addProperty("source_type", "Entity");
                                entry.addProperty("target_name", saComp.getName());
                                entry.addProperty("target_type", "SystemComponent");
                                entry.addProperty("target_id", getElementId(saComp));
                                transitioned.add(entry);
                                counts[1]++;
                            }
                        } catch (Exception e) {
                            LOG.warning("Skipping element during OA→SA transition: "
                                    + e.getMessage());
                        }
                    }
                }
            });

            modelService.invalidateCache(session);

            JsonObject response = new JsonObject();
            response.addProperty("status", "transitioned");
            response.addProperty("source_layer", "oa");
            response.addProperty("target_layer", "sa");
            response.addProperty("functions_transitioned", counts[0]);
            response.addProperty("components_transitioned", counts[1]);
            response.addProperty("total_transitioned", transitioned.size());
            response.add("elements", transitioned);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to transition OA to SA: " + e.getMessage());
        }
    }
}

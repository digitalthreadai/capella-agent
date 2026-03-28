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
import org.polarsys.capella.core.data.ctx.SystemAnalysis;
import org.polarsys.capella.core.data.ctx.SystemFunction;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.FaFactory;
import org.polarsys.capella.core.data.fa.FunctionRealization;
import org.polarsys.capella.core.data.la.LaFactory;
import org.polarsys.capella.core.data.la.LogicalArchitecture;
import org.polarsys.capella.core.data.la.LogicalFunction;
import org.polarsys.capella.core.data.oa.OperationalActivity;
import org.polarsys.capella.core.data.oa.OperationalAnalysis;
import org.polarsys.capella.core.data.pa.PaFactory;
import org.polarsys.capella.core.data.pa.PhysicalArchitecture;
import org.polarsys.capella.core.data.pa.PhysicalFunction;

/**
 * Transitions functions between any two adjacent architecture layers.
 * <p>
 * Supports:
 * <ul>
 *   <li>OA -> SA: OperationalActivity to SystemFunction</li>
 *   <li>SA -> LA: SystemFunction to LogicalFunction</li>
 *   <li>LA -> PA: LogicalFunction to PhysicalFunction</li>
 * </ul>
 * Creates realization traces between the source and target functions.
 * Can transition specific functions (by UUID) or all functions in a layer.
 */
public class TransitionFunctionsTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "transition_functions";
    private static final String DESCRIPTION =
            "Transitions functions between adjacent architecture layers with realization traces.";

    private static final List<String> VALID_TRANSITIONS = List.of("oa_to_sa", "sa_to_la", "la_to_pa");

    public TransitionFunctionsTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.TRANSITION);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("direction",
                "Transition direction: oa_to_sa, sa_to_la, la_to_pa",
                VALID_TRANSITIONS));
        params.add(ToolParameter.optionalString("function_uuids",
                "Comma-separated UUIDs of specific functions to transition (all if omitted)"));
        return params;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String direction = getRequiredString(parameters, "direction").toLowerCase();
        String functionUuidsStr = getOptionalString(parameters, "function_uuids", null);

        if (!VALID_TRANSITIONS.contains(direction)) {
            return ToolResult.error("Invalid direction. Must be: oa_to_sa, sa_to_la, la_to_pa");
        }

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();

            String sourceLayerName, targetLayerName;
            switch (direction) {
                case "oa_to_sa": sourceLayerName = "oa"; targetLayerName = "sa"; break;
                case "sa_to_la": sourceLayerName = "sa"; targetLayerName = "la"; break;
                case "la_to_pa": sourceLayerName = "la"; targetLayerName = "pa"; break;
                default: return ToolResult.error("Unknown direction: " + direction);
            }

            BlockArchitecture sourceArch = modelService.getArchitecture(session, sourceLayerName);
            BlockArchitecture targetArch = modelService.getArchitecture(session, targetLayerName);

            // Collect source functions to transition
            List<AbstractFunction> sourceFunctions = new ArrayList<>();

            if (functionUuidsStr != null && !functionUuidsStr.isBlank()) {
                for (String uuidStr : functionUuidsStr.split(",")) {
                    String uuid = uuidStr.trim();
                    if (uuid.isEmpty()) continue;
                    try {
                        uuid = InputValidator.validateUuid(uuid);
                    } catch (IllegalArgumentException e) {
                        return ToolResult.error("Invalid UUID: " + e.getMessage());
                    }
                    EObject obj = resolveElementByUuid(uuid);
                    if (obj == null) {
                        return ToolResult.error("Function not found: " + uuid);
                    }
                    if (!(obj instanceof AbstractFunction)) {
                        return ToolResult.error("Element is not a function: " + uuid);
                    }
                    sourceFunctions.add((AbstractFunction) obj);
                }
            } else {
                Iterator<EObject> it = sourceArch.eAllContents();
                while (it.hasNext()) {
                    EObject obj = it.next();
                    if (obj instanceof AbstractFunction) {
                        String name = getElementName(obj);
                        if (name != null && !name.isBlank() && !name.contains("Root")) {
                            sourceFunctions.add((AbstractFunction) obj);
                        }
                    }
                }
            }

            if (sourceFunctions.isEmpty()) {
                return ToolResult.error("No functions found to transition");
            }

            // Get the target function package
            EObject targetFnPkg = targetArch.getOwnedFunctionPkg();
            if (targetFnPkg == null) {
                return ToolResult.error("Target layer has no function package");
            }

            // Determine the list getter method and factory based on direction
            final JsonArray transitioned = new JsonArray();
            final int[] count = {0};

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Transition functions " + direction) {
                @Override
                protected void doExecute() {
                    for (AbstractFunction srcFn : sourceFunctions) {
                        AbstractFunction targetFn = createTargetFunction(direction, srcFn);
                        if (targetFn == null) continue;

                        // Add to target package
                        String listGetter = getTargetListGetter(direction);
                        try {
                            java.lang.reflect.Method method =
                                    targetFnPkg.getClass().getMethod(listGetter);
                            Object result = method.invoke(targetFnPkg);
                            if (result instanceof List) {
                                ((List<EObject>) result).add(targetFn);
                            }
                        } catch (Exception e) {
                            return;
                        }

                        // Create realization trace
                        try {
                            FunctionRealization realization =
                                    FaFactory.eINSTANCE.createFunctionRealization();
                            realization.setSourceElement(targetFn);
                            realization.setTargetElement(srcFn);
                            targetFn.getOwnedFunctionRealizations().add(realization);
                        } catch (Exception e) {
                            // Continue without trace
                        }

                        JsonObject entry = new JsonObject();
                        entry.addProperty("source_name", srcFn.getName());
                        entry.addProperty("source_type", srcFn.eClass().getName());
                        entry.addProperty("source_id", getElementId(srcFn));
                        entry.addProperty("target_name", targetFn.getName());
                        entry.addProperty("target_type", targetFn.eClass().getName());
                        entry.addProperty("target_id", getElementId(targetFn));
                        transitioned.add(entry);
                        count[0]++;
                    }
                }
            });

            modelService.invalidateCache(session);

            JsonObject response = new JsonObject();
            response.addProperty("status", "transitioned");
            response.addProperty("direction", direction);
            response.addProperty("source_layer", sourceLayerName);
            response.addProperty("target_layer", targetLayerName);
            response.addProperty("functions_transitioned", count[0]);
            response.add("elements", transitioned);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to transition functions: " + e.getMessage());
        }
    }

    /**
     * Creates a target function in the appropriate layer using the correct factory.
     */
    private AbstractFunction createTargetFunction(String direction, AbstractFunction source) {
        switch (direction) {
            case "oa_to_sa": {
                org.polarsys.capella.core.data.ctx.SystemFunction fn =
                        org.polarsys.capella.core.data.ctx.CtxFactory.eINSTANCE.createSystemFunction();
                fn.setName(source.getName());
                if (source.getDescription() != null) fn.setDescription(source.getDescription());
                return fn;
            }
            case "sa_to_la": {
                LogicalFunction fn = LaFactory.eINSTANCE.createLogicalFunction();
                fn.setName(source.getName());
                if (source.getDescription() != null) fn.setDescription(source.getDescription());
                return fn;
            }
            case "la_to_pa": {
                PhysicalFunction fn = PaFactory.eINSTANCE.createPhysicalFunction();
                fn.setName(source.getName());
                if (source.getDescription() != null) fn.setDescription(source.getDescription());
                return fn;
            }
            default:
                return null;
        }
    }

    /**
     * Returns the getter method name for the target function package's owned functions list.
     */
    private String getTargetListGetter(String direction) {
        switch (direction) {
            case "oa_to_sa": return "getOwnedSystemFunctions";
            case "sa_to_la": return "getOwnedLogicalFunctions";
            case "la_to_pa": return "getOwnedPhysicalFunctions";
            default: return "getOwnedFunctions";
        }
    }
}

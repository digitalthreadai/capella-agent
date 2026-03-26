package com.capellaagent.modelchat.tools.write;

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
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.information.DataPkg;
import org.polarsys.capella.core.data.information.ExchangeItem;
import org.polarsys.capella.core.data.information.ExchangeMechanism;
import org.polarsys.capella.core.data.information.InformationFactory;

/**
 * Creates an ExchangeItem in the specified layer's DataPkg.
 */
public class CreateExchangeItemTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");
    private static final List<String> VALID_KINDS = List.of("EVENT", "FLOW", "OPERATION", "SHARED_DATA");

    public CreateExchangeItemTool() {
        super("create_exchange_item",
                "Creates an exchange item in a layer's data package.",
                ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS));
        params.add(ToolParameter.requiredString("name",
                "Name of the exchange item"));
        params.add(ToolParameter.optionalEnum("kind",
                "Exchange mechanism: EVENT, FLOW, OPERATION, SHARED_DATA",
                VALID_KINDS, "FLOW"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        String rawName = getRequiredString(parameters, "name");
        String kind = getOptionalString(parameters, "kind", "FLOW").toUpperCase();

        String name;
        try {
            name = InputValidator.sanitizeName(rawName);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Invalid name: " + e.getMessage());
        }

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();
            BlockArchitecture arch = modelService.getArchitecture(session, layer);

            // Navigate to DataPkg
            DataPkg dataPkg = null;
            try {
                dataPkg = (DataPkg) arch.getClass().getMethod("getOwnedDataPkg").invoke(arch);
            } catch (Exception e) {
                return ToolResult.error("Cannot find DataPkg in layer " + layer);
            }

            if (dataPkg == null) {
                return ToolResult.error("No DataPkg found in layer " + layer);
            }

            ExchangeMechanism mechanism;
            switch (kind) {
                case "EVENT": mechanism = ExchangeMechanism.EVENT; break;
                case "OPERATION": mechanism = ExchangeMechanism.OPERATION; break;
                case "SHARED_DATA": mechanism = ExchangeMechanism.SHARED_DATA; break;
                default: mechanism = ExchangeMechanism.FLOW; break;
            }

            final DataPkg pkg = dataPkg;
            final String eiName = name;
            final ExchangeMechanism mech = mechanism;
            final EObject[] created = new EObject[1];

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Create exchange item '" + name + "'") {
                @Override
                protected void doExecute() {
                    ExchangeItem ei = InformationFactory.eINSTANCE.createExchangeItem();
                    ei.setName(eiName);
                    ei.setExchangeMechanism(mech);
                    pkg.getOwnedExchangeItems().add(ei);
                    created[0] = ei;
                }
            });

            if (created[0] == null) {
                return ToolResult.error("Exchange item creation failed");
            }

            modelService.invalidateCache(session);

            JsonObject response = new JsonObject();
            response.addProperty("status", "created");
            response.addProperty("name", getElementName(created[0]));
            response.addProperty("uuid", getElementId(created[0]));
            response.addProperty("kind", kind);
            response.addProperty("layer", layer);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to create exchange item: " + e.getMessage());
        }
    }
}

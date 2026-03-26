package com.capellaagent.modelchat.tools.write;

import java.util.ArrayList;
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
import org.polarsys.capella.core.data.cs.CsFactory;
import org.polarsys.capella.core.data.cs.Interface;
import org.polarsys.capella.core.data.cs.InterfacePkg;
import org.polarsys.capella.core.data.information.ExchangeItem;
import org.polarsys.capella.core.data.information.InformationFactory;

/**
 * Creates a new Interface in the specified architecture layer.
 * <p>
 * Optionally creates ExchangeItems and allocates them to the interface.
 * The interface is placed in the layer's InterfacePkg.
 */
public class CreateInterfaceTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "create_interface";
    private static final String DESCRIPTION =
            "Creates an interface with optional exchange items in a layer.";

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public CreateInterfaceTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS));
        params.add(ToolParameter.requiredString("name",
                "Name of the new interface"));
        params.add(ToolParameter.optionalString("exchange_item_names",
                "Comma-separated names of exchange items to create and allocate"));
        params.add(ToolParameter.optionalString("description",
                "Description text for the interface"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        String rawName = getRequiredString(parameters, "name");
        String exchangeItemNames = getOptionalString(parameters, "exchange_item_names", null);
        String rawDescription = getOptionalString(parameters, "description", null);

        if (!VALID_LAYERS.contains(layer)) {
            return ToolResult.error("Invalid layer '" + layer + "'");
        }

        // Sanitize inputs
        String name;
        String description;
        try {
            name = InputValidator.sanitizeName(rawName);
            description = rawDescription != null ? InputValidator.sanitizeDescription(rawDescription) : null;
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Input validation failed: " + e.getMessage());
        }

        // Parse exchange item names
        List<String> eiNames = new ArrayList<>();
        if (exchangeItemNames != null && !exchangeItemNames.isBlank()) {
            for (String eiName : exchangeItemNames.split(",")) {
                String trimmed = eiName.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        eiNames.add(InputValidator.sanitizeName(trimmed));
                    } catch (IllegalArgumentException e) {
                        return ToolResult.error("Invalid exchange item name '" + trimmed + "': " + e.getMessage());
                    }
                }
            }
        }

        try {
            CapellaModelService modelService = getModelService();
            Session session = getActiveSession();
            BlockArchitecture architecture = modelService.getArchitecture(session, layer);

            // Find the InterfacePkg
            InterfacePkg interfacePkg = architecture.getOwnedInterfacePkg();
            if (interfacePkg == null) {
                return ToolResult.error("No InterfacePkg found in the " + layer + " architecture");
            }

            final String ifaceName = name;
            final String ifaceDescription = description;
            final List<String> itemNames = eiNames;
            final EObject[] created = new EObject[1];
            final List<EObject> createdItems = new ArrayList<>();

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Create interface '" + name + "'") {
                @Override
                protected void doExecute() {
                    // Create the Interface
                    Interface iface = CsFactory.eINSTANCE.createInterface();
                    iface.setName(ifaceName);
                    if (ifaceDescription != null) {
                        iface.setDescription(ifaceDescription);
                    }
                    interfacePkg.getOwnedInterfaces().add(iface);

                    // Create and allocate exchange items
                    for (String eiName : itemNames) {
                        ExchangeItem ei = InformationFactory.eINSTANCE.createExchangeItem();
                        ei.setName(eiName);
                        // Add exchange item to the interface pkg's data pkg or directly allocate
                        // VERIFY: In Capella, ExchangeItems are typically in a DataPkg,
                        // but can be allocated to interfaces via exchangeItems reference
                        iface.getExchangeItems().add(ei);
                        createdItems.add(ei);
                    }

                    created[0] = iface;
                }
            });

            if (created[0] == null) {
                return ToolResult.error("Interface creation failed");
            }

            modelService.invalidateCache(session);

            JsonObject response = new JsonObject();
            response.addProperty("status", "created");
            response.addProperty("name", getElementName(created[0]));
            response.addProperty("uuid", getElementId(created[0]));
            response.addProperty("type", created[0].eClass().getName());
            response.addProperty("layer", layer);

            JsonArray eiArray = new JsonArray();
            for (EObject ei : createdItems) {
                JsonObject eiObj = new JsonObject();
                eiObj.addProperty("name", getElementName(ei));
                eiObj.addProperty("id", getElementId(ei));
                eiArray.add(eiObj);
            }
            response.add("exchange_items", eiArray);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to create interface: " + e.getMessage());
        }
    }
}

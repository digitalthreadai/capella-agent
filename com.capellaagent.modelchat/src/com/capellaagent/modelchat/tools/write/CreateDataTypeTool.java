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
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.information.DataPkg;
import org.polarsys.capella.core.data.information.InformationFactory;
import org.polarsys.capella.core.data.information.datatype.DatatypeFactory;

/**
 * Creates a new data type (class, enum, string, numeric, boolean) in a layer.
 */
public class CreateDataTypeTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");
    private static final List<String> VALID_TYPES = List.of("class", "enum", "string", "numeric", "boolean");

    public CreateDataTypeTool() {
        super("create_data_type",
                "Creates a data type (class, enum, string, etc.) in a layer.",
                ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS));
        params.add(ToolParameter.requiredEnum("type",
                "Data type kind: class, enum, string, numeric, boolean",
                VALID_TYPES));
        params.add(ToolParameter.requiredString("name",
                "Name of the data type"));
        return params;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        String type = getRequiredString(parameters, "type").toLowerCase();
        String rawName = getRequiredString(parameters, "name");

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

            final DataPkg pkg = dataPkg;
            final String dtName = name;
            final EObject[] created = new EObject[1];

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Create data type '" + name + "'") {
                @Override
                protected void doExecute() {
                    switch (type) {
                        case "class": {
                            var cls = InformationFactory.eINSTANCE.createClass();
                            cls.setName(dtName);
                            pkg.getOwnedClasses().add(cls);
                            created[0] = cls;
                            break;
                        }
                        case "enum": {
                            var en = DatatypeFactory.eINSTANCE.createEnumeration();
                            en.setName(dtName);
                            pkg.getOwnedDataTypes().add(en);
                            created[0] = en;
                            break;
                        }
                        case "string": {
                            var st = DatatypeFactory.eINSTANCE.createStringType();
                            st.setName(dtName);
                            pkg.getOwnedDataTypes().add(st);
                            created[0] = st;
                            break;
                        }
                        case "numeric": {
                            var nt = DatatypeFactory.eINSTANCE.createNumericType();
                            nt.setName(dtName);
                            pkg.getOwnedDataTypes().add(nt);
                            created[0] = nt;
                            break;
                        }
                        case "boolean": {
                            var bt = DatatypeFactory.eINSTANCE.createBooleanType();
                            bt.setName(dtName);
                            pkg.getOwnedDataTypes().add(bt);
                            created[0] = bt;
                            break;
                        }
                    }
                }
            });

            if (created[0] == null) {
                return ToolResult.error("Data type creation failed");
            }

            modelService.invalidateCache(session);

            JsonObject response = new JsonObject();
            response.addProperty("status", "created");
            response.addProperty("name", getElementName(created[0]));
            response.addProperty("uuid", getElementId(created[0]));
            response.addProperty("type", created[0].eClass().getName());
            response.addProperty("layer", layer);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to create data type: " + e.getMessage());
        }
    }
}

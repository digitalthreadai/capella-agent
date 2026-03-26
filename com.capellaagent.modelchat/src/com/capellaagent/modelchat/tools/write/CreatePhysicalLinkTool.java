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
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.core.data.cs.CsFactory;
import org.polarsys.capella.core.data.cs.PhysicalLink;
import org.polarsys.capella.core.data.cs.PhysicalPort;
import org.polarsys.capella.core.data.pa.PhysicalComponent;

/**
 * Creates a PhysicalLink between two PhysicalComponents.
 * <p>
 * Creates PhysicalPorts on each component and a PhysicalLink connecting them.
 * Both source and target must be PhysicalComponents in the Physical Architecture.
 */
public class CreatePhysicalLinkTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "create_physical_link";
    private static final String DESCRIPTION =
            "Creates a physical link between two physical components.";

    public CreatePhysicalLinkTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("source_uuid",
                "UUID of the source PhysicalComponent"));
        params.add(ToolParameter.requiredString("target_uuid",
                "UUID of the target PhysicalComponent"));
        params.add(ToolParameter.optionalString("name",
                "Name for the physical link (auto-generated if omitted)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String sourceUuid = getRequiredString(parameters, "source_uuid");
        String targetUuid = getRequiredString(parameters, "target_uuid");
        String name = getOptionalString(parameters, "name", null);

        try {
            sourceUuid = InputValidator.validateUuid(sourceUuid);
            targetUuid = InputValidator.validateUuid(targetUuid);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Invalid UUID: " + e.getMessage());
        }

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            // Resolve source and target
            EObject sourceObj = resolveElementByUuid(sourceUuid);
            if (sourceObj == null) {
                return ToolResult.error("Source not found with UUID: " + sourceUuid);
            }
            if (!(sourceObj instanceof PhysicalComponent)) {
                return ToolResult.error("Source is not a PhysicalComponent (type: "
                        + sourceObj.eClass().getName() + ")");
            }

            EObject targetObj = resolveElementByUuid(targetUuid);
            if (targetObj == null) {
                return ToolResult.error("Target not found with UUID: " + targetUuid);
            }
            if (!(targetObj instanceof PhysicalComponent)) {
                return ToolResult.error("Target is not a PhysicalComponent (type: "
                        + targetObj.eClass().getName() + ")");
            }

            PhysicalComponent srcComp = (PhysicalComponent) sourceObj;
            PhysicalComponent tgtComp = (PhysicalComponent) targetObj;

            // Auto-generate name if not provided
            if (name == null || name.isBlank()) {
                name = "[" + getElementName(srcComp) + "] to [" + getElementName(tgtComp) + "]";
            }
            name = InputValidator.sanitizeName(name);

            final String linkName = name;
            final EObject[] created = new EObject[1];

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Create physical link '" + linkName + "'") {
                @Override
                protected void doExecute() {
                    // Create physical ports on each component
                    PhysicalPort srcPort = CsFactory.eINSTANCE.createPhysicalPort();
                    srcPort.setName(linkName + "_src_port");
                    srcComp.getOwnedFeatures().add(srcPort);

                    PhysicalPort tgtPort = CsFactory.eINSTANCE.createPhysicalPort();
                    tgtPort.setName(linkName + "_tgt_port");
                    tgtComp.getOwnedFeatures().add(tgtPort);

                    // Create the physical link
                    PhysicalLink link = CsFactory.eINSTANCE.createPhysicalLink();
                    link.setName(linkName);
                    link.getLinkEnds().add(srcPort);
                    link.getLinkEnds().add(tgtPort);

                    // Add the link to the source component's owned physical links
                    srcComp.getOwnedPhysicalLinks().add(link);

                    created[0] = link;
                }
            });

            if (created[0] == null) {
                return ToolResult.error("Physical link creation failed");
            }

            modelService.invalidateCache(session);

            JsonObject response = new JsonObject();
            response.addProperty("status", "created");
            response.addProperty("name", getElementName(created[0]));
            response.addProperty("uuid", getElementId(created[0]));
            response.addProperty("type", created[0].eClass().getName());
            response.addProperty("source_name", getElementName(srcComp));
            response.addProperty("source_uuid", sourceUuid);
            response.addProperty("target_name", getElementName(tgtComp));
            response.addProperty("target_uuid", targetUuid);

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to create physical link: " + e.getMessage());
        }
    }
}

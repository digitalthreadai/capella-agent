package com.capellaagent.modelchat.tools.diagram;

import java.util.ArrayList;
import java.util.Collection;
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
import org.eclipse.sirius.business.api.dialect.DialectManager;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.diagram.DDiagram;
import org.eclipse.sirius.diagram.DDiagramElement;
import org.eclipse.sirius.viewpoint.DRepresentation;
import org.eclipse.sirius.viewpoint.DRepresentationDescriptor;

/**
 * Adds or removes a semantic element from an existing Sirius diagram.
 * <p>
 * For the "add" action, the tool refreshes the diagram via
 * {@link DialectManager#refresh(DRepresentation, org.eclipse.core.runtime.IProgressMonitor)},
 * which causes Sirius to synchronize the diagram with the model and add
 * representations for any newly reachable semantic elements. This is the
 * recommended approach for Capella diagrams since they use synchronized
 * mappings defined in the odesign.
 * <p>
 * For the "remove" action, the tool finds the {@link DDiagramElement}
 * representing the semantic element and removes it from the diagram. This
 * hides the element from the diagram without deleting the underlying
 * semantic model element.
 *
 * <h3>Tool Specification</h3>
 * <ul>
 *   <li><b>Name:</b> update_diagram</li>
 *   <li><b>Category:</b> diagram</li>
 *   <li><b>Parameters:</b>
 *     <ul>
 *       <li>{@code diagram_uuid} (string, required) - UUID of the diagram to modify</li>
 *       <li>{@code element_uuid} (string, required) - UUID of the semantic element to add/remove</li>
 *       <li>{@code action} (string, required) - Action to perform: add or remove</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class UpdateDiagramTool extends AbstractCapellaTool {

    private static final String TOOL_NAME = "update_diagram";
    private static final String DESCRIPTION =
            "Adds or removes a semantic element from a Sirius diagram. "
            + "'add' refreshes the diagram to include newly visible elements; "
            + "'remove' hides the element without deleting it from the model.";

    private static final List<String> VALID_ACTIONS = List.of("add", "remove");

    public UpdateDiagramTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.DIAGRAM);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("diagram_uuid",
                "UUID of the diagram to modify"));
        params.add(ToolParameter.requiredString("element_uuid",
                "UUID of the semantic element to add or remove from the diagram"));
        params.add(ToolParameter.requiredEnum("action",
                "Action to perform: add (refresh diagram to include element) "
                + "or remove (hide element from diagram)",
                VALID_ACTIONS));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String diagramUuid = getRequiredString(parameters, "diagram_uuid");
        String elementUuid = getRequiredString(parameters, "element_uuid");
        String action = getRequiredString(parameters, "action").toLowerCase();

        // Validate UUIDs
        try {
            diagramUuid = InputValidator.validateUuid(diagramUuid);
            elementUuid = InputValidator.validateUuid(elementUuid);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Invalid UUID: " + e.getMessage());
        }

        if (!VALID_ACTIONS.contains(action)) {
            return ToolResult.error("Invalid action '" + action
                    + "'. Must be one of: " + String.join(", ", VALID_ACTIONS));
        }

        try {
            Session session = getActiveSession();

            // Resolve diagram by UUID from the session's representation descriptors
            DDiagram diagram = findDiagramByUuid(session, diagramUuid);
            if (diagram == null) {
                return ToolResult.error("Diagram not found with UUID: " + diagramUuid);
            }

            // Resolve the semantic element
            EObject semanticElement = resolveElementByUuid(elementUuid);
            if (semanticElement == null) {
                return ToolResult.error("Semantic element not found with UUID: " + elementUuid);
            }

            TransactionalEditingDomain domain = getEditingDomain(session);
            final boolean[] success = {false};
            final String[] resultMessage = {""};
            final DDiagram targetDiagram = diagram;
            final EObject element = semanticElement;
            final String diagramAction = action;

            domain.getCommandStack().execute(new RecordingCommand(domain,
                    action + " element on diagram") {
                @Override
                protected void doExecute() {
                    if ("add".equals(diagramAction)) {
                        // Refresh the diagram to synchronize with the model.
                        // Sirius will automatically add diagram elements for semantic
                        // elements that match the diagram's mappings.
                        try {
                            DialectManager.INSTANCE.refresh(targetDiagram, null);
                            success[0] = true;
                            resultMessage[0] = "Diagram refreshed. If the element has a valid "
                                    + "mapping in the diagram description (odesign), it will now "
                                    + "appear on the diagram.";
                        } catch (Exception e) {
                            resultMessage[0] = "Diagram refresh failed: " + e.getMessage();
                        }
                    } else {
                        // Remove: find the DDiagramElement for this semantic element
                        // and remove it from the diagram
                        DDiagramElement found = null;
                        for (DDiagramElement dde : targetDiagram.getDiagramElements()) {
                            if (dde.getTarget() == element) {
                                found = dde;
                                break;
                            }
                        }
                        if (found != null) {
                            org.eclipse.emf.ecore.util.EcoreUtil.remove(found);
                            success[0] = true;
                            resultMessage[0] = "Element removed from diagram (hidden, "
                                    + "not deleted from model)";
                        } else {
                            success[0] = false;
                            resultMessage[0] = "Element was not found on this diagram. "
                                    + "It may not have a visible representation.";
                        }
                    }
                }
            });

            JsonObject response = new JsonObject();
            response.addProperty("status", success[0] ? "success" : "not_found");
            response.addProperty("action", action);
            response.addProperty("diagram_uuid", diagramUuid);
            response.addProperty("diagram_name", diagram.getName());
            response.addProperty("element_uuid", elementUuid);
            response.addProperty("element_name", getElementName(semanticElement));
            response.addProperty("message", resultMessage[0]);

            return success[0] ? ToolResult.success(response) : ToolResult.error(resultMessage[0]);

        } catch (Exception e) {
            return ToolResult.error("Failed to update diagram: " + e.getMessage());
        }
    }

    /**
     * Finds a DDiagram by its UUID from the session's representation descriptors.
     *
     * @param session     the Sirius session
     * @param diagramUuid the UUID of the diagram to find
     * @return the DDiagram, or null if not found
     */
    private DDiagram findDiagramByUuid(Session session, String diagramUuid) {
        Collection<DRepresentationDescriptor> descriptors =
                DialectManager.INSTANCE.getAllRepresentationDescriptors(session);

        for (DRepresentationDescriptor desc : descriptors) {
            // Check both the descriptor UID and the representation UID
            String uid = desc.getUid() != null ? desc.getUid().toString() : "";
            if (diagramUuid.equals(uid)) {
                DRepresentation rep = desc.getRepresentation();
                if (rep instanceof DDiagram) {
                    return (DDiagram) rep;
                }
            }

            // Also try matching via the representation's own ID
            try {
                DRepresentation rep = desc.getRepresentation();
                if (rep instanceof DDiagram) {
                    String repId = getElementId(rep);
                    if (diagramUuid.equals(repId)) {
                        return (DDiagram) rep;
                    }
                }
            } catch (Exception e) {
                // Representation may not be loadable; continue
            }
        }

        return null;
    }
}

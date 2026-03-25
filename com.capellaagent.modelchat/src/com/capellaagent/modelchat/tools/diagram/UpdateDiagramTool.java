package com.capellaagent.modelchat.tools.diagram;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.RecordingCommand;

// PLACEHOLDER imports for Sirius Diagram API
// import org.eclipse.sirius.business.api.dialect.DialectManager;
// import org.eclipse.sirius.business.api.session.Session;
// import org.eclipse.sirius.diagram.DDiagram;
// import org.eclipse.sirius.diagram.DDiagramElement;
// import org.eclipse.sirius.diagram.business.api.helper.display.DisplayServiceManager;
// import org.eclipse.sirius.diagram.business.internal.helper.task.operations.CreateViewTask;
// import org.eclipse.sirius.viewpoint.DRepresentation;
// import org.eclipse.sirius.viewpoint.DRepresentationDescriptor;
// import org.eclipse.sirius.diagram.tools.api.command.IDiagramCommandFactory;
// import org.eclipse.sirius.diagram.tools.api.command.IDiagramCommandFactoryProvider;

/**
 * Adds or removes a semantic element from an existing Sirius diagram.
 * <p>
 * When adding, the element is made visible on the diagram by creating a diagram
 * element (node or edge) for it. When removing, the diagram element is hidden
 * (removed from the diagram view) without deleting the underlying semantic element.
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
            + "'add' makes the element visible; 'remove' hides it without deleting the element.";

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
        params.add(ToolParameter.requiredString("action",
                "Action to perform: add (make element visible) or remove (hide element)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String diagramUuid = getRequiredString(parameters, "diagram_uuid");
        String elementUuid = getRequiredString(parameters, "element_uuid");
        String action = getRequiredString(parameters, "action").toLowerCase();

        if (diagramUuid.isBlank()) {
            return ToolResult.error("Parameter 'diagram_uuid' must not be empty");
        }
        if (elementUuid.isBlank()) {
            return ToolResult.error("Parameter 'element_uuid' must not be empty");
        }
        if (!VALID_ACTIONS.contains(action)) {
            return ToolResult.error("Invalid action '" + action
                    + "'. Must be one of: " + String.join(", ", VALID_ACTIONS));
        }

        try {
            // PLACEHOLDER: Resolve diagram by UUID
            // Session session = getActiveSession();
            // DRepresentationDescriptor descriptor = findDescriptorByUuid(session, diagramUuid);
            // if (descriptor == null) {
            //     return ToolResult.error("Diagram not found with UUID: " + diagramUuid);
            // }
            // DRepresentation representation = descriptor.getRepresentation();
            // if (!(representation instanceof DDiagram)) {
            //     return ToolResult.error("Representation is not a diagram");
            // }
            // DDiagram diagram = (DDiagram) representation;

            EObject semanticElement = resolveElementByUuid(elementUuid);
            if (semanticElement == null) {
                return ToolResult.error("Semantic element not found with UUID: " + elementUuid);
            }

            final String diagramAction = action;
            final EObject element = semanticElement;

            TransactionalEditingDomain domain = getEditingDomain();
            final boolean[] success = {false};
            final String[] resultMessage = {""};

            domain.getCommandStack().execute(new RecordingCommand(domain,
                    action + " element on diagram") {
                @Override
                protected void doExecute() {
                    if ("add".equals(diagramAction)) {
                        // PLACEHOLDER: Add element to diagram
                        //
                        // Approach 1: Use Sirius DiagramCommandFactory
                        // IDiagramCommandFactoryProvider provider =
                        //     (IDiagramCommandFactoryProvider) diagram.eAdapterOfType(
                        //         IDiagramCommandFactoryProvider.class);
                        // IDiagramCommandFactory factory = provider.getCommandFactory(domain);
                        //
                        // Approach 2: Use DialectManager to create a view
                        // The Sirius API for adding elements to diagrams typically
                        // involves creating a DNode or DEdge that references the
                        // semantic element. The exact API depends on the diagram
                        // description (odesign) mappings.
                        //
                        // For Capella, the recommended approach is:
                        // 1. Find the appropriate mapping for the element type
                        // 2. Create a DNode with that mapping pointing to the semantic element
                        // 3. Add the DNode to the diagram's owned diagram elements
                        //
                        // DDiagramElement diagramElement = createDiagramElement(diagram, element);
                        // if (diagramElement != null) {
                        //     success[0] = true;
                        //     resultMessage[0] = "Element added to diagram";
                        // }

                        success[0] = addElementToDiagram(diagramUuid, element);
                        resultMessage[0] = success[0] ? "Element added to diagram"
                                : "Failed to add element to diagram";

                    } else { // remove
                        // PLACEHOLDER: Remove element from diagram (hide, not delete)
                        //
                        // Find the DDiagramElement for this semantic element:
                        // for (DDiagramElement dde : diagram.getDiagramElements()) {
                        //     if (dde.getTarget() == element) {
                        //         // Remove from diagram (hides, does not delete semantic)
                        //         EcoreUtil.remove(dde);
                        //         success[0] = true;
                        //         break;
                        //     }
                        // }
                        // resultMessage[0] = success[0]
                        //     ? "Element removed from diagram"
                        //     : "Element was not found on this diagram";

                        success[0] = removeElementFromDiagram(diagramUuid, element);
                        resultMessage[0] = success[0] ? "Element removed from diagram"
                                : "Element was not found on this diagram";
                    }
                }
            });

            JsonObject response = new JsonObject();
            response.addProperty("status", success[0] ? "success" : "not_found");
            response.addProperty("action", action);
            response.addProperty("diagram_uuid", diagramUuid);
            response.addProperty("element_uuid", elementUuid);
            response.addProperty("element_name", getElementName(element));
            response.addProperty("message", resultMessage[0]);

            return success[0] ? ToolResult.success(response) : ToolResult.error(resultMessage[0]);

        } catch (Exception e) {
            return ToolResult.error("Failed to update diagram: " + e.getMessage());
        }
    }

    /**
     * Adds a semantic element's representation to the target diagram.
     *
     * @param diagramUuid the UUID of the diagram
     * @param element     the semantic element to add
     * @return true if the element was successfully added
     */
    private boolean addElementToDiagram(String diagramUuid, EObject element) {
        // PLACEHOLDER: Implement using Sirius DDiagram API
        return false;
    }

    /**
     * Removes a semantic element's representation from the target diagram.
     *
     * @param diagramUuid the UUID of the diagram
     * @param element     the semantic element whose diagram representation should be removed
     * @return true if the element was found and removed
     */
    private boolean removeElementFromDiagram(String diagramUuid, EObject element) {
        // PLACEHOLDER: Implement using Sirius DDiagram API
        return false;
    }
}

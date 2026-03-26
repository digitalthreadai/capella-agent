package com.capellaagent.modelchat.tools.export_;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.sirius.business.api.dialect.DialectManager;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.diagram.DDiagram;
import org.eclipse.sirius.viewpoint.DRepresentation;
import org.eclipse.sirius.viewpoint.DRepresentationDescriptor;

/**
 * Exports a catalog of all diagrams in the model.
 */
public class ExportDiagramCatalogTool extends AbstractCapellaTool {

    public ExportDiagramCatalogTool() {
        super("export_diagram_catalog",
                "Lists all diagrams with type, target, and element count.",
                ToolCategory.EXPORT);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        return List.of();
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        try {
            Session session = getActiveSession();

            Collection<DRepresentationDescriptor> descriptors =
                    DialectManager.INSTANCE.getAllRepresentationDescriptors(session);

            JsonArray catalog = new JsonArray();
            for (DRepresentationDescriptor desc : descriptors) {
                JsonObject entry = new JsonObject();
                entry.addProperty("name", desc.getName());
                entry.addProperty("uuid", desc.getUid() != null ? desc.getUid().toString() : "");
                entry.addProperty("description_name",
                        desc.getDescription() != null ? desc.getDescription().getName() : "");
                entry.addProperty("target",
                        desc.getTarget() != null ? getElementName(desc.getTarget()) : "");

                DRepresentation rep = desc.getRepresentation();
                if (rep instanceof DDiagram) {
                    entry.addProperty("element_count",
                            ((DDiagram) rep).getDiagramElements().size());
                } else {
                    entry.addProperty("element_count", -1);
                }

                catalog.add(entry);
            }

            JsonObject response = new JsonObject();
            response.addProperty("total_diagrams", catalog.size());
            response.add("diagrams", catalog);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to export diagram catalog: " + e.getMessage());
        }
    }
}

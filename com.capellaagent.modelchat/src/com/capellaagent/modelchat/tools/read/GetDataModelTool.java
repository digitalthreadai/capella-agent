package com.capellaagent.modelchat.tools.read;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.capella.CapellaModelService;
import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.information.Class;
import org.polarsys.capella.core.data.information.DataPkg;
import org.polarsys.capella.core.data.information.Property;
import org.polarsys.capella.core.data.information.datatype.BooleanType;
import org.polarsys.capella.core.data.information.datatype.DataType;
import org.polarsys.capella.core.data.information.datatype.Enumeration;
import org.polarsys.capella.core.data.information.datatype.NumericType;
import org.polarsys.capella.core.data.information.datatype.StringType;

/**
 * Retrieves the data model (classes, enumerations, data types) from a layer.
 */
public class GetDataModelTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public GetDataModelTool() {
        super("get_data_model",
                "Lists classes, enumerations, and data types in a layer.",
                ToolCategory.MODEL_READ);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.optionalEnum("layer",
                "Architecture layer: oa, sa, la, pa (all if omitted)",
                VALID_LAYERS, null));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getOptionalString(parameters, "layer", null);

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            List<String> layers = (layer != null && !layer.isBlank())
                    ? List.of(layer.toLowerCase()) : VALID_LAYERS;

            JsonArray results = new JsonArray();

            for (String l : layers) {
                try {
                    BlockArchitecture arch = modelService.getArchitecture(session, l);
                    Iterator<EObject> it = arch.eAllContents();
                    while (it.hasNext()) {
                        EObject obj = it.next();
                        JsonObject item = null;

                        if (obj instanceof Class) {
                            Class cls = (Class) obj;
                            item = new JsonObject();
                            item.addProperty("name", getElementName(cls));
                            item.addProperty("id", getElementId(cls));
                            item.addProperty("type", "class");
                            item.addProperty("layer", l);

                            JsonArray props = new JsonArray();
                            for (org.polarsys.capella.core.data.capellacore.Feature feat : cls.getOwnedFeatures()) {
                                JsonObject propObj = new JsonObject();
                                propObj.addProperty("name", getElementName(feat));
                                propObj.addProperty("type", feat.eClass().getName());
                                props.add(propObj);
                            }
                            item.add("properties", props);
                        } else if (obj instanceof Enumeration) {
                            Enumeration en = (Enumeration) obj;
                            item = new JsonObject();
                            item.addProperty("name", getElementName(en));
                            item.addProperty("id", getElementId(en));
                            item.addProperty("type", "enum");
                            item.addProperty("layer", l);
                            item.add("properties", new JsonArray());
                        } else if (obj instanceof DataType && !(obj instanceof Enumeration)) {
                            DataType dt = (DataType) obj;
                            item = new JsonObject();
                            item.addProperty("name", getElementName(dt));
                            item.addProperty("id", getElementId(dt));
                            String subtype = "datatype";
                            if (dt instanceof StringType) subtype = "string";
                            else if (dt instanceof NumericType) subtype = "numeric";
                            else if (dt instanceof BooleanType) subtype = "boolean";
                            item.addProperty("type", subtype);
                            item.addProperty("layer", l);
                            item.add("properties", new JsonArray());
                        }

                        if (item != null) {
                            results.add(item);
                        }
                    }
                } catch (Exception e) { /* layer may not exist */ }
            }

            JsonObject response = new JsonObject();
            response.addProperty("count", results.size());
            response.add("data_types", results);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to get data model: " + e.getMessage());
        }
    }
}

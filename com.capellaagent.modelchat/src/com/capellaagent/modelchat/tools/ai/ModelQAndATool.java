package com.capellaagent.modelchat.tools.ai;

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
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.FunctionalExchange;
import org.polarsys.capella.core.data.interaction.AbstractCapability;

/**
 * Gathers comprehensive model context for free-form Q&A about the model.
 * The LLM uses this context to answer arbitrary questions about the
 * system architecture.
 */
public class ModelQAndATool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public ModelQAndATool() {
        super("model_q_and_a",
                "Gathers model context for AI-driven Q&A.",
                ToolCategory.AI_INTELLIGENCE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("question",
                "The question about the model to answer"));
        params.add(ToolParameter.optionalEnum("layer",
                "Specific layer to focus on (all if omitted)",
                VALID_LAYERS, null));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String question = getRequiredString(parameters, "question");
        String layer = getOptionalString(parameters, "layer", null);

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();

            List<String> layers = (layer != null && !layer.isBlank())
                    ? List.of(layer.toLowerCase()) : VALID_LAYERS;

            JsonObject modelContext = new JsonObject();
            modelContext.addProperty("question", question);

            for (String l : layers) {
                try {
                    BlockArchitecture arch = modelService.getArchitecture(session, l);
                    JsonObject layerCtx = new JsonObject();

                    JsonArray functions = new JsonArray();
                    JsonArray components = new JsonArray();
                    JsonArray capabilities = new JsonArray();
                    JsonArray exchanges = new JsonArray();

                    int count = 0;
                    Iterator<EObject> it = arch.eAllContents();
                    while (it.hasNext() && count < 200) {
                        EObject obj = it.next();
                        if (obj instanceof AbstractFunction) {
                            String name = getElementName(obj);
                            if (name != null && !name.isBlank() && !name.contains("Root")) {
                                JsonObject f = new JsonObject();
                                f.addProperty("name", name);
                                f.addProperty("description",
                                        truncate(getElementDescription(obj), 100));
                                functions.add(f);
                                count++;
                            }
                        }
                        if (obj instanceof Component) {
                            JsonObject c = new JsonObject();
                            c.addProperty("name", getElementName(obj));
                            c.addProperty("is_actor", ((Component) obj).isActor());
                            components.add(c);
                            count++;
                        }
                        if (obj instanceof AbstractCapability) {
                            JsonObject cap = new JsonObject();
                            cap.addProperty("name", getElementName(obj));
                            capabilities.add(cap);
                            count++;
                        }
                        if (obj instanceof FunctionalExchange) {
                            FunctionalExchange fe = (FunctionalExchange) obj;
                            JsonObject ex = new JsonObject();
                            ex.addProperty("name", getElementName(fe));
                            ex.addProperty("from", fe.getSource() != null
                                    ? getElementName(fe.getSource().eContainer()) : "");
                            ex.addProperty("to", fe.getTarget() != null
                                    ? getElementName(fe.getTarget().eContainer()) : "");
                            exchanges.add(ex);
                            count++;
                        }
                    }

                    layerCtx.add("functions", functions);
                    layerCtx.add("components", components);
                    layerCtx.add("capabilities", capabilities);
                    layerCtx.add("exchanges", exchanges);
                    modelContext.add(l, layerCtx);
                } catch (Exception e) { /* skip */ }
            }

            modelContext.addProperty("answer_prompt",
                    "Using the model context above, answer the question: " + question);

            return ToolResult.success(modelContext);

        } catch (Exception e) {
            return ToolResult.error("Failed to gather Q&A context: " + e.getMessage());
        }
    }
}

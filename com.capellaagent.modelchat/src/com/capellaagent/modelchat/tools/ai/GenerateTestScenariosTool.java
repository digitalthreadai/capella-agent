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
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.FunctionalChain;
import org.polarsys.capella.core.data.fa.FunctionalExchange;
import org.polarsys.capella.core.data.interaction.AbstractCapability;
import org.polarsys.capella.core.data.interaction.Scenario;

/**
 * Gathers capabilities, functional chains, and scenarios to enable
 * AI-driven test scenario generation.
 */
public class GenerateTestScenariosTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public GenerateTestScenariosTool() {
        super("generate_test_scenarios",
                "Gathers model context for AI test scenario generation.",
                ToolCategory.AI_INTELLIGENCE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();
            BlockArchitecture arch = modelService.getArchitecture(session, layer);

            JsonArray capabilities = new JsonArray();
            JsonArray funcChains = new JsonArray();
            JsonArray existingScenarios = new JsonArray();
            JsonArray functions = new JsonArray();

            Iterator<EObject> it = arch.eAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();

                if (obj instanceof AbstractCapability) {
                    AbstractCapability cap = (AbstractCapability) obj;
                    JsonObject c = new JsonObject();
                    c.addProperty("name", getElementName(cap));
                    c.addProperty("id", getElementId(cap));
                    c.addProperty("scenario_count", cap.getOwnedScenarios().size());

                    JsonArray scenarioNames = new JsonArray();
                    for (Scenario s : cap.getOwnedScenarios()) {
                        scenarioNames.add(getElementName(s));
                    }
                    c.add("scenarios", scenarioNames);
                    capabilities.add(c);
                }

                if (obj instanceof FunctionalChain) {
                    FunctionalChain fc = (FunctionalChain) obj;
                    JsonObject f = new JsonObject();
                    f.addProperty("name", getElementName(fc));
                    f.addProperty("id", getElementId(fc));
                    funcChains.add(f);
                }

                if (obj instanceof Scenario) {
                    Scenario s = (Scenario) obj;
                    JsonObject sc = new JsonObject();
                    sc.addProperty("name", getElementName(s));
                    sc.addProperty("kind", s.getKind() != null ? s.getKind().getName() : "");
                    existingScenarios.add(sc);
                }

                if (obj instanceof AbstractFunction) {
                    String name = getElementName(obj);
                    if (name != null && !name.isBlank() && !name.contains("Root")) {
                        JsonObject f = new JsonObject();
                        f.addProperty("name", name);
                        f.addProperty("id", getElementId(obj));
                        functions.add(f);
                    }
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("capability_count", capabilities.size());
            response.addProperty("functional_chain_count", funcChains.size());
            response.addProperty("existing_scenario_count", existingScenarios.size());
            response.addProperty("function_count", functions.size());
            response.add("capabilities", capabilities);
            response.add("functional_chains", funcChains);
            response.add("existing_scenarios", existingScenarios);
            response.add("functions", functions);
            response.addProperty("generation_prompt",
                    "Based on the capabilities, functional chains, and functions, "
                    + "suggest test scenarios that should be created. Include: "
                    + "nominal paths, error paths, and boundary conditions. "
                    + "Use create_scenario to create each one.");
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to gather test scenario context: " + e.getMessage());
        }
    }
}

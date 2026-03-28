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
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.FunctionalChain;
import org.polarsys.capella.core.data.fa.FunctionalChainInvolvement;
import org.polarsys.capella.core.data.fa.FunctionalExchange;
import org.polarsys.capella.core.data.interaction.AbstractCapability;
import org.polarsys.capella.core.data.interaction.InstanceRole;
import org.polarsys.capella.core.data.interaction.Scenario;
import org.polarsys.capella.core.data.interaction.SequenceMessage;

/**
 * Gathers model context from functional scenarios to enable AI-driven
 * test case generation.
 * <p>
 * Extracts:
 * <ul>
 *   <li>Functional chains with their step sequences</li>
 *   <li>Scenarios with message sequences and instance roles</li>
 *   <li>Functions with their input/output exchanges</li>
 *   <li>Capabilities and their associated scenarios</li>
 * </ul>
 * The structured output enables the LLM to generate test cases covering
 * nominal paths, error paths, and boundary conditions.
 */
public class GenerateTestCasesTool extends AbstractCapellaTool {

    private static final List<String> VALID_LAYERS = List.of("oa", "sa", "la", "pa");

    public GenerateTestCasesTool() {
        super("generate_test_cases",
                "Gathers scenario/functional chain context for AI test case generation. "
                + "Returns structured data for the LLM to create test cases.",
                ToolCategory.AI_INTELLIGENCE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("layer",
                "Architecture layer: oa, sa, la, pa",
                VALID_LAYERS));
        params.add(ToolParameter.optionalString("capability_name",
                "Filter to a specific capability by name (optional)"));
        params.add(ToolParameter.optionalBoolean("include_exchange_details",
                "Include detailed exchange item info in function context (default: true)"));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        String capabilityFilter = getOptionalString(parameters, "capability_name", null);
        boolean includeExchangeDetails = getOptionalBoolean(
                parameters, "include_exchange_details", true);

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();
            BlockArchitecture arch = modelService.getArchitecture(session, layer);

            JsonArray capabilitiesArray = new JsonArray();
            JsonArray funcChainsArray = new JsonArray();
            JsonArray scenariosArray = new JsonArray();
            JsonArray functionsArray = new JsonArray();

            Iterator<EObject> it = arch.eAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();

                // Capabilities
                if (obj instanceof AbstractCapability) {
                    AbstractCapability cap = (AbstractCapability) obj;
                    String capName = getElementName(cap);

                    if (capabilityFilter != null && !capabilityFilter.isBlank()) {
                        if (capName == null || !capName.toLowerCase()
                                .contains(capabilityFilter.toLowerCase())) {
                            continue;
                        }
                    }

                    JsonObject capObj = new JsonObject();
                    capObj.addProperty("name", capName);
                    capObj.addProperty("id", getElementId(cap));
                    capObj.addProperty("description",
                            truncate(getElementDescription(cap), 300));

                    // Scenarios within this capability
                    JsonArray capScenarios = new JsonArray();
                    for (Scenario scenario : cap.getOwnedScenarios()) {
                        JsonObject scenarioObj = buildScenarioObject(scenario);
                        capScenarios.add(scenarioObj);
                        scenariosArray.add(scenarioObj);
                    }
                    capObj.add("scenarios", capScenarios);
                    capObj.addProperty("scenario_count", capScenarios.size());
                    capabilitiesArray.add(capObj);
                }

                // Functional chains
                if (obj instanceof FunctionalChain) {
                    FunctionalChain fc = (FunctionalChain) obj;
                    JsonObject fcObj = new JsonObject();
                    fcObj.addProperty("name", getElementName(fc));
                    fcObj.addProperty("id", getElementId(fc));
                    fcObj.addProperty("description",
                            truncate(getElementDescription(fc), 300));

                    // Steps in the chain
                    JsonArray steps = new JsonArray();
                    int stepIndex = 0;
                    for (FunctionalChainInvolvement involvement : fc.getOwnedFunctionalChainInvolvements()) {
                        EObject involved = involvement.getInvolved();
                        if (involved != null) {
                            JsonObject step = new JsonObject();
                            step.addProperty("step_index", stepIndex++);
                            step.addProperty("involved_name", getElementName(involved));
                            step.addProperty("involved_type", involved.eClass().getName());
                            step.addProperty("involved_id", getElementId(involved));
                            steps.add(step);
                        }
                    }
                    fcObj.add("steps", steps);
                    fcObj.addProperty("step_count", steps.size());
                    funcChainsArray.add(fcObj);
                }

                // Functions with exchange details
                if (obj instanceof AbstractFunction) {
                    AbstractFunction func = (AbstractFunction) obj;
                    String funcName = getElementName(func);
                    if (funcName == null || funcName.isBlank() || funcName.contains("Root")) continue;

                    JsonObject funcObj = new JsonObject();
                    funcObj.addProperty("name", funcName);
                    funcObj.addProperty("id", getElementId(func));
                    funcObj.addProperty("description",
                            truncate(getElementDescription(func), 200));

                    if (includeExchangeDetails) {
                        // Input exchanges
                        JsonArray inputs = new JsonArray();
                        for (var incoming : func.getIncoming()) {
                            if (incoming instanceof FunctionalExchange) {
                                FunctionalExchange fe = (FunctionalExchange) incoming;
                                JsonObject input = new JsonObject();
                                input.addProperty("exchange_name", getElementName(fe));
                                input.addProperty("exchange_id", getElementId(fe));

                                // Source function
                                EObject sourcePort = fe.getSource();
                                if (sourcePort != null && sourcePort.eContainer() instanceof AbstractFunction) {
                                    input.addProperty("from_function",
                                            getElementName(sourcePort.eContainer()));
                                }

                                // Exchange items
                                JsonArray items = new JsonArray();
                                try {
                                    for (var ei : fe.getExchangedItems()) {
                                        items.add(getElementName(ei));
                                    }
                                } catch (Exception e) { /* skip */ }
                                if (items.size() > 0) {
                                    input.add("exchange_items", items);
                                }
                                inputs.add(input);
                            }
                        }
                        funcObj.add("inputs", inputs);

                        // Output exchanges
                        JsonArray outputs = new JsonArray();
                        for (var outgoing : func.getOutgoing()) {
                            if (outgoing instanceof FunctionalExchange) {
                                FunctionalExchange fe = (FunctionalExchange) outgoing;
                                JsonObject output = new JsonObject();
                                output.addProperty("exchange_name", getElementName(fe));
                                output.addProperty("exchange_id", getElementId(fe));

                                EObject targetPort = fe.getTarget();
                                if (targetPort != null && targetPort.eContainer() instanceof AbstractFunction) {
                                    output.addProperty("to_function",
                                            getElementName(targetPort.eContainer()));
                                }

                                JsonArray items = new JsonArray();
                                try {
                                    for (var ei : fe.getExchangedItems()) {
                                        items.add(getElementName(ei));
                                    }
                                } catch (Exception e) { /* skip */ }
                                if (items.size() > 0) {
                                    output.add("exchange_items", items);
                                }
                                outputs.add(output);
                            }
                        }
                        funcObj.add("outputs", outputs);
                    }

                    funcObj.addProperty("input_count", func.getIncoming().size());
                    funcObj.addProperty("output_count", func.getOutgoing().size());
                    functionsArray.add(funcObj);
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("layer", layer);
            response.addProperty("capability_count", capabilitiesArray.size());
            response.addProperty("functional_chain_count", funcChainsArray.size());
            response.addProperty("scenario_count", scenariosArray.size());
            response.addProperty("function_count", functionsArray.size());
            response.add("capabilities", capabilitiesArray);
            response.add("functional_chains", funcChainsArray);
            response.add("functions", functionsArray);

            response.addProperty("generation_prompt",
                    "Based on the capabilities, functional chains, scenarios, and functions, "
                    + "generate structured test cases. For each test case include:\n"
                    + "- Test case ID and name\n"
                    + "- Preconditions\n"
                    + "- Test steps with expected results\n"
                    + "- Postconditions\n"
                    + "- Test category: nominal, error, boundary, performance\n"
                    + "- Priority: critical, high, medium, low\n"
                    + "- Traceability to capability/functional chain");

            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed to gather test case context: " + e.getMessage());
        }
    }

    /**
     * Builds a JSON object for a Scenario including its messages and roles.
     */
    private JsonObject buildScenarioObject(Scenario scenario) {
        JsonObject scenarioObj = new JsonObject();
        scenarioObj.addProperty("name", getElementName(scenario));
        scenarioObj.addProperty("id", getElementId(scenario));
        scenarioObj.addProperty("kind",
                scenario.getKind() != null ? scenario.getKind().getName() : "");

        // Instance roles (participants)
        JsonArray roles = new JsonArray();
        for (InstanceRole role : scenario.getOwnedInstanceRoles()) {
            JsonObject roleObj = new JsonObject();
            roleObj.addProperty("name", getElementName(role));
            EObject represented = role.getRepresentedInstance();
            if (represented != null) {
                roleObj.addProperty("represents", getElementName(represented));
                roleObj.addProperty("represents_type", represented.eClass().getName());
            }
            roles.add(roleObj);
        }
        scenarioObj.add("participants", roles);

        // Messages (sequence)
        JsonArray messages = new JsonArray();
        int msgIndex = 0;
        for (SequenceMessage msg : scenario.getOwnedMessages()) {
            JsonObject msgObj = new JsonObject();
            msgObj.addProperty("index", msgIndex++);
            msgObj.addProperty("name", getElementName(msg));
            msgObj.addProperty("kind",
                    msg.getKind() != null ? msg.getKind().getName() : "");

            // Source and target roles
            if (msg.getSendingEnd() != null && msg.getSendingEnd().getCovered() != null) {
                msgObj.addProperty("from",
                        getElementName(msg.getSendingEnd().getCovered()));
            }
            if (msg.getReceivingEnd() != null && msg.getReceivingEnd().getCovered() != null) {
                msgObj.addProperty("to",
                        getElementName(msg.getReceivingEnd().getCovered()));
            }
            messages.add(msgObj);
        }
        scenarioObj.add("messages", messages);
        scenarioObj.addProperty("message_count", messages.size());

        return scenarioObj;
    }
}

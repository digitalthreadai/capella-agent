package com.capellaagent.simulation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.tools.IToolDescriptor;
import com.capellaagent.core.tools.IToolExecutor;
import com.capellaagent.simulation.bridge.ISimulationEngine;
import com.capellaagent.simulation.bridge.MatlabCommandBridge;
import com.capellaagent.simulation.bridge.MatlabEngineBridge;
import com.capellaagent.simulation.config.SimulationPreferences;
import com.capellaagent.simulation.orchestrator.ParameterExtractor;
import com.capellaagent.simulation.orchestrator.ResultPropagator;
import com.capellaagent.simulation.orchestrator.SimulationOrchestrator;
import com.capellaagent.simulation.orchestrator.WhatIfManager;
import com.capellaagent.simulation.tools.ExtractParamsTool;
import com.capellaagent.simulation.tools.GetSimStatusTool;
import com.capellaagent.simulation.tools.ListEnginesTool;
import com.capellaagent.simulation.tools.PropagateResultsTool;
import com.capellaagent.simulation.tools.RunSimulationTool;
import com.capellaagent.simulation.tools.WhatIfTool;

/**
 * Registers all simulation tools with the core ToolRegistry.
 * <p>
 * Manages the simulation engine registry and creates tool instances with
 * their required service dependencies.
 * <p>
 * <b>Note:</b> This registrar is only invoked when the system property
 * {@code capellaagent.simulation.enabled=true} is set (see {@link SimActivator}).
 * The simulation bundle is placeholder scaffolding — engines return fabricated
 * results and do not connect to a real MATLAB or simulation back-end in this build.
 * All tool descriptions are prefixed with {@code [SIMULATION NOT FUNCTIONAL IN THIS BUILD]}
 * so the LLM can inform the user accordingly.
 */
public class SimToolRegistrar {

    private static final String STUB_PREFIX = "[SIMULATION NOT FUNCTIONAL IN THIS BUILD] ";

    /**
     * Holder for a paired tool descriptor and executor.
     */
    public record ToolEntry(IToolDescriptor descriptor, IToolExecutor executor) {
    }

    private final List<ToolEntry> registeredTools = new ArrayList<>();
    private final Map<String, ISimulationEngine> engineRegistry = new HashMap<>();

    /**
     * Registers all simulation tools.
     */
    public void registerAll() {
        // Register available simulation engines
        registerEngine(new MatlabEngineBridge());
        registerEngine(new MatlabCommandBridge());

        // Create services
        SimulationPreferences preferences = new SimulationPreferences();
        ParameterExtractor extractor = new ParameterExtractor();
        ResultPropagator propagator = new ResultPropagator();
        SimulationOrchestrator orchestrator = new SimulationOrchestrator(
                engineRegistry, extractor, propagator);
        WhatIfManager whatIfManager = new WhatIfManager(orchestrator);

        // Create and register tools
        registerTool(new ListEnginesTool(engineRegistry));
        registerTool(new ExtractParamsTool(extractor));
        registerTool(new RunSimulationTool(orchestrator, engineRegistry));
        registerTool(new PropagateResultsTool(propagator));
        registerTool(new WhatIfTool(whatIfManager, engineRegistry));
        registerTool(new GetSimStatusTool());
    }

    /**
     * Unregisters all tools and disconnects all engines.
     */
    public void unregisterAll() {
        registeredTools.clear();
        for (ISimulationEngine engine : engineRegistry.values()) {
            try {
                engine.disconnect();
            } catch (Exception e) {
                // Log but do not throw during shutdown
            }
        }
        engineRegistry.clear();
    }

    /**
     * Returns the list of registered tool entries.
     *
     * @return an unmodifiable view of registered tools
     */
    public List<ToolEntry> getRegisteredTools() {
        return List.copyOf(registeredTools);
    }

    /**
     * Returns the engine registry.
     *
     * @return an unmodifiable view of registered engines
     */
    public Map<String, ISimulationEngine> getEngineRegistry() {
        return Map.copyOf(engineRegistry);
    }

    private void registerEngine(ISimulationEngine engine) {
        engineRegistry.put(engine.getId(), engine);
    }

    private <T extends IToolDescriptor & IToolExecutor> void registerTool(T tool) {
        // Wrap the descriptor to prefix the description with the stub warning
        IToolDescriptor prefixedDescriptor = new IToolDescriptor() {
            @Override public String getName() { return tool.getName(); }
            @Override public String getDescription() {
                return STUB_PREFIX + tool.getDescription();
            }
            @Override public String getCategory() { return tool.getCategory(); }
            @Override public com.google.gson.JsonObject getParametersSchema() {
                return tool.getParametersSchema();
            }
        };
        registeredTools.add(new ToolEntry(prefixedDescriptor, tool));
    }
}

package com.capellaagent.modelchat;

import com.capellaagent.core.tools.ToolRegistry;
import com.capellaagent.modelchat.tools.transition.ReconcileLayersTool;
import com.capellaagent.modelchat.tools.transition.TransitionLaToPaTool;
import com.capellaagent.modelchat.tools.transition.TransitionOaToSaTool;
import com.capellaagent.modelchat.tools.transition.AutoTransitionAllTool;
import com.capellaagent.modelchat.tools.transition.TransitionPaToEpbsTool;
import com.capellaagent.modelchat.tools.transition.TransitionSaToLaTool;
import com.capellaagent.modelchat.tools.transition.TransitionFunctionsTool;

/**
 * Registers all transition tools with the core {@link ToolRegistry}.
 * Delegated from {@link ModelChatToolRegistrar#registerAll()}.
 */
final class TransitionToolRegistrar {

    private TransitionToolRegistrar() {}

    static void registerAll(ToolRegistry registry, ModelChatToolRegistrar parent) {
        parent.reg(registry, new TransitionOaToSaTool());
        parent.reg(registry, new TransitionSaToLaTool());
        parent.reg(registry, new TransitionLaToPaTool());
        parent.reg(registry, new ReconcileLayersTool());
        parent.reg(registry, new TransitionFunctionsTool());
        // P3 transition tools
        parent.reg(registry, new TransitionPaToEpbsTool());
        parent.reg(registry, new AutoTransitionAllTool());
    }
}

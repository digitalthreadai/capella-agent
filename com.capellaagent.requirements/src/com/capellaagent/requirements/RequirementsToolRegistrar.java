package com.capellaagent.requirements;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolRegistry;
import com.capellaagent.requirements.tools.CoverageDashboardTool;
import com.capellaagent.requirements.tools.ImportReqifTool;
import com.capellaagent.requirements.tools.ImportRequirementsExcelTool;
import com.capellaagent.requirements.tools.LinkRequirementsToElementsTool;

/**
 * Registers requirement-related tools with the core {@link ToolRegistry}.
 * <p>
 * If the Requirements Viewpoint bundle ({@code org.polarsys.kitalpha.vp.requirements})
 * is absent, write-side tools are still registered but will return a structured error
 * explaining how to enable the viewpoint. Read-only tools ({@link CoverageDashboardTool})
 * always register unconditionally.
 */
public class RequirementsToolRegistrar {

    private static final Logger LOG = Logger.getLogger(RequirementsToolRegistrar.class.getName());

    private final List<AbstractCapellaTool> registered = new ArrayList<>();

    public void registerAll() {
        ToolRegistry registry = ToolRegistry.getInstance();
        reg(registry, new ImportReqifTool());
        reg(registry, new ImportRequirementsExcelTool());
        reg(registry, new LinkRequirementsToElementsTool());
        reg(registry, new CoverageDashboardTool());
    }

    private void reg(ToolRegistry registry, AbstractCapellaTool tool) {
        try {
            registry.register(tool);
            registered.add(tool);
        } catch (Exception e) {
            LOG.warning("Failed to register requirements tool: " + tool.getName()
                    + " - " + e.getMessage());
        }
    }

    public void unregisterAll() {
        ToolRegistry registry = ToolRegistry.getInstance();
        for (AbstractCapellaTool tool : registered) {
            try {
                registry.unregister(tool.getName());
            } catch (Exception ignored) {
                // Best-effort unregistration on shutdown
            }
        }
        registered.clear();
    }
}

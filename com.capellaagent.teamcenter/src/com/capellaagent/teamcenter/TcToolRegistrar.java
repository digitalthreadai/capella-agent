package com.capellaagent.teamcenter;

import java.util.ArrayList;
import java.util.List;

import com.capellaagent.core.tools.IToolDescriptor;
import com.capellaagent.core.tools.IToolExecutor;
import com.capellaagent.teamcenter.client.TcConfiguration;
import com.capellaagent.teamcenter.client.TcRestClient;
import com.capellaagent.teamcenter.api.TcBomService;
import com.capellaagent.teamcenter.api.TcObjectService;
import com.capellaagent.teamcenter.api.TcRequirementsService;
import com.capellaagent.teamcenter.api.TcSearchService;
import com.capellaagent.teamcenter.import_.PartImporter;
import com.capellaagent.teamcenter.import_.RequirementImporter;
import com.capellaagent.teamcenter.import_.TcToCapellaMapper;
import com.capellaagent.teamcenter.tools.TcGetBomTool;
import com.capellaagent.teamcenter.tools.TcGetObjectTool;
import com.capellaagent.teamcenter.tools.TcImportPartTool;
import com.capellaagent.teamcenter.tools.TcImportRequirementTool;
import com.capellaagent.teamcenter.tools.TcLinkTool;
import com.capellaagent.teamcenter.tools.TcListRequirementsTool;
import com.capellaagent.teamcenter.tools.TcSearchTool;

/**
 * Registers all Teamcenter tools with the core ToolRegistry.
 * <p>
 * Each tool is instantiated with its required service dependencies and
 * registered as both an {@link IToolDescriptor} and {@link IToolExecutor}.
 */
public class TcToolRegistrar {

    /**
     * Holder for a paired tool descriptor and executor.
     */
    public record ToolEntry(IToolDescriptor descriptor, IToolExecutor executor) {
    }

    private final List<ToolEntry> registeredTools = new ArrayList<>();
    private TcRestClient restClient;

    /**
     * Registers all Teamcenter tools.
     * <p>
     * Creates the shared REST client and service instances, then instantiates
     * each tool with the appropriate dependencies.
     */
    public void registerAll() {
        TcConfiguration config = new TcConfiguration();
        restClient = new TcRestClient(config);

        // API services
        TcSearchService searchService = new TcSearchService(restClient);
        TcObjectService objectService = new TcObjectService(restClient);
        TcBomService bomService = new TcBomService(restClient);
        TcRequirementsService reqService = new TcRequirementsService(restClient);

        // Import services
        TcToCapellaMapper mapper = new TcToCapellaMapper();
        RequirementImporter reqImporter = new RequirementImporter(mapper);
        PartImporter partImporter = new PartImporter(mapper);

        // Create and register tools
        registerTool(new TcSearchTool(searchService));
        registerTool(new TcGetObjectTool(objectService));
        registerTool(new TcGetBomTool(bomService));
        registerTool(new TcListRequirementsTool(reqService));
        registerTool(new TcImportRequirementTool(reqService, reqImporter));
        registerTool(new TcImportPartTool(objectService, partImporter));
        registerTool(new TcLinkTool(objectService));
    }

    /**
     * Unregisters all tools previously registered by this registrar.
     */
    public void unregisterAll() {
        registeredTools.clear();
        if (restClient != null) {
            restClient.logout();
            restClient = null;
        }
    }

    /**
     * Returns the list of registered tool entries.
     *
     * @return an unmodifiable view of registered tools
     */
    public List<ToolEntry> getRegisteredTools() {
        return List.copyOf(registeredTools);
    }

    private <T extends IToolDescriptor & IToolExecutor> void registerTool(T tool) {
        registeredTools.add(new ToolEntry(tool, tool));
    }
}

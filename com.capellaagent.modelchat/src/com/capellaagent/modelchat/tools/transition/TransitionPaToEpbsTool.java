package com.capellaagent.modelchat.tools.transition;

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
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.epbs.ConfigurationItem;
import org.polarsys.capella.core.data.epbs.EPBSArchitecture;
import org.polarsys.capella.core.data.epbs.EpbsFactory;
import org.polarsys.capella.core.data.pa.PhysicalComponent;
import org.polarsys.capella.core.data.pa.PhysicalComponentNature;

/**
 * Creates EPBS ConfigurationItems for PA physical (node) components.
 * Simplified transition: creates a CI for each node component not yet mapped.
 */
public class TransitionPaToEpbsTool extends AbstractCapellaTool {

    public TransitionPaToEpbsTool() {
        super("transition_pa_to_epbs",
                "Creates EPBS config items from PA node components.",
                ToolCategory.TRANSITION);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        return List.of();
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();
            BlockArchitecture paArch = modelService.getArchitecture(session, "pa");

            // Find EPBS architecture
            EPBSArchitecture epbs = null;
            var se = modelService.getSystemEngineering(session);
            for (EObject arch : se.getOwnedArchitectures()) {
                if (arch instanceof EPBSArchitecture) {
                    epbs = (EPBSArchitecture) arch;
                    break;
                }
            }

            if (epbs == null) {
                return ToolResult.error("No EPBS architecture found. Model may not have EPBS layer.");
            }

            // Collect node components from PA
            List<PhysicalComponent> nodeComps = new ArrayList<>();
            Iterator<EObject> it = paArch.eAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();
                if (obj instanceof PhysicalComponent) {
                    PhysicalComponent pc = (PhysicalComponent) obj;
                    if (pc.getNature() == PhysicalComponentNature.NODE) {
                        nodeComps.add(pc);
                    }
                }
            }

            if (nodeComps.isEmpty()) {
                return ToolResult.successMessage("No node components found in PA to transition.");
            }

            final EPBSArchitecture finalEpbs = epbs;
            final int[] createdCount = {0};
            JsonArray createdItems = new JsonArray();

            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain,
                    "Transition PA to EPBS") {
                @Override
                protected void doExecute() {
                    // Get or create the CI pkg
                    var ciPkg = finalEpbs.getOwnedConfigurationItemPkg();
                    for (PhysicalComponent pc : nodeComps) {
                        ConfigurationItem ci = EpbsFactory.eINSTANCE.createConfigurationItem();
                        ci.setName(getElementName(pc));
                        if (ciPkg != null) {
                            ciPkg.getOwnedConfigurationItems().add(ci);
                        }
                        createdCount[0]++;

                        JsonObject item = new JsonObject();
                        item.addProperty("name", getElementName(pc));
                        item.addProperty("ci_uuid", getElementId(ci));
                        item.addProperty("source_uuid", getElementId(pc));
                        createdItems.add(item);
                    }
                }
            });

            modelService.invalidateCache(session);

            JsonObject response = new JsonObject();
            response.addProperty("status", "transitioned");
            response.addProperty("created_count", createdCount[0]);
            response.add("configuration_items", createdItems);
            return ToolResult.success(response);

        } catch (Exception e) {
            return ToolResult.error("Failed PA to EPBS transition: " + e.getMessage());
        }
    }
}

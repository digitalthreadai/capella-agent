package com.capellaagent.modelchat.tools.write;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.capellaagent.core.capella.CapellaModelService;
import com.capellaagent.core.security.InputValidator;
import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.cs.Component;
import org.polarsys.capella.core.data.cs.Interface;
import org.polarsys.capella.core.data.cs.CsFactory;
import org.polarsys.capella.core.data.fa.ComponentPort;
import org.polarsys.capella.core.data.fa.FaFactory;
import org.polarsys.capella.core.data.fa.FunctionPort;
import org.polarsys.capella.core.data.fa.FunctionalExchange;
import org.polarsys.capella.core.data.fa.ComponentExchange;
import org.polarsys.capella.core.data.fa.OrientationPortKind;
import org.polarsys.capella.core.data.information.ExchangeItem;
import org.polarsys.capella.core.data.information.InformationFactory;
import org.polarsys.capella.core.data.information.ExchangeMechanism;
import org.polarsys.capella.core.data.la.LaFactory;
import org.polarsys.capella.core.data.la.LogicalComponent;
import org.polarsys.capella.core.data.la.LogicalArchitecture;
import org.polarsys.capella.core.data.la.LogicalFunction;

/**
 * Applies a predefined architecture pattern to a target component or layer.
 * <p>
 * Supported patterns:
 * <ul>
 *   <li><b>standard_interface</b> - Creates a standard interface pattern with
 *       provided/required ports on a component</li>
 *   <li><b>observer</b> - Creates an observer pattern with event publisher
 *       and subscriber components</li>
 *   <li><b>mediator</b> - Creates a mediator component that centralizes
 *       communication between existing components</li>
 *   <li><b>layered</b> - Creates a layered decomposition with presentation,
 *       logic, and data sub-components</li>
 * </ul>
 */
public class ApplyPatternTool extends AbstractCapellaTool {

    private static final List<String> VALID_PATTERNS = List.of(
            "standard_interface", "observer", "mediator", "layered");
    private static final List<String> VALID_LAYERS = List.of("sa", "la", "pa");

    public ApplyPatternTool() {
        super("apply_pattern",
                "Applies a predefined architecture pattern (standard_interface, observer, "
                + "mediator, layered) to a component or layer.",
                ToolCategory.MODEL_WRITE);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredEnum("pattern",
                "Pattern to apply: standard_interface, observer, mediator, layered",
                VALID_PATTERNS));
        params.add(ToolParameter.requiredEnum("layer",
                "Target architecture layer: sa, la, pa",
                VALID_LAYERS));
        params.add(ToolParameter.optionalString("target_uuid",
                "UUID of the target component (required for standard_interface; "
                + "optional for others which create new components)"));
        params.add(ToolParameter.optionalString("name_prefix",
                "Prefix for generated element names (default: 'Pattern')"));
        return params;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String patternName = getRequiredString(parameters, "pattern").toLowerCase();
        String layer = getRequiredString(parameters, "layer").toLowerCase();
        String targetUuid = getOptionalString(parameters, "target_uuid", null);
        String namePrefix = getOptionalString(parameters, "name_prefix", "Pattern");

        try {
            namePrefix = InputValidator.sanitizeName(namePrefix);
        } catch (Exception e) {
            namePrefix = "Pattern";
        }

        try {
            Session session = getActiveSession();
            CapellaModelService modelService = getModelService();
            BlockArchitecture arch = modelService.getArchitecture(session, layer);

            EObject targetComponent = null;
            if (targetUuid != null && !targetUuid.isBlank()) {
                try {
                    targetUuid = InputValidator.validateUuid(targetUuid);
                } catch (IllegalArgumentException e) {
                    return ToolResult.error("Invalid UUID: " + e.getMessage());
                }
                targetComponent = resolveElementByUuid(targetUuid);
                if (targetComponent == null) {
                    return ToolResult.error("Target component not found: " + targetUuid);
                }
            }

            switch (patternName) {
                case "standard_interface":
                    return applyStandardInterfacePattern(session, modelService, arch,
                            targetComponent, namePrefix);
                case "observer":
                    return applyObserverPattern(session, modelService, arch, namePrefix);
                case "mediator":
                    return applyMediatorPattern(session, modelService, arch, namePrefix);
                case "layered":
                    return applyLayeredPattern(session, modelService, arch,
                            targetComponent, namePrefix);
                default:
                    return ToolResult.error("Unknown pattern: " + patternName);
            }

        } catch (Exception e) {
            return ToolResult.error("Failed to apply pattern: " + e.getMessage());
        }
    }

    /**
     * Standard interface pattern: creates provided/required ports and an interface
     * on a target component.
     */
    @SuppressWarnings("unchecked")
    private ToolResult applyStandardInterfacePattern(Session session,
            CapellaModelService modelService, BlockArchitecture arch,
            EObject targetComponent, String namePrefix) {

        if (targetComponent == null) {
            return ToolResult.error("standard_interface pattern requires a target_uuid (component)");
        }
        if (!(targetComponent instanceof Component)) {
            return ToolResult.error("Target must be a Component, got: "
                    + targetComponent.eClass().getName());
        }

        Component comp = (Component) targetComponent;
        final JsonArray created = new JsonArray();

        TransactionalEditingDomain domain;
        try {
            domain = getEditingDomain(session);
        } catch (Exception e) {
            return ToolResult.error("No editing domain: " + e.getMessage());
        }

        domain.getCommandStack().execute(new RecordingCommand(domain,
                "Apply standard interface pattern") {
            @Override
            protected void doExecute() {
                // Create an interface
                Interface iface = CsFactory.eINSTANCE.createInterface();
                iface.setName(namePrefix + "_Interface");

                // Add interface to the architecture's interface package
                EStructuralFeature intfPkgFeature =
                        arch.eClass().getEStructuralFeature("ownedInterfacePkg");
                if (intfPkgFeature != null) {
                    EObject intfPkg = (EObject) arch.eGet(intfPkgFeature);
                    if (intfPkg != null) {
                        EStructuralFeature ownedIntfs =
                                intfPkg.eClass().getEStructuralFeature("ownedInterfaces");
                        if (ownedIntfs != null && ownedIntfs.isMany()) {
                            ((List<EObject>) intfPkg.eGet(ownedIntfs)).add(iface);
                        }
                    }
                }

                JsonObject ifaceEntry = new JsonObject();
                ifaceEntry.addProperty("type", "Interface");
                ifaceEntry.addProperty("name", iface.getName());
                ifaceEntry.addProperty("id", getElementId(iface));
                created.add(ifaceEntry);

                // Create provided port
                ComponentPort providedPort = FaFactory.eINSTANCE.createComponentPort();
                providedPort.setName(namePrefix + "_ProvidedPort");
                providedPort.setOrientation(OrientationPortKind.OUT);
                try {
                    comp.getOwnedFeatures().add(providedPort);
                    providedPort.getProvidedInterfaces().add(iface);

                    JsonObject portEntry = new JsonObject();
                    portEntry.addProperty("type", "ComponentPort (provided)");
                    portEntry.addProperty("name", providedPort.getName());
                    portEntry.addProperty("id", getElementId(providedPort));
                    created.add(portEntry);
                } catch (Exception e) {
                    // Port creation may vary by component type
                }

                // Create required port
                ComponentPort requiredPort = FaFactory.eINSTANCE.createComponentPort();
                requiredPort.setName(namePrefix + "_RequiredPort");
                requiredPort.setOrientation(OrientationPortKind.IN);
                try {
                    comp.getOwnedFeatures().add(requiredPort);
                    requiredPort.getRequiredInterfaces().add(iface);

                    JsonObject portEntry = new JsonObject();
                    portEntry.addProperty("type", "ComponentPort (required)");
                    portEntry.addProperty("name", requiredPort.getName());
                    portEntry.addProperty("id", getElementId(requiredPort));
                    created.add(portEntry);
                } catch (Exception e) {
                    // Port creation may vary
                }
            }
        });

        modelService.invalidateCache(session);

        JsonObject response = new JsonObject();
        response.addProperty("status", "pattern_applied");
        response.addProperty("pattern", "standard_interface");
        response.addProperty("target_component", getElementName(comp));
        response.addProperty("elements_created", created.size());
        response.add("created_elements", created);
        return ToolResult.success(response);
    }

    /**
     * Observer pattern: creates a publisher component, subscriber component,
     * and an event exchange between them.
     */
    @SuppressWarnings("unchecked")
    private ToolResult applyObserverPattern(Session session,
            CapellaModelService modelService, BlockArchitecture arch,
            String namePrefix) {

        final JsonArray created = new JsonArray();

        TransactionalEditingDomain domain;
        try {
            domain = getEditingDomain(session);
        } catch (Exception e) {
            return ToolResult.error("No editing domain: " + e.getMessage());
        }

        domain.getCommandStack().execute(new RecordingCommand(domain,
                "Apply observer pattern") {
            @Override
            protected void doExecute() {
                // Find component package
                EStructuralFeature compPkgFeature =
                        arch.eClass().getEStructuralFeature("ownedLogicalComponentPkg");
                if (compPkgFeature == null) {
                    compPkgFeature = arch.eClass().getEStructuralFeature("ownedSystemComponentPkg");
                }
                if (compPkgFeature == null) {
                    compPkgFeature = arch.eClass().getEStructuralFeature("ownedPhysicalComponentPkg");
                }

                EObject compPkg = compPkgFeature != null ? (EObject) arch.eGet(compPkgFeature) : null;
                if (compPkg == null) return;

                // Create publisher component
                LogicalComponent publisher = LaFactory.eINSTANCE.createLogicalComponent();
                publisher.setName(namePrefix + "_EventPublisher");

                // Create subscriber component
                LogicalComponent subscriber = LaFactory.eINSTANCE.createLogicalComponent();
                subscriber.setName(namePrefix + "_EventSubscriber");

                // Add to component package
                try {
                    java.lang.reflect.Method method =
                            compPkg.getClass().getMethod("getOwnedLogicalComponents");
                    Object result = method.invoke(compPkg);
                    if (result instanceof List) {
                        ((List<EObject>) result).add(publisher);
                        ((List<EObject>) result).add(subscriber);
                    }
                } catch (Exception e) {
                    // Try generic approach
                    for (EReference ref : compPkg.eClass().getEAllContainments()) {
                        if (ref.isMany() && ref.getEReferenceType().isInstance(publisher)) {
                            ((List<EObject>) compPkg.eGet(ref)).add(publisher);
                            ((List<EObject>) compPkg.eGet(ref)).add(subscriber);
                            break;
                        }
                    }
                }

                // Create event exchange between publisher and subscriber
                ComponentPort pubPort = FaFactory.eINSTANCE.createComponentPort();
                pubPort.setName(namePrefix + "_EventOut");
                pubPort.setOrientation(OrientationPortKind.OUT);
                publisher.getOwnedFeatures().add(pubPort);

                ComponentPort subPort = FaFactory.eINSTANCE.createComponentPort();
                subPort.setName(namePrefix + "_EventIn");
                subPort.setOrientation(OrientationPortKind.IN);
                subscriber.getOwnedFeatures().add(subPort);

                ComponentExchange exchange = FaFactory.eINSTANCE.createComponentExchange();
                exchange.setName(namePrefix + "_EventNotification");
                exchange.setSource(pubPort);
                exchange.setTarget(subPort);
                publisher.getOwnedComponentExchanges().add(exchange);

                JsonObject pubEntry = new JsonObject();
                pubEntry.addProperty("type", "LogicalComponent");
                pubEntry.addProperty("name", publisher.getName());
                pubEntry.addProperty("role", "EventPublisher");
                pubEntry.addProperty("id", getElementId(publisher));
                created.add(pubEntry);

                JsonObject subEntry = new JsonObject();
                subEntry.addProperty("type", "LogicalComponent");
                subEntry.addProperty("name", subscriber.getName());
                subEntry.addProperty("role", "EventSubscriber");
                subEntry.addProperty("id", getElementId(subscriber));
                created.add(subEntry);

                JsonObject exchEntry = new JsonObject();
                exchEntry.addProperty("type", "ComponentExchange");
                exchEntry.addProperty("name", exchange.getName());
                exchEntry.addProperty("id", getElementId(exchange));
                created.add(exchEntry);
            }
        });

        modelService.invalidateCache(session);

        JsonObject response = new JsonObject();
        response.addProperty("status", "pattern_applied");
        response.addProperty("pattern", "observer");
        response.addProperty("elements_created", created.size());
        response.add("created_elements", created);
        return ToolResult.success(response);
    }

    /**
     * Mediator pattern: creates a mediator component with exchanges to existing components.
     */
    @SuppressWarnings("unchecked")
    private ToolResult applyMediatorPattern(Session session,
            CapellaModelService modelService, BlockArchitecture arch,
            String namePrefix) {

        final JsonArray created = new JsonArray();

        // Collect existing components
        List<Component> existingComponents = new ArrayList<>();
        java.util.Iterator<EObject> it = arch.eAllContents();
        while (it.hasNext()) {
            EObject obj = it.next();
            if (obj instanceof Component && !((Component) obj).isActor()) {
                existingComponents.add((Component) obj);
            }
        }

        if (existingComponents.isEmpty()) {
            return ToolResult.error("No existing components found to mediate");
        }

        TransactionalEditingDomain domain;
        try {
            domain = getEditingDomain(session);
        } catch (Exception e) {
            return ToolResult.error("No editing domain: " + e.getMessage());
        }

        domain.getCommandStack().execute(new RecordingCommand(domain,
                "Apply mediator pattern") {
            @Override
            protected void doExecute() {
                // Create mediator component
                LogicalComponent mediator = LaFactory.eINSTANCE.createLogicalComponent();
                mediator.setName(namePrefix + "_Mediator");
                mediator.setDescription("Mediator component: centralizes communication "
                        + "between " + existingComponents.size() + " components");

                // Add mediator to same container as first component
                EObject container = existingComponents.get(0).eContainer();
                if (container != null) {
                    EReference containmentRef = existingComponents.get(0).eContainmentFeature();
                    if (containmentRef != null && containmentRef.isMany()) {
                        try {
                            ((List<EObject>) container.eGet(containmentRef)).add(mediator);
                        } catch (Exception e) {
                            return;
                        }
                    }
                }

                JsonObject medEntry = new JsonObject();
                medEntry.addProperty("type", "LogicalComponent");
                medEntry.addProperty("name", mediator.getName());
                medEntry.addProperty("role", "Mediator");
                medEntry.addProperty("id", getElementId(mediator));
                created.add(medEntry);

                // Create exchange to each existing component
                for (Component comp : existingComponents) {
                    ComponentPort medPort = FaFactory.eINSTANCE.createComponentPort();
                    medPort.setName("To_" + getElementName(comp));
                    medPort.setOrientation(OrientationPortKind.INOUT);
                    mediator.getOwnedFeatures().add(medPort);

                    ComponentExchange exchange = FaFactory.eINSTANCE.createComponentExchange();
                    exchange.setName(namePrefix + "_MediatorTo_" + getElementName(comp));
                    exchange.setSource(medPort);

                    // Find or create a port on the target component
                    ComponentPort targetPort = FaFactory.eINSTANCE.createComponentPort();
                    targetPort.setName("FromMediator");
                    targetPort.setOrientation(OrientationPortKind.INOUT);
                    comp.getOwnedFeatures().add(targetPort);

                    exchange.setTarget(targetPort);
                    mediator.getOwnedComponentExchanges().add(exchange);

                    JsonObject exchEntry = new JsonObject();
                    exchEntry.addProperty("type", "ComponentExchange");
                    exchEntry.addProperty("name", exchange.getName());
                    exchEntry.addProperty("to_component", getElementName(comp));
                    exchEntry.addProperty("id", getElementId(exchange));
                    created.add(exchEntry);
                }
            }
        });

        modelService.invalidateCache(session);

        JsonObject response = new JsonObject();
        response.addProperty("status", "pattern_applied");
        response.addProperty("pattern", "mediator");
        response.addProperty("existing_components", existingComponents.size());
        response.addProperty("elements_created", created.size());
        response.add("created_elements", created);
        return ToolResult.success(response);
    }

    /**
     * Layered pattern: creates sub-components for presentation, logic, and data layers.
     */
    @SuppressWarnings("unchecked")
    private ToolResult applyLayeredPattern(Session session,
            CapellaModelService modelService, BlockArchitecture arch,
            EObject targetComponent, String namePrefix) {

        final JsonArray created = new JsonArray();

        // If no target, create a new root-level component
        TransactionalEditingDomain domain;
        try {
            domain = getEditingDomain(session);
        } catch (Exception e) {
            return ToolResult.error("No editing domain: " + e.getMessage());
        }

        domain.getCommandStack().execute(new RecordingCommand(domain,
                "Apply layered pattern") {
            @Override
            protected void doExecute() {
                // Determine parent for new sub-components
                EObject parent = targetComponent;
                if (parent == null) {
                    // Create a container component
                    LogicalComponent container = LaFactory.eINSTANCE.createLogicalComponent();
                    container.setName(namePrefix + "_System");

                    EStructuralFeature compPkgFeature =
                            arch.eClass().getEStructuralFeature("ownedLogicalComponentPkg");
                    if (compPkgFeature == null) {
                        compPkgFeature = arch.eClass().getEStructuralFeature("ownedSystemComponentPkg");
                    }
                    EObject compPkg = compPkgFeature != null
                            ? (EObject) arch.eGet(compPkgFeature) : null;
                    if (compPkg != null) {
                        for (EReference ref : compPkg.eClass().getEAllContainments()) {
                            if (ref.isMany() && ref.getEReferenceType().isInstance(container)) {
                                ((List<EObject>) compPkg.eGet(ref)).add(container);
                                break;
                            }
                        }
                    }

                    parent = container;
                    JsonObject containerEntry = new JsonObject();
                    containerEntry.addProperty("type", "LogicalComponent");
                    containerEntry.addProperty("name", container.getName());
                    containerEntry.addProperty("role", "Container");
                    containerEntry.addProperty("id", getElementId(container));
                    created.add(containerEntry);
                }

                // Create three layered sub-components
                String[] layerNames = {"Presentation", "BusinessLogic", "DataAccess"};
                String[] layerDescs = {
                        "Handles UI/external interfaces and user interaction",
                        "Contains core business logic and processing rules",
                        "Manages data persistence and retrieval"
                };

                List<LogicalComponent> layers = new ArrayList<>();
                for (int i = 0; i < layerNames.length; i++) {
                    LogicalComponent subComp = LaFactory.eINSTANCE.createLogicalComponent();
                    subComp.setName(namePrefix + "_" + layerNames[i]);
                    subComp.setDescription(layerDescs[i]);

                    // Add as sub-component
                    if (parent instanceof LogicalComponent) {
                        ((LogicalComponent) parent).getOwnedLogicalComponents().add(subComp);
                    }

                    layers.add(subComp);

                    JsonObject entry = new JsonObject();
                    entry.addProperty("type", "LogicalComponent");
                    entry.addProperty("name", subComp.getName());
                    entry.addProperty("role", layerNames[i]);
                    entry.addProperty("id", getElementId(subComp));
                    created.add(entry);
                }

                // Create exchanges between adjacent layers
                for (int i = 0; i < layers.size() - 1; i++) {
                    LogicalComponent upper = layers.get(i);
                    LogicalComponent lower = layers.get(i + 1);

                    ComponentPort upperPort = FaFactory.eINSTANCE.createComponentPort();
                    upperPort.setName("To_" + lower.getName());
                    upperPort.setOrientation(OrientationPortKind.OUT);
                    upper.getOwnedFeatures().add(upperPort);

                    ComponentPort lowerPort = FaFactory.eINSTANCE.createComponentPort();
                    lowerPort.setName("From_" + upper.getName());
                    lowerPort.setOrientation(OrientationPortKind.IN);
                    lower.getOwnedFeatures().add(lowerPort);

                    ComponentExchange exchange = FaFactory.eINSTANCE.createComponentExchange();
                    exchange.setName(upper.getName() + "_to_" + lower.getName());
                    exchange.setSource(upperPort);
                    exchange.setTarget(lowerPort);

                    if (parent instanceof Component) {
                        ((Component) parent).getOwnedComponentExchanges().add(exchange);
                    }

                    JsonObject exchEntry = new JsonObject();
                    exchEntry.addProperty("type", "ComponentExchange");
                    exchEntry.addProperty("name", exchange.getName());
                    exchEntry.addProperty("id", getElementId(exchange));
                    created.add(exchEntry);
                }
            }
        });

        modelService.invalidateCache(session);

        JsonObject response = new JsonObject();
        response.addProperty("status", "pattern_applied");
        response.addProperty("pattern", "layered");
        response.addProperty("elements_created", created.size());
        response.add("created_elements", created);
        return ToolResult.success(response);
    }
}

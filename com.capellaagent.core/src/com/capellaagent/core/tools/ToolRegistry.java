package com.capellaagent.core.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Thread-safe singleton registry for tool registrations.
 * <p>
 * Tools are registered by agent bundles at activation time. The registry
 * provides lookup, execution, and schema conversion for LLM integration.
 */
public final class ToolRegistry {

    private static final Logger LOG = Logger.getLogger(ToolRegistry.class.getName());

    private static final ToolRegistry INSTANCE = new ToolRegistry();

    private final ConcurrentHashMap<String, ToolRegistration> tools = new ConcurrentHashMap<>();

    private ToolRegistry() {
        // Singleton
    }

    /**
     * Returns the singleton instance.
     *
     * @return the tool registry instance
     */
    public static ToolRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a tool with its descriptor and executor.
     *
     * @param descriptor the tool's metadata
     * @param executor   the tool's execution logic
     * @throws IllegalArgumentException if a tool with the same name is already registered
     */
    public void register(IToolDescriptor descriptor, IToolExecutor executor) {
        ToolRegistration registration = new ToolRegistration(descriptor, executor);
        ToolRegistration previous = tools.putIfAbsent(descriptor.getName(), registration);
        if (previous != null) {
            throw new IllegalArgumentException(
                    "Tool already registered: " + descriptor.getName());
        }
        LOG.info("Registered tool: " + descriptor.getName() +
                " [" + descriptor.getCategory() + "]");
    }

    /**
     * Convenience method to register a tool that implements both
     * {@link IToolDescriptor} and {@link IToolExecutor} (e.g., {@link AbstractCapellaTool}).
     *
     * @param tool the tool instance implementing both interfaces
     * @throws IllegalArgumentException if a tool with the same name is already registered
     */
    public <T extends IToolDescriptor & IToolExecutor> void register(T tool) {
        register((IToolDescriptor) tool, (IToolExecutor) tool);
    }

    /**
     * Unregisters a tool by name.
     *
     * @param name the tool name to unregister
     * @return true if the tool was found and removed
     */
    public boolean unregister(String name) {
        ToolRegistration removed = tools.remove(name);
        if (removed != null) {
            LOG.info("Unregistered tool: " + name);
        }
        return removed != null;
    }

    /**
     * Executes a tool by name with the given arguments.
     *
     * @param name the tool function name
     * @param args the arguments as a JsonObject
     * @return the execution result as a JsonObject
     * @throws ToolExecutionException if the tool is not found or execution fails
     */
    public JsonObject execute(String name, JsonObject args) throws ToolExecutionException {
        ToolRegistration registration = tools.get(name);
        if (registration == null) {
            throw new ToolExecutionException(ToolExecutionException.ERR_NOT_FOUND,
                    "Tool not found: " + name);
        }

        LOG.fine("Executing tool: " + name + " with args: " + args);
        try {
            JsonObject result = registration.getExecutor().execute(args);
            LOG.fine("Tool " + name + " executed successfully");
            return result;
        } catch (ToolExecutionException e) {
            LOG.log(Level.WARNING, "Tool " + name + " execution failed: " + e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unexpected error executing tool " + name, e);
            throw new ToolExecutionException(ToolExecutionException.ERR_EXECUTION,
                    "Unexpected error executing tool " + name + ": " + e.getMessage(), e);
        }
    }

    /**
     * Executes a tool by name with the given JSON arguments string.
     * <p>
     * This is the preferred entry point for the ChatJob orchestration loop,
     * which receives tool arguments as a raw JSON string from the LLM.
     *
     * @param name    the tool function name
     * @param argsJson the arguments as a JSON string
     * @return the execution result as a ToolResult
     */
    public ToolResult executeTool(String name, String argsJson) {
        try {
            JsonObject args = argsJson != null && !argsJson.isBlank()
                    ? com.google.gson.JsonParser.parseString(argsJson).getAsJsonObject()
                    : new JsonObject();
            JsonObject result = execute(name, args);
            return ToolResult.success(result);
        } catch (ToolExecutionException e) {
            LOG.log(Level.WARNING, "Tool " + name + " failed: " + e.getMessage(), e);
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unexpected error executing tool " + name, e);
            return ToolResult.error("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Returns tool descriptors filtered by one or more categories.
     * <p>
     * If no categories are specified, all tools are returned.
     *
     * @param categories zero or more category strings to filter by
     * @return an unmodifiable list of matching tool descriptors
     */
    public List<IToolDescriptor> getTools(String... categories) {
        if (categories == null || categories.length == 0) {
            List<IToolDescriptor> all = new ArrayList<>();
            for (ToolRegistration reg : tools.values()) {
                all.add(reg.getDescriptor());
            }
            return Collections.unmodifiableList(all);
        }

        List<String> categoryList = Arrays.asList(categories);
        List<IToolDescriptor> filtered = new ArrayList<>();
        for (ToolRegistration reg : tools.values()) {
            if (categoryList.contains(reg.getDescriptor().getCategory())) {
                filtered.add(reg.getDescriptor());
            }
        }
        return Collections.unmodifiableList(filtered);
    }

    /**
     * Converts tool descriptors to a JSON array suitable for sending to an LLM.
     * <p>
     * Each tool is represented as a JSON object with "name", "description",
     * and "input_schema" (or "parameters") fields. The exact format depends on
     * the LLM provider, so this provides a provider-neutral format that providers
     * can adapt.
     *
     * @param categories zero or more category strings to filter by
     * @return a JsonArray of tool schema objects
     */
    public JsonArray getToolsForLlm(String... categories) {
        List<IToolDescriptor> descriptors = getTools(categories);
        JsonArray toolArray = new JsonArray();

        for (IToolDescriptor descriptor : descriptors) {
            JsonObject tool = new JsonObject();
            tool.addProperty("name", descriptor.getName());
            tool.addProperty("description", descriptor.getDescription());
            tool.add("input_schema", descriptor.getParametersSchema());
            toolArray.add(tool);
        }

        return toolArray;
    }

    /**
     * Returns the total number of registered tools.
     *
     * @return the count of registered tools
     */
    public int size() {
        return tools.size();
    }

    /**
     * Checks whether a tool with the given name is registered.
     *
     * @param name the tool name
     * @return true if the tool exists
     */
    public boolean hasToolTool(String name) {
        return tools.containsKey(name);
    }

    /**
     * Removes all registered tools. Intended for testing only.
     */
    public void clear() {
        tools.clear();
    }
}

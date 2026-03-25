package com.capellaagent.core.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;

import com.capellaagent.core.security.AuditLogger;
import com.capellaagent.core.security.SecurityService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Abstract base class for Capella model tools.
 * <p>
 * Supports two coding patterns for tool implementations:
 * <ul>
 *   <li><b>New pattern (recommended):</b> Subclass uses the 3-arg constructor,
 *       overrides {@link #defineParameters()} and {@link #executeInternal(Map)}.
 *       Parameters are extracted via {@link #getRequiredString(Map, String)}, etc.
 *       Returns {@link ToolResult}.</li>
 *   <li><b>Legacy pattern:</b> Subclass uses the default constructor,
 *       overrides {@link #doExecute(JsonObject)}, and works with raw JSON.
 *       Returns {@link JsonObject}.</li>
 * </ul>
 * <p>
 * Both patterns share the same infrastructure: session management, transaction
 * wrapping, security checks, element helpers, audit logging, and result builders.
 */
public abstract class AbstractCapellaTool implements IToolDescriptor, IToolExecutor {

    private static final Logger LOG = Logger.getLogger(AbstractCapellaTool.class.getName());

    // -- Instance fields for the new pattern (3-arg constructor) --
    private final String toolName;
    private final String toolDescription;
    private final String toolCategory;

    // ========================================================================
    // Constructors
    // ========================================================================

    /**
     * Default constructor for the legacy pattern (subclass implements getName()
     * etc. directly or overrides doExecute).
     */
    protected AbstractCapellaTool() {
        this.toolName = null;
        this.toolDescription = null;
        this.toolCategory = null;
    }

    /**
     * Constructor for the new pattern. Stores the tool's name, description,
     * and category so {@link IToolDescriptor} methods return them directly.
     *
     * @param name        the tool function name (e.g., "list_elements")
     * @param description human-readable description for the LLM
     * @param category    the tool category (use {@link ToolCategory} constants)
     */
    protected AbstractCapellaTool(String name, String description, String category) {
        this.toolName = name;
        this.toolDescription = description;
        this.toolCategory = category;
    }

    // ========================================================================
    // IToolDescriptor implementation
    // ========================================================================

    @Override
    public String getName() {
        return toolName != null ? toolName : getClass().getSimpleName();
    }

    @Override
    public String getDescription() {
        return toolDescription != null ? toolDescription : "";
    }

    @Override
    public String getCategory() {
        return toolCategory != null ? toolCategory : "capella.model";
    }

    @Override
    public JsonObject getParametersSchema() {
        List<ToolParameter> params = defineParameters();
        if (params == null || params.isEmpty()) {
            JsonObject empty = new JsonObject();
            empty.addProperty("type", "object");
            empty.add("properties", new JsonObject());
            return empty;
        }

        JsonObject properties = new JsonObject();
        JsonArray required = new JsonArray();

        for (ToolParameter param : params) {
            JsonObject prop = new JsonObject();
            prop.addProperty("type", param.getType());
            prop.addProperty("description", param.getDescription());
            properties.add(param.getName(), prop);

            if (param.isRequired()) {
                required.add(param.getName());
            }
        }

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        if (required.size() > 0) {
            schema.add("required", required);
        }
        return schema;
    }

    // ========================================================================
    // Execution: bridges both patterns
    // ========================================================================

    /**
     * New pattern: subclasses override this to define their parameters.
     * Default returns empty list (for legacy tools).
     */
    protected List<ToolParameter> defineParameters() {
        return List.of();
    }

    /**
     * New pattern: subclasses override this with their tool logic.
     * Parameters are already extracted from JSON into a typed Map.
     *
     * @param parameters the tool parameters as a name-value map
     * @return the tool result
     */
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        // Default: not overridden, will fall through to doExecute path
        return null;
    }

    /**
     * Legacy pattern: subclasses override this with raw JsonObject access.
     *
     * @param args the raw arguments from the LLM
     * @return a JsonObject result
     * @throws ToolExecutionException if execution fails
     */
    protected JsonObject doExecute(JsonObject args) throws ToolExecutionException {
        // Default: not overridden, will fall through to executeInternal path
        return null;
    }

    /**
     * Main entry point called by the ToolRegistry. Routes to either
     * {@link #executeInternal(Map)} (new pattern) or {@link #doExecute(JsonObject)}
     * (legacy pattern), wrapping with error handling and audit logging.
     */
    @Override
    public JsonObject execute(JsonObject arguments) {
        try {
            AuditLogger.getInstance().logToolExecution(
                    getName(), arguments.toString(), true, "started");

            JsonObject result;

            // Try the new pattern first (executeInternal with Map parameters)
            Map<String, Object> paramMap = jsonToMap(arguments);
            ToolResult toolResult = executeInternal(paramMap);

            if (toolResult != null) {
                // New pattern was used
                result = toolResult.toJson();
            } else {
                // Fall back to legacy pattern
                result = doExecute(arguments);
                if (result == null) {
                    result = errorResult(ToolExecutionException.ERR_EXECUTION,
                            "Tool " + getName() + " did not produce a result. "
                            + "Override executeInternal() or doExecute().");
                }
            }

            AuditLogger.getInstance().logToolExecution(
                    getName(), arguments.toString(), true, "completed");
            return result;

        } catch (ToolExecutionException e) {
            LOG.log(Level.WARNING, "Tool execution failed: " + getName(), e);
            AuditLogger.getInstance().logToolExecution(
                    getName(), arguments.toString(), false, e.getMessage());
            return errorResult(e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unexpected error in tool: " + getName(), e);
            AuditLogger.getInstance().logToolExecution(
                    getName(), arguments.toString(), false, e.getMessage());
            return errorResult(ToolExecutionException.ERR_EXECUTION,
                    "Unexpected error: " + e.getMessage());
        }
    }

    // ========================================================================
    // Parameter Extraction Helpers (Map-based, for new pattern)
    // ========================================================================

    /**
     * Gets a required string parameter from the parameter map.
     *
     * @throws IllegalArgumentException if the parameter is missing or blank
     */
    protected String getRequiredString(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Required parameter '" + key + "' is missing");
        }
        String str = value.toString();
        if (str.isBlank()) {
            throw new IllegalArgumentException("Required parameter '" + key + "' must not be blank");
        }
        return str;
    }

    /**
     * Gets an optional string parameter with a default value.
     */
    protected String getOptionalString(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        String str = value.toString();
        return str.isEmpty() ? defaultValue : str;
    }

    /**
     * Gets an optional integer parameter with a default value.
     */
    protected int getOptionalInt(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets a required boolean parameter from the parameter map.
     *
     * @throws IllegalArgumentException if the parameter is missing
     */
    protected boolean getRequiredBoolean(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Required parameter '" + key + "' is missing");
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    /**
     * Gets an optional boolean parameter with a default value.
     */
    protected boolean getOptionalBoolean(Map<String, Object> params, String key, boolean defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    // ========================================================================
    // Parameter Extraction Helpers (JsonObject-based, for legacy pattern)
    // ========================================================================

    /**
     * Validates that a required string argument is present and non-empty.
     */
    protected String requireString(JsonObject args, String paramName) throws ToolExecutionException {
        if (!args.has(paramName) || args.get(paramName).isJsonNull()) {
            throw new ToolExecutionException(ToolExecutionException.ERR_INVALID_ARGS,
                    "Required parameter '" + paramName + "' is missing");
        }
        String value = args.get(paramName).getAsString();
        if (value.isBlank()) {
            throw new ToolExecutionException(ToolExecutionException.ERR_INVALID_ARGS,
                    "Required parameter '" + paramName + "' must not be blank");
        }
        return value;
    }

    /**
     * Gets an optional string argument with a default value.
     */
    protected String optionalString(JsonObject args, String paramName, String defaultValue) {
        if (args.has(paramName) && !args.get(paramName).isJsonNull()) {
            return args.get(paramName).getAsString();
        }
        return defaultValue;
    }

    // ========================================================================
    // Session & Domain Helpers
    // ========================================================================

    /**
     * Gets the active Sirius session from the workspace.
     * <p>
     * PLACEHOLDER: The exact API call depends on the Capella/Sirius version.
     */
    protected Object getActiveSession() throws ToolExecutionException {
        // PLACEHOLDER: Actual implementation depends on Capella/Sirius version.
        // Session session = CapellaSessionUtil.getActiveSession();
        // if (session == null) throw ...;
        // return session;
        throw new ToolExecutionException(ToolExecutionException.ERR_EXECUTION,
                "getActiveSession() not yet connected to Capella runtime. "
                + "Override in concrete tool or configure CapellaSessionUtil.");
    }

    /**
     * Gets the transactional editing domain from a Sirius session.
     * <p>
     * PLACEHOLDER: The exact API call depends on the Capella/Sirius version.
     *
     * @param session the Sirius session (typed as Object for compile-time safety)
     */
    protected TransactionalEditingDomain getEditingDomain(Object session)
            throws ToolExecutionException {
        // PLACEHOLDER: return ((Session) session).getTransactionalEditingDomain();
        throw new ToolExecutionException(ToolExecutionException.ERR_EXECUTION,
                "getEditingDomain(session) not yet connected to Capella runtime.");
    }

    /**
     * Gets the transactional editing domain from the active session (no-arg convenience).
     * <p>
     * PLACEHOLDER: Calls getActiveSession() then getEditingDomain(session).
     */
    protected TransactionalEditingDomain getEditingDomain() throws ToolExecutionException {
        Object session = getActiveSession();
        return getEditingDomain(session);
    }

    /**
     * Checks that the agent is in READ_WRITE mode.
     */
    protected void requireWriteMode() throws ToolExecutionException {
        SecurityService.getInstance().requireWriteMode();
    }

    /**
     * Executes a runnable within an EMF transaction.
     */
    protected void executeInTransaction(Object session, String label, Runnable work)
            throws ToolExecutionException {
        try {
            TransactionalEditingDomain domain = getEditingDomain(session);
            domain.getCommandStack().execute(new RecordingCommand(domain, label) {
                @Override
                protected void doExecute() {
                    work.run();
                }
            });
        } catch (ToolExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolExecutionException(ToolExecutionException.ERR_TRANSACTION,
                    "Transaction failed (" + label + "): " + e.getMessage(), e);
        }
    }

    // ========================================================================
    // Element Helpers
    // ========================================================================

    /**
     * Gets the name of an EMF element by reading its "name" structural feature.
     *
     * @param element the EMF element
     * @return the name, or empty string if the element has no name feature
     */
    protected String getElementName(EObject element) {
        if (element == null) return "";
        EStructuralFeature nameFeature = element.eClass().getEStructuralFeature("name");
        if (nameFeature != null) {
            Object value = element.eGet(nameFeature);
            return value != null ? value.toString() : "";
        }
        return "";
    }

    /**
     * Gets the description of an EMF element by reading its "description" feature.
     *
     * @param element the EMF element
     * @return the description, or empty string if none
     */
    protected String getElementDescription(EObject element) {
        if (element == null) return "";
        EStructuralFeature descFeature = element.eClass().getEStructuralFeature("description");
        if (descFeature != null) {
            Object value = element.eGet(descFeature);
            return value != null ? value.toString() : "";
        }
        return "";
    }

    /**
     * Gets a unique identifier for an EMF element (URI fragment or resource ID).
     *
     * @param element the EMF element
     * @return the ID string, or empty string if unavailable
     */
    protected String getElementId(EObject element) {
        if (element == null) return "";
        // Try URI fragment first
        if (element.eResource() != null) {
            return element.eResource().getURIFragment(element);
        }
        // PLACEHOLDER: For Capella elements, try getId() or getSid()
        // if (element instanceof CapellaElement) {
        //     return ((CapellaElement) element).getId();
        // }
        return "";
    }

    /**
     * Truncates a string to the specified maximum length, appending "..." if truncated.
     *
     * @param text      the text to truncate (null-safe)
     * @param maxLength the maximum length
     * @return the truncated string
     */
    protected String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    /**
     * Resolves a model element by its UUID within the active session.
     * <p>
     * PLACEHOLDER: The actual implementation searches all resources in the
     * active Sirius session for an element matching the given UUID.
     *
     * @param uuid the element UUID
     * @return the resolved EObject, or null if not found
     */
    protected EObject resolveElementByUuid(String uuid) {
        // PLACEHOLDER: Actual implementation:
        // Session session = getActiveSession();
        // for (Resource r : session.getSemanticResources()) {
        //     EObject obj = r.getEObject(uuid);
        //     if (obj != null) return obj;
        // }
        LOG.warning("resolveElementByUuid() is a PLACEHOLDER. UUID: " + uuid);
        return null;
    }

    /**
     * Serializes an EMF EObject into a JsonObject with basic metadata.
     */
    protected JsonObject serializeElement(EObject element) {
        JsonObject json = new JsonObject();
        json.addProperty("type", element.eClass().getName());
        json.addProperty("name", getElementName(element));
        json.addProperty("id", getElementId(element));
        return json;
    }

    // ========================================================================
    // Audit Logging
    // ========================================================================

    /**
     * Logs an audit entry for this tool's action.
     */
    protected void auditLog(String action, JsonObject details) {
        AuditLogger.getInstance().log(getName() + "." + action, details);
    }

    // ========================================================================
    // Result Builders
    // ========================================================================

    /**
     * Creates a success result JSON object.
     */
    protected JsonObject successResult(String message) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "success");
        result.addProperty("message", message);
        return result;
    }

    /**
     * Creates a success result with additional data.
     */
    protected JsonObject successResult(String message, JsonObject data) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "success");
        result.addProperty("message", message);
        result.add("data", data);
        return result;
    }

    /**
     * Creates an error result JSON object.
     */
    protected JsonObject errorResult(String errorCode, String message) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "error");
        result.addProperty("errorCode", errorCode);
        result.addProperty("message", message);
        return result;
    }

    // ========================================================================
    // JSON ↔ Map Conversion
    // ========================================================================

    /**
     * Converts a JsonObject to a Map for use with the new executeInternal pattern.
     */
    private Map<String, Object> jsonToMap(JsonObject json) {
        Map<String, Object> map = new HashMap<>();
        if (json == null) return map;
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            JsonElement val = entry.getValue();
            if (val.isJsonNull()) {
                map.put(entry.getKey(), null);
            } else if (val.isJsonPrimitive()) {
                if (val.getAsJsonPrimitive().isNumber()) {
                    // Try integer first, fallback to double
                    try {
                        map.put(entry.getKey(), val.getAsInt());
                    } catch (NumberFormatException e) {
                        map.put(entry.getKey(), val.getAsDouble());
                    }
                } else if (val.getAsJsonPrimitive().isBoolean()) {
                    map.put(entry.getKey(), val.getAsBoolean());
                } else {
                    map.put(entry.getKey(), val.getAsString());
                }
            } else {
                // For nested objects/arrays, pass the raw JSON string
                map.put(entry.getKey(), val.toString());
            }
        }
        return map;
    }
}

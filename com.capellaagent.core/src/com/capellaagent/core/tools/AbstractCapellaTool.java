package com.capellaagent.core.tools;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
// PLACEHOLDER: Import depends on Capella version
// import org.eclipse.sirius.business.api.session.Session;
// import org.eclipse.sirius.business.api.session.SessionManager;

import com.capellaagent.core.security.AuditLogger;
import com.capellaagent.core.security.SecurityService;
import com.capellaagent.core.util.CapellaSessionUtil;
import com.google.gson.JsonObject;

/**
 * Abstract base class for Capella model tools.
 * <p>
 * Provides common infrastructure for tools that interact with the Capella model:
 * <ul>
 *   <li>Session and editing domain acquisition</li>
 *   <li>Security checks (read-only vs read-write mode)</li>
 *   <li>Transactional command execution</li>
 *   <li>Element serialization for LLM responses</li>
 *   <li>Audit logging</li>
 *   <li>Structured error/success result builders</li>
 * </ul>
 * <p>
 * Subclasses implement {@link #doExecute(JsonObject)} with their tool-specific logic.
 * The base {@link #execute(JsonObject)} method wraps this with error handling.
 */
public abstract class AbstractCapellaTool implements IToolDescriptor, IToolExecutor {

    private static final Logger LOG = Logger.getLogger(AbstractCapellaTool.class.getName());

    /**
     * Subclasses implement this method with their tool-specific execution logic.
     *
     * @param args the validated arguments from the LLM
     * @return a JsonObject containing the tool's result
     * @throws ToolExecutionException if the tool execution fails
     */
    protected abstract JsonObject doExecute(JsonObject args) throws ToolExecutionException;

    /**
     * Executes the tool, wrapping the subclass implementation with error handling
     * and audit logging.
     *
     * @param arguments the arguments from the LLM
     * @return a JsonObject containing either the result or an error
     */
    @Override
    public JsonObject execute(JsonObject arguments) {
        try {
            AuditLogger.getInstance().logToolExecution(
                    getName(), arguments.toString(), true, "started");

            JsonObject result = doExecute(arguments);

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

    // -- Session & Domain Helpers --

    /**
     * Gets the active Sirius session from the workspace.
     * <p>
     * PLACEHOLDER: The exact API call depends on the Capella/Sirius version in use.
     *
     * @return the active Sirius Session
     * @throws ToolExecutionException if no session is active
     */
    protected Object getActiveSession() throws ToolExecutionException {
        // PLACEHOLDER: Actual implementation depends on Capella/Sirius version.
        // Example:
        // Session session = CapellaSessionUtil.getActiveSession();
        // if (session == null) {
        //     throw new ToolExecutionException(ToolExecutionException.ERR_NOT_FOUND,
        //             "No active Capella session. Open a .aird file first.");
        // }
        // return session;
        throw new ToolExecutionException(ToolExecutionException.ERR_EXECUTION,
                "getActiveSession() not yet connected to Capella runtime. " +
                "Override in concrete tool or configure CapellaSessionUtil.");
    }

    /**
     * Gets the transactional editing domain from a Sirius session.
     * <p>
     * PLACEHOLDER: The exact API call depends on the Capella/Sirius version.
     *
     * @param session the Sirius session (typed as Object for compile-time safety)
     * @return the TransactionalEditingDomain
     * @throws ToolExecutionException if the domain cannot be obtained
     */
    protected TransactionalEditingDomain getEditingDomain(Object session)
            throws ToolExecutionException {
        // PLACEHOLDER: Actual implementation:
        // return ((Session) session).getTransactionalEditingDomain();
        throw new ToolExecutionException(ToolExecutionException.ERR_EXECUTION,
                "getEditingDomain() not yet connected to Capella runtime.");
    }

    /**
     * Checks that the agent is in READ_WRITE mode. Throws if in READ_ONLY mode.
     *
     * @throws ToolExecutionException if the agent is in read-only mode
     */
    protected void requireWriteMode() throws ToolExecutionException {
        SecurityService.getInstance().requireWriteMode();
    }

    /**
     * Executes a runnable within an EMF transaction.
     *
     * @param session the Sirius session (typed as Object for flexibility)
     * @param label   a human-readable label for the transaction (shown in undo history)
     * @param work    the work to execute inside the transaction
     * @throws ToolExecutionException if the transaction fails
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

    /**
     * Serializes an EMF EObject into a JsonObject with basic metadata.
     * <p>
     * Extracts the element's name (if it has one), its EClass type name,
     * and its URI fragment as a unique identifier.
     *
     * @param element the EMF element to serialize
     * @return a JsonObject with "type", "name", and "id" fields
     */
    protected JsonObject serializeElement(EObject element) {
        JsonObject json = new JsonObject();

        // Type (EClass name)
        json.addProperty("type", element.eClass().getName());

        // Name (look for a "name" feature)
        org.eclipse.emf.ecore.EStructuralFeature nameFeature =
                element.eClass().getEStructuralFeature("name");
        if (nameFeature != null) {
            Object nameValue = element.eGet(nameFeature);
            json.addProperty("name", nameValue != null ? nameValue.toString() : "");
        }

        // ID (URI fragment)
        if (element.eResource() != null) {
            String fragment = element.eResource().getURIFragment(element);
            json.addProperty("id", fragment);
        }

        // PLACEHOLDER: For Capella elements, also extract:
        // - element.getId() if it implements CapellaElement
        // - element.getSid() for semantic ID
        // - element.getDescription() for summary

        return json;
    }

    /**
     * Logs an audit entry for this tool's action.
     *
     * @param action  the action being performed (e.g., "create_component")
     * @param details structured details about the action
     */
    protected void auditLog(String action, JsonObject details) {
        AuditLogger.getInstance().log(getName() + "." + action, details);
    }

    // -- Result Builders --

    /**
     * Creates a success result JSON object.
     *
     * @param message a human-readable success message
     * @return a JsonObject with status "success" and the message
     */
    protected JsonObject successResult(String message) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "success");
        result.addProperty("message", message);
        return result;
    }

    /**
     * Creates a success result with additional data.
     *
     * @param message a human-readable success message
     * @param data    additional structured data to include
     * @return a JsonObject with status "success", message, and data
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
     *
     * @param errorCode a machine-readable error code
     * @param message   a human-readable error message
     * @return a JsonObject with status "error", the error code, and message
     */
    protected JsonObject errorResult(String errorCode, String message) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "error");
        result.addProperty("errorCode", errorCode);
        result.addProperty("message", message);
        return result;
    }

    /**
     * Validates that a required string argument is present and non-empty.
     *
     * @param args      the arguments object
     * @param paramName the parameter name to check
     * @return the string value
     * @throws ToolExecutionException if the parameter is missing or empty
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
     *
     * @param args         the arguments object
     * @param paramName    the parameter name
     * @param defaultValue the default to return if absent
     * @return the string value or default
     */
    protected String optionalString(JsonObject args, String paramName, String defaultValue) {
        if (args.has(paramName) && !args.get(paramName).isJsonNull()) {
            return args.get(paramName).getAsString();
        }
        return defaultValue;
    }
}

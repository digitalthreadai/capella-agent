package com.capellaagent.core.tools;

/**
 * Exception thrown when a tool execution fails.
 * <p>
 * Carries a machine-readable error code that can be included in the tool
 * result sent back to the LLM, helping it understand what went wrong and
 * potentially retry or adjust its approach.
 */
public class ToolExecutionException extends Exception {

    private static final long serialVersionUID = 1L;

    /** Error code for invalid or missing arguments. */
    public static final String ERR_INVALID_ARGS = "TOOL_INVALID_ARGS";

    /** Error code for permission denied (e.g., write in read-only mode). */
    public static final String ERR_PERMISSION_DENIED = "TOOL_PERMISSION_DENIED";

    /** Error code for element not found in the model. */
    public static final String ERR_NOT_FOUND = "TOOL_NOT_FOUND";

    /** Error code for transaction failures. */
    public static final String ERR_TRANSACTION = "TOOL_TRANSACTION_ERROR";

    /** Error code for generic execution failures. */
    public static final String ERR_EXECUTION = "TOOL_EXECUTION_ERROR";

    private final String errorCode;

    /**
     * Constructs a new tool execution exception.
     *
     * @param errorCode a machine-readable error code
     * @param message   a human-readable description of the failure
     */
    public ToolExecutionException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Constructs a new tool execution exception with a cause.
     *
     * @param errorCode a machine-readable error code
     * @param message   a human-readable description of the failure
     * @param cause     the underlying cause
     */
    public ToolExecutionException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Returns the machine-readable error code.
     *
     * @return the error code string
     */
    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return "ToolExecutionException{errorCode='" + errorCode + "', message='" + getMessage() + "'}";
    }
}

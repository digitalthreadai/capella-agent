package com.capellaagent.core.llm;

/**
 * Checked exception thrown by LLM provider operations.
 * <p>
 * Carries a machine-readable error code alongside the human-readable message,
 * enabling callers to implement structured error handling.
 */
public class LlmException extends Exception {

    private static final long serialVersionUID = 1L;

    /** Error code for authentication failures (invalid or missing API key). */
    public static final String ERR_AUTHENTICATION = "LLM_AUTH_FAILED";

    /** Error code for rate limiting by the provider. */
    public static final String ERR_RATE_LIMITED = "LLM_RATE_LIMITED";

    /** Error code for provider connectivity issues. */
    public static final String ERR_CONNECTION = "LLM_CONNECTION_ERROR";

    /** Error code for invalid request parameters. */
    public static final String ERR_INVALID_REQUEST = "LLM_INVALID_REQUEST";

    /** Error code for response parsing failures. */
    public static final String ERR_PARSE = "LLM_PARSE_ERROR";

    /** Error code for provider not found. */
    public static final String ERR_PROVIDER_NOT_FOUND = "LLM_PROVIDER_NOT_FOUND";

    /** Error code for generic/unclassified errors. */
    public static final String ERR_UNKNOWN = "LLM_UNKNOWN_ERROR";

    private final String errorCode;

    /**
     * Constructs a new LLM exception.
     *
     * @param errorCode a machine-readable error code (use the ERR_ constants)
     * @param message   a human-readable description of the error
     */
    public LlmException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Constructs a new LLM exception with a cause.
     *
     * @param errorCode a machine-readable error code
     * @param message   a human-readable description of the error
     * @param cause     the underlying cause
     */
    public LlmException(String errorCode, String message, Throwable cause) {
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
        return "LlmException{errorCode='" + errorCode + "', message='" + getMessage() + "'}";
    }
}

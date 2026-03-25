package com.capellaagent.teamcenter.client;

/**
 * Exception thrown when a Teamcenter REST API call fails.
 * <p>
 * Carries the HTTP status code and response body for diagnostic purposes.
 */
public class TcException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int statusCode;
    private final String responseBody;

    /**
     * Constructs a new TcException.
     *
     * @param message      a human-readable description of the failure
     * @param statusCode   the HTTP status code, or -1 if no response was received
     * @param responseBody the raw response body, or {@code null}
     */
    public TcException(String message, int statusCode, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    /**
     * Constructs a new TcException wrapping a cause.
     *
     * @param message a human-readable description
     * @param cause   the underlying cause
     */
    public TcException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.responseBody = null;
    }

    /**
     * Returns the HTTP status code from the failed request.
     *
     * @return the HTTP status code, or -1 if not applicable
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Returns the raw response body from the failed request.
     *
     * @return the response body string, or {@code null}
     */
    public String getResponseBody() {
        return responseBody;
    }

    @Override
    public String toString() {
        return "TcException{statusCode=" + statusCode + ", message='" + getMessage() + "'}";
    }
}

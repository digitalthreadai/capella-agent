package com.capellaagent.core.security;

import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLHandshakeException;

import com.capellaagent.core.llm.LlmException;

/**
 * Maps low-level exceptions to generic, user-safe error messages so that
 * raw stack traces, host names, API endpoints, response bodies, or
 * provider internals never reach the user-facing UI or the LLM context.
 * <p>
 * Usage:
 * <pre>
 * try {
 *     provider.chat(...);
 * } catch (Exception e) {
 *     MessageDialog.openError(shell, "Error",
 *         ErrorMessageFilter.safeUserMessage(e));
 * }
 * </pre>
 * <p>
 * The raw exception is always logged at SEVERE so operators still have
 * the detail they need for debugging — it simply never reaches the UI.
 */
public final class ErrorMessageFilter {

    private static final Logger LOG = Logger.getLogger(ErrorMessageFilter.class.getName());

    private ErrorMessageFilter() { }

    /**
     * Returns a generic, user-safe message for the given throwable.
     * <p>
     * Logs the full exception (with cause chain) at SEVERE so operators
     * can diagnose, but the returned string never contains raw provider
     * response bodies, URLs, stack traces, or credentials.
     *
     * @param t the exception to sanitize; may be null
     * @return a short, user-appropriate message
     */
    public static String safeUserMessage(Throwable t) {
        if (t == null) {
            return "An unknown error occurred.";
        }

        // Always log the full detail for operators — never user-visible.
        LOG.log(Level.SEVERE, "Filtered error", t);

        // Walk the cause chain to classify the root problem.
        Throwable cursor = t;
        while (cursor != null) {
            if (cursor instanceof SSLHandshakeException) {
                return "TLS handshake failed. Check network connectivity and certificate trust.";
            }
            if (cursor instanceof UnknownHostException) {
                return "Cannot reach provider. Check your network connection.";
            }
            if (cursor instanceof HttpTimeoutException) {
                return "Provider did not respond in time. Check your network connection.";
            }
            if (cursor instanceof LlmException) {
                LlmException le = (LlmException) cursor;
                return mapLlmError(le);
            }
            cursor = cursor.getCause();
            if (cursor == t) {
                break; // cycle guard
            }
        }

        // Fallback — never leak raw message text to the user.
        return "Operation failed. Check the Error Log view for details.";
    }

    /**
     * Returns a safe message for {@link LlmException} error codes without
     * exposing the provider's raw response body.
     */
    private static String mapLlmError(LlmException e) {
        String code = e.getErrorCode();
        if (code == null) {
            return "Provider call failed. Check the Error Log view for details.";
        }
        switch (code) {
            case LlmException.ERR_AUTHENTICATION:
                return "Authentication failed. Check your API key in Preferences > Capella Agent.";
            case LlmException.ERR_RATE_LIMITED:
                return "Provider rate limit exceeded. Wait a moment and try again.";
            case LlmException.ERR_CONNECTION:
                return "Cannot reach provider. Check your network connection.";
            case LlmException.ERR_INVALID_REQUEST:
                return "Provider rejected the request. Check your configuration.";
            case LlmException.ERR_PARSE:
                return "Could not parse the provider's response. Check the Error Log view for details.";
            default:
                return "Provider call failed. Check the Error Log view for details.";
        }
    }

    /**
     * Returns a sanitized error string suitable for feeding back to the LLM
     * as a tool result. Same filtering as {@link #safeUserMessage(Throwable)}
     * but with a prefix that clearly labels this as an error, so the LLM
     * does not confuse it with legitimate tool output.
     *
     * @param t the exception to sanitize
     * @return a string formatted for LLM consumption
     */
    public static String safeToolResultMessage(Throwable t) {
        return "ERROR: " + safeUserMessage(t);
    }
}

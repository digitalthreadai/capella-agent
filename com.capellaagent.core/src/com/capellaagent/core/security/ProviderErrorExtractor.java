package com.capellaagent.core.security;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Extracts only the provider-supplied error *code* from a JSON error body,
 * never the full message. Error bodies from OpenAI/Claude/Gemini routinely
 * include echoed prompt fragments and user-supplied content in
 * {@code error.message} — logging them risks leaking credentials, PII, and
 * model data into the plain-text error log.
 * <p>
 * Accepted shapes:
 * <ul>
 *   <li>OpenAI/Claude: {@code {"error":{"code":"...","type":"...","message":"..."}}}</li>
 *   <li>Gemini: {@code {"error":{"status":"...","code":400,"message":"..."}}}</li>
 *   <li>Anything else: returns {@code null}</li>
 * </ul>
 */
public final class ProviderErrorExtractor {

    private ProviderErrorExtractor() { }

    /**
     * Extracts a short error code/type identifier from a provider JSON error
     * body. Never returns the {@code message} field.
     *
     * @param body the raw response body (may be null or non-JSON)
     * @return a short identifier like {@code "invalid_api_key"} or
     *         {@code "PERMISSION_DENIED"}; or {@code null} if unknown
     */
    public static String extractErrorCode(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (!root.has("error") || !root.get("error").isJsonObject()) {
                return null;
            }
            JsonObject err = root.getAsJsonObject("error");
            // OpenAI/Claude use "code" and "type"; Gemini uses "status".
            if (err.has("code") && err.get("code").isJsonPrimitive()) {
                return safeCode(err.get("code").getAsString());
            }
            if (err.has("type") && err.get("type").isJsonPrimitive()) {
                return safeCode(err.get("type").getAsString());
            }
            if (err.has("status") && err.get("status").isJsonPrimitive()) {
                return safeCode(err.get("status").getAsString());
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Constrains a code string to a short, safe identifier. Strips anything
     * other than {@code [A-Za-z0-9_.-]} and caps at 64 characters so that a
     * malicious provider can't embed a prompt-injection payload in the code
     * field itself.
     */
    private static String safeCode(String code) {
        if (code == null) {
            return null;
        }
        String filtered = code.replaceAll("[^A-Za-z0-9_.\\-]", "");
        if (filtered.length() > 64) {
            filtered = filtered.substring(0, 64);
        }
        return filtered.isEmpty() ? null : filtered;
    }
}

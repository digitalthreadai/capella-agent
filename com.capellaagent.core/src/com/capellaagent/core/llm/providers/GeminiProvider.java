package com.capellaagent.core.llm.providers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.capellaagent.core.config.AgentConfiguration;
import com.capellaagent.core.llm.AuthToken;
import com.capellaagent.core.llm.ILlmProvider;
import com.capellaagent.core.security.ProviderErrorExtractor;
import com.capellaagent.core.llm.LlmException;
import com.capellaagent.core.llm.LlmMessage;
import com.capellaagent.core.llm.LlmRequestConfig;
import com.capellaagent.core.llm.LlmResponse;
import com.capellaagent.core.llm.LlmToolCall;
import com.capellaagent.core.tools.IToolDescriptor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * LLM provider for Google Gemini API.
 * <p>
 * Gemini uses a unique protocol (not OpenAI-compatible), so this provider
 * implements the full Gemini REST API directly.
 * <p>
 * Get an API key at <a href="https://aistudio.google.com/apikey">aistudio.google.com</a>.
 */
public class GeminiProvider implements ILlmProvider {

    private static final Logger LOG = Logger.getLogger(GeminiProvider.class.getName());

    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String DEFAULT_MODEL = "gemini-2.0-flash";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

    private final HttpClient httpClient;

    public GeminiProvider() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public GeminiProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String getId() {
        return "gemini";
    }

    @Override
    public String getDisplayName() {
        return "Google Gemini";
    }

    @Override
    public LlmResponse chat(List<LlmMessage> messages, List<IToolDescriptor> tools,
                             LlmRequestConfig config) throws LlmException {

        AuthToken apiKey = AgentConfiguration.getInstance().getApiKeyToken("gemini");
        if (!apiKey.isPresent()) {
            throw new LlmException(LlmException.ERR_AUTHENTICATION,
                    "Gemini API key not configured. Set it in Preferences > Capella Agent > LLM Provider.");
        }

        String model = (config.getModelId() != null && !config.getModelId().isBlank())
                ? config.getModelId() : DEFAULT_MODEL;

        // SECURITY (A1): Do NOT embed the API key in the URL query string.
        // URLs are logged by proxies, written to browser history, and routinely
        // leak into server access logs. Gemini accepts the key as an HTTP header
        // (x-goog-api-key), which stays out of URL logs.
        String url = API_BASE + model + ":generateContent";
        JsonObject requestBody = buildRequestBody(messages, tools, config);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey.value())
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            return parseResponse(response);

        } catch (LlmException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new LlmException(LlmException.ERR_CONNECTION,
                    "Request to Gemini API timed out", e);
        } catch (java.io.IOException e) {
            throw new LlmException(LlmException.ERR_CONNECTION,
                    "Failed to connect to Gemini API: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(LlmException.ERR_CONNECTION,
                    "Request to Gemini API was interrupted", e);
        } catch (Exception e) {
            throw new LlmException(LlmException.ERR_UNKNOWN,
                    "Unexpected error calling Gemini API: " + e.getMessage(), e);
        }
    }

    private JsonObject buildRequestBody(List<LlmMessage> messages, List<IToolDescriptor> tools,
                                         LlmRequestConfig config) {
        JsonObject body = new JsonObject();

        // System instruction
        if (config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
            JsonObject systemInstruction = new JsonObject();
            JsonArray parts = new JsonArray();
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", config.getSystemPrompt());
            parts.add(textPart);
            systemInstruction.add("parts", parts);
            body.add("systemInstruction", systemInstruction);
        }

        // Contents (messages)
        JsonArray contents = new JsonArray();
        for (LlmMessage msg : messages) {
            if (msg.getRole() == LlmMessage.Role.SYSTEM) continue; // handled above

            JsonObject content = new JsonObject();
            String role = switch (msg.getRole()) {
                case USER, TOOL -> "user";
                case ASSISTANT -> "model";
                default -> "user";
            };
            content.addProperty("role", role);

            JsonArray parts = new JsonArray();
            if (msg.getRole() == LlmMessage.Role.TOOL) {
                // Gemini function response format
                JsonObject functionResponse = new JsonObject();
                functionResponse.addProperty("name", msg.getName().orElse("tool_result"));
                JsonObject responseObj = new JsonObject();
                responseObj.addProperty("result", msg.getContent());
                functionResponse.add("response", responseObj);
                JsonObject part = new JsonObject();
                part.add("functionResponse", functionResponse);
                parts.add(part);
            } else {
                JsonObject textPart = new JsonObject();
                textPart.addProperty("text", msg.getContent());
                parts.add(textPart);
            }
            content.add("parts", parts);
            contents.add(content);
        }
        body.add("contents", contents);

        // Tools
        if (tools != null && !tools.isEmpty()) {
            JsonArray toolsArray = new JsonArray();
            JsonObject toolsObj = new JsonObject();
            JsonArray functionDeclarations = new JsonArray();
            for (IToolDescriptor tool : tools) {
                JsonObject fn = new JsonObject();
                fn.addProperty("name", tool.getName());
                fn.addProperty("description", tool.getDescription());
                fn.add("parameters", tool.getParametersSchema());
                functionDeclarations.add(fn);
            }
            toolsObj.add("functionDeclarations", functionDeclarations);
            toolsArray.add(toolsObj);
            body.add("tools", toolsArray);
        }

        // Generation config
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", config.getTemperature());
        generationConfig.addProperty("maxOutputTokens", config.getMaxTokens());
        body.add("generationConfig", generationConfig);

        return body;
    }

    private LlmResponse parseResponse(HttpResponse<String> httpResponse) throws LlmException {
        int statusCode = httpResponse.statusCode();
        String responseBody = httpResponse.body();

        // SECURITY (A4): never embed the raw response body in error messages.
        String errorCode = ProviderErrorExtractor.extractErrorCode(responseBody);
        if (statusCode == 400) {
            throw new LlmException(LlmException.ERR_INVALID_REQUEST,
                    "Gemini API bad request"
                    + (errorCode != null ? " (" + errorCode + ")" : ""));
        }
        if (statusCode == 403) {
            throw new LlmException(LlmException.ERR_AUTHENTICATION,
                    "Gemini API authentication failed. Check your API key.");
        }
        if (statusCode == 429) {
            throw new LlmException(LlmException.ERR_RATE_LIMITED,
                    "Gemini API rate limit exceeded.");
        }
        if (statusCode != 200) {
            throw new LlmException(LlmException.ERR_INVALID_REQUEST,
                    "Gemini API returned HTTP " + statusCode
                    + (errorCode != null ? " (" + errorCode + ")" : ""));
        }

        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray candidates = json.getAsJsonArray("candidates");

            if (candidates == null || candidates.isEmpty()) {
                throw new LlmException(LlmException.ERR_PARSE,
                        "Gemini response contained no candidates");
            }

            JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
            JsonObject content = firstCandidate.getAsJsonObject("content");
            String finishReason = firstCandidate.has("finishReason")
                    ? firstCandidate.get("finishReason").getAsString() : "STOP";

            StringBuilder textBuilder = new StringBuilder();
            List<LlmToolCall> toolCalls = new ArrayList<>();

            if (content != null && content.has("parts")) {
                JsonArray parts = content.getAsJsonArray("parts");
                int tcIndex = 0;
                for (JsonElement part : parts) {
                    JsonObject partObj = part.getAsJsonObject();
                    if (partObj.has("text")) {
                        textBuilder.append(partObj.get("text").getAsString());
                    } else if (partObj.has("functionCall")) {
                        JsonObject fc = partObj.getAsJsonObject("functionCall");
                        String name = fc.get("name").getAsString();
                        String args = fc.has("args") ? fc.get("args").toString() : "{}";
                        toolCalls.add(new LlmToolCall("gemini_tc_" + tcIndex, name, args));
                        tcIndex++;
                    }
                }
            }

            String text = textBuilder.length() > 0 ? textBuilder.toString() : null;
            String stopReason = !toolCalls.isEmpty() ? "tool_calls" : finishReason;
            return new LlmResponse(text, toolCalls, stopReason);

        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            // SECURITY (A4): never log full response body.
            LOG.log(Level.SEVERE,
                "Failed to parse Gemini response (status=" + statusCode
                + ", bodyLen=" + (responseBody == null ? 0 : responseBody.length()) + ")", e);
            throw new LlmException(LlmException.ERR_PARSE,
                    "Failed to parse Gemini response", e);
        }
    }
}

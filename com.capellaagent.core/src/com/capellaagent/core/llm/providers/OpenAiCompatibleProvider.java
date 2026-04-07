package com.capellaagent.core.llm.providers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.capellaagent.core.config.AgentConfiguration;
import com.capellaagent.core.llm.ILlmProvider;
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
 * Abstract base class for all LLM providers that implement the
 * OpenAI Chat Completions protocol.
 * <p>
 * Many providers (OpenAI, Groq, DeepSeek, Mistral, Together AI, OpenRouter,
 * Fireworks, etc.) share the same request/response format. This base class
 * implements the full protocol once; subclasses only need to provide:
 * <ul>
 *   <li>{@link #getId()} and {@link #getDisplayName()} — identity</li>
 *   <li>{@link #getDefaultApiUrl()} — the provider's chat completions endpoint</li>
 *   <li>{@link #getDefaultModel()} — the default model name</li>
 *   <li>{@link #getApiKeyId()} — the key used in Eclipse Secure Storage</li>
 * </ul>
 * <p>
 * For providers with additional requirements (e.g., Azure's {@code api-key} header
 * or OpenRouter's {@code HTTP-Referer} header), override
 * {@link #getExtraHeaders()} or {@link #getAuthHeaderValue(String)}.
 */
public abstract class OpenAiCompatibleProvider implements ILlmProvider {

    private static final Logger LOG = Logger.getLogger(OpenAiCompatibleProvider.class.getName());
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

    private final HttpClient httpClient;

    /**
     * Parses the {@code usage} block of an OpenAI-compatible chat completion
     * response. Covers OpenAI, Groq, GitHub Models, DeepSeek, Mistral,
     * OpenRouter (when {@code usage.include=true}) and any other provider
     * that uses the same shape.
     */
    @Override
    public com.capellaagent.core.llm.LlmUsage parseUsage(com.google.gson.JsonObject rawResponse) {
        if (rawResponse == null || !rawResponse.has("usage")
                || !rawResponse.get("usage").isJsonObject()) {
            return com.capellaagent.core.llm.LlmUsage.empty();
        }
        com.google.gson.JsonObject u = rawResponse.getAsJsonObject("usage");
        int prompt = u.has("prompt_tokens") && !u.get("prompt_tokens").isJsonNull()
            ? u.get("prompt_tokens").getAsInt() : 0;
        int completion = u.has("completion_tokens") && !u.get("completion_tokens").isJsonNull()
            ? u.get("completion_tokens").getAsInt() : 0;
        int cached = 0;
        if (u.has("prompt_tokens_details") && u.get("prompt_tokens_details").isJsonObject()) {
            com.google.gson.JsonObject details = u.getAsJsonObject("prompt_tokens_details");
            if (details.has("cached_tokens")) {
                cached = details.get("cached_tokens").getAsInt();
            }
        }
        int reasoning = 0;
        if (u.has("completion_tokens_details") && u.get("completion_tokens_details").isJsonObject()) {
            com.google.gson.JsonObject details = u.getAsJsonObject("completion_tokens_details");
            if (details.has("reasoning_tokens")) {
                reasoning = details.get("reasoning_tokens").getAsInt();
            }
        }
        return new com.capellaagent.core.llm.LlmUsage(
            prompt, completion, cached, reasoning,
            com.capellaagent.core.llm.LlmUsage.Source.EXACT);
    }

    /**
     * Creates a provider with a default HTTP client.
     */
    protected OpenAiCompatibleProvider() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Creates a provider with a custom HTTP client (for testing).
     *
     * @param httpClient the HTTP client to use
     */
    protected OpenAiCompatibleProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    // ── Subclass contract ──────────────────────────────────────────────

    /** The REST endpoint URL for chat completions. */
    protected abstract String getDefaultApiUrl();

    /** The default model name if none is specified in config. */
    protected abstract String getDefaultModel();

    /** The key used to retrieve the API key from Eclipse Secure Storage. */
    protected abstract String getApiKeyId();

    /**
     * Extra HTTP headers to include in every request.
     * Override for providers that require additional headers
     * (e.g., OpenRouter's {@code HTTP-Referer}).
     *
     * @return an unmodifiable map of header name to value; empty by default
     */
    protected Map<String, String> getExtraHeaders() {
        return Collections.emptyMap();
    }

    /**
     * Returns the name of the authorization header.
     * Override for providers that use a non-standard header
     * (e.g., Azure uses {@code api-key}).
     *
     * @return header name; defaults to {@code "Authorization"}
     */
    protected String getAuthHeaderName() {
        return "Authorization";
    }

    /**
     * Returns the value of the authorization header.
     * Override for providers that use a non-standard format.
     *
     * @param apiKey the raw API key from Secure Storage
     * @return header value; defaults to {@code "Bearer " + apiKey}
     */
    protected String getAuthHeaderValue(String apiKey) {
        return "Bearer " + apiKey;
    }

    /**
     * Whether this provider requires an API key. Override to return false
     * for providers like Ollama that don't need one.
     *
     * @return true if an API key is required; defaults to true
     */
    protected boolean requiresApiKey() {
        return true;
    }

    // ── ILlmProvider implementation ────────────────────────────────────

    @Override
    public LlmResponse chat(List<LlmMessage> messages, List<IToolDescriptor> tools,
                             LlmRequestConfig config) throws LlmException {

        String apiKey = AgentConfiguration.getInstance().getApiKey(getApiKeyId());
        if (requiresApiKey() && (apiKey == null || apiKey.isBlank())) {
            throw new LlmException(LlmException.ERR_AUTHENTICATION,
                    getDisplayName() + " API key not configured. "
                    + "Set it in Preferences > Capella Agent > LLM Provider.");
        }

        String model = (config.getModelId() != null && !config.getModelId().isBlank())
                ? config.getModelId() : getDefaultModel();

        JsonObject requestBody = buildRequestBody(messages, tools, config, model);

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(getDefaultApiUrl()))
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()));

            // Auth header
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header(getAuthHeaderName(), getAuthHeaderValue(apiKey));
            }

            // Extra headers (provider-specific)
            for (Map.Entry<String, String> h : getExtraHeaders().entrySet()) {
                builder.header(h.getKey(), h.getValue());
            }

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            return parseResponse(response);

        } catch (LlmException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new LlmException(LlmException.ERR_CONNECTION,
                    "Request to " + getDisplayName() + " timed out", e);
        } catch (java.io.IOException e) {
            throw new LlmException(LlmException.ERR_CONNECTION,
                    "Failed to connect to " + getDisplayName() + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(LlmException.ERR_CONNECTION,
                    "Request to " + getDisplayName() + " was interrupted", e);
        } catch (Exception e) {
            throw new LlmException(LlmException.ERR_UNKNOWN,
                    "Unexpected error calling " + getDisplayName() + ": " + e.getMessage(), e);
        }
    }

    // ── Protocol: request building ─────────────────────────────────────

    private JsonObject buildRequestBody(List<LlmMessage> messages, List<IToolDescriptor> tools,
                                         LlmRequestConfig config, String model) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", config.getMaxTokens());
        body.addProperty("temperature", config.getTemperature());

        JsonArray messagesArray = new JsonArray();

        // System prompt
        if (config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
            JsonObject sysMsg = new JsonObject();
            sysMsg.addProperty("role", "system");
            sysMsg.addProperty("content", config.getSystemPrompt());
            messagesArray.add(sysMsg);
        }

        for (LlmMessage msg : messages) {
            messagesArray.add(convertMessage(msg));
        }
        body.add("messages", messagesArray);

        // Tools
        if (tools != null && !tools.isEmpty()) {
            JsonArray toolsArray = new JsonArray();
            for (IToolDescriptor tool : tools) {
                toolsArray.add(convertTool(tool));
            }
            body.add("tools", toolsArray);
        }

        return body;
    }

    private JsonObject convertMessage(LlmMessage msg) {
        JsonObject message = new JsonObject();
        switch (msg.getRole()) {
            case SYSTEM:
                message.addProperty("role", "system");
                message.addProperty("content", msg.getContent());
                break;
            case USER:
                message.addProperty("role", "user");
                message.addProperty("content", msg.getContent());
                break;
            case ASSISTANT:
                message.addProperty("role", "assistant");
                if (msg.hasToolCalls()) {
                    // OpenAI format: content is null when tool_calls present
                    message.add("content", com.google.gson.JsonNull.INSTANCE);
                    JsonArray toolCallsArr = new JsonArray();
                    for (LlmToolCall tc : msg.getToolCalls()) {
                        JsonObject tcObj = new JsonObject();
                        tcObj.addProperty("id", tc.getId());
                        tcObj.addProperty("type", "function");
                        JsonObject fn = new JsonObject();
                        fn.addProperty("name", tc.getName());
                        fn.addProperty("arguments", tc.getArguments());
                        tcObj.add("function", fn);
                        toolCallsArr.add(tcObj);
                    }
                    message.add("tool_calls", toolCallsArr);
                } else {
                    message.addProperty("content", msg.getContent() != null ? msg.getContent() : "");
                }
                break;
            case TOOL:
                message.addProperty("role", "tool");
                message.addProperty("content", msg.getContent());
                message.addProperty("tool_call_id",
                        msg.getToolCallId().orElse("unknown"));
                break;
        }
        return message;
    }

    private JsonObject convertTool(IToolDescriptor tool) {
        JsonObject toolObj = new JsonObject();
        toolObj.addProperty("type", "function");
        JsonObject function = new JsonObject();
        function.addProperty("name", tool.getName());
        function.addProperty("description", tool.getDescription());
        function.add("parameters", tool.getParametersSchema());
        toolObj.add("function", function);
        return toolObj;
    }

    // ── Protocol: response parsing ─────────────────────────────────────

    private LlmResponse parseResponse(HttpResponse<String> httpResponse) throws LlmException {
        int statusCode = httpResponse.statusCode();
        String responseBody = httpResponse.body();

        if (statusCode == 401) {
            throw new LlmException(LlmException.ERR_AUTHENTICATION,
                    getDisplayName() + " authentication failed. Check your API key.");
        }
        if (statusCode == 429) {
            throw new LlmException(LlmException.ERR_RATE_LIMITED,
                    getDisplayName() + " rate limit exceeded. Please wait and try again.");
        }
        if (statusCode >= 500) {
            throw new LlmException(LlmException.ERR_CONNECTION,
                    getDisplayName() + " server error (HTTP " + statusCode + ")");
        }
        if (statusCode != 200) {
            throw new LlmException(LlmException.ERR_INVALID_REQUEST,
                    getDisplayName() + " returned HTTP " + statusCode + ": " + responseBody);
        }

        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray choices = json.getAsJsonArray("choices");

            if (choices == null || choices.isEmpty()) {
                throw new LlmException(LlmException.ERR_PARSE,
                        getDisplayName() + " response contained no choices");
            }

            JsonObject firstChoice = choices.get(0).getAsJsonObject();
            String finishReason = firstChoice.has("finish_reason")
                    ? firstChoice.get("finish_reason").getAsString() : "unknown";
            JsonObject messageObj = firstChoice.getAsJsonObject("message");

            // Text content
            String textContent = null;
            if (messageObj.has("content") && !messageObj.get("content").isJsonNull()) {
                textContent = messageObj.get("content").getAsString();
            }

            // Tool calls
            List<LlmToolCall> toolCalls = new ArrayList<>();
            if (messageObj.has("tool_calls") && !messageObj.get("tool_calls").isJsonNull()) {
                JsonArray toolCallsArray = messageObj.getAsJsonArray("tool_calls");
                for (JsonElement tc : toolCallsArray) {
                    JsonObject tcObj = tc.getAsJsonObject();
                    String id = tcObj.get("id").getAsString();
                    JsonObject function = tcObj.getAsJsonObject("function");
                    String name = function.get("name").getAsString();
                    String arguments = function.get("arguments").getAsString();
                    toolCalls.add(new LlmToolCall(id, name, arguments));
                }
            }

            return new LlmResponse(textContent, toolCalls, finishReason);

        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to parse " + getDisplayName() + " response: " + responseBody, e);
            throw new LlmException(LlmException.ERR_PARSE,
                    "Failed to parse " + getDisplayName() + " response: " + e.getMessage(), e);
        }
    }
}

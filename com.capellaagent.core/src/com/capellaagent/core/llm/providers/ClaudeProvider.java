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
 * LLM provider implementation for the Anthropic Messages API.
 * <p>
 * Communicates with the Anthropic Claude API at
 * {@code https://api.anthropic.com/v1/messages}. Converts the unified
 * capella-agent message and tool formats to Anthropic's native protocol
 * and parses the response, handling both text blocks and tool_use blocks.
 */
public class ClaudeProvider implements ILlmProvider {

    private static final Logger LOG = Logger.getLogger(ClaudeProvider.class.getName());

    /**
     * Parses Anthropic's {@code usage} block.
     * <p>
     * Anthropic shape: {@code usage: {input_tokens, output_tokens,
     * cache_read_input_tokens, cache_creation_input_tokens}}.
     */
    @Override
    public com.capellaagent.core.llm.LlmUsage parseUsage(com.google.gson.JsonObject rawResponse) {
        if (rawResponse == null || !rawResponse.has("usage")
                || !rawResponse.get("usage").isJsonObject()) {
            return com.capellaagent.core.llm.LlmUsage.empty();
        }
        com.google.gson.JsonObject u = rawResponse.getAsJsonObject("usage");
        int input = u.has("input_tokens") ? u.get("input_tokens").getAsInt() : 0;
        int output = u.has("output_tokens") ? u.get("output_tokens").getAsInt() : 0;
        int cached = u.has("cache_read_input_tokens")
            ? u.get("cache_read_input_tokens").getAsInt() : 0;
        return new com.capellaagent.core.llm.LlmUsage(
            input, output, cached, 0,
            com.capellaagent.core.llm.LlmUsage.Source.EXACT);
    }

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

    private final HttpClient httpClient;

    /**
     * Creates a new Claude provider with a default HTTP client.
     */
    public ClaudeProvider() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Creates a new Claude provider with a custom HTTP client (for testing).
     *
     * @param httpClient the HTTP client to use
     */
    public ClaudeProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String getId() {
        return "anthropic";
    }

    @Override
    public String getDisplayName() {
        return "Anthropic Claude";
    }

    @Override
    public LlmResponse chat(List<LlmMessage> messages, List<IToolDescriptor> tools,
                             LlmRequestConfig config) throws LlmException {

        String apiKey = AgentConfiguration.getInstance().getApiKey("anthropic");
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmException(LlmException.ERR_AUTHENTICATION,
                    "Anthropic API key not configured. Set it in Agent preferences.");
        }

        String model = (config.getModelId() != null && !config.getModelId().isBlank())
                ? config.getModelId() : DEFAULT_MODEL;

        JsonObject requestBody = buildRequestBody(messages, tools, config, model);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
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
                    "Request to Anthropic API timed out", e);
        } catch (java.io.IOException e) {
            throw new LlmException(LlmException.ERR_CONNECTION,
                    "Failed to connect to Anthropic API: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(LlmException.ERR_CONNECTION,
                    "Request to Anthropic API was interrupted", e);
        } catch (Exception e) {
            throw new LlmException(LlmException.ERR_UNKNOWN,
                    "Unexpected error calling Anthropic API: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the JSON request body for the Anthropic Messages API.
     */
    private JsonObject buildRequestBody(List<LlmMessage> messages, List<IToolDescriptor> tools,
                                         LlmRequestConfig config, String model) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", config.getMaxTokens());
        body.addProperty("temperature", config.getTemperature());

        // System prompt is a top-level field in Anthropic API
        if (config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
            body.addProperty("system", config.getSystemPrompt());
        }

        // Convert messages (Anthropic does not include system messages in the array)
        JsonArray messagesArray = new JsonArray();
        for (LlmMessage msg : messages) {
            if (msg.getRole() == LlmMessage.Role.SYSTEM) {
                // System messages handled via the top-level "system" field.
                // If config has no system prompt, use the first system message.
                if (!body.has("system")) {
                    body.addProperty("system", msg.getContent());
                }
                continue;
            }
            messagesArray.add(convertMessage(msg));
        }
        body.add("messages", messagesArray);

        // Convert tools
        if (tools != null && !tools.isEmpty()) {
            JsonArray toolsArray = new JsonArray();
            for (IToolDescriptor tool : tools) {
                toolsArray.add(convertTool(tool));
            }
            body.add("tools", toolsArray);
        }

        return body;
    }

    /**
     * Converts a unified LlmMessage to Anthropic message format.
     */
    private JsonObject convertMessage(LlmMessage msg) {
        JsonObject message = new JsonObject();

        switch (msg.getRole()) {
            case USER:
                message.addProperty("role", "user");
                message.addProperty("content", msg.getContent());
                break;

            case ASSISTANT:
                message.addProperty("role", "assistant");
                // Check if this is a tool-use assistant message
                // For simplicity, treat as plain text content
                message.addProperty("content", msg.getContent());
                break;

            case TOOL:
                // Tool results in Anthropic format go in a "user" message
                // with content type "tool_result"
                message.addProperty("role", "user");
                JsonArray contentArray = new JsonArray();
                JsonObject toolResult = new JsonObject();
                toolResult.addProperty("type", "tool_result");
                toolResult.addProperty("tool_use_id",
                        msg.getToolCallId().orElse("unknown"));
                toolResult.addProperty("content", msg.getContent());
                contentArray.add(toolResult);
                message.add("content", contentArray);
                break;

            default:
                message.addProperty("role", "user");
                message.addProperty("content", msg.getContent());
                break;
        }

        return message;
    }

    /**
     * Converts a tool descriptor to Anthropic tool format.
     */
    private JsonObject convertTool(IToolDescriptor tool) {
        JsonObject toolObj = new JsonObject();
        toolObj.addProperty("name", tool.getName());
        toolObj.addProperty("description", tool.getDescription());
        toolObj.add("input_schema", tool.getParametersSchema());
        return toolObj;
    }

    /**
     * Parses the HTTP response from the Anthropic API.
     */
    private LlmResponse parseResponse(HttpResponse<String> httpResponse) throws LlmException {
        int statusCode = httpResponse.statusCode();
        String responseBody = httpResponse.body();

        if (statusCode == 401) {
            throw new LlmException(LlmException.ERR_AUTHENTICATION,
                    "Anthropic API authentication failed. Check your API key.");
        }
        if (statusCode == 429) {
            throw new LlmException(LlmException.ERR_RATE_LIMITED,
                    "Anthropic API rate limit exceeded. Please wait and try again.");
        }
        if (statusCode >= 500) {
            throw new LlmException(LlmException.ERR_CONNECTION,
                    "Anthropic API server error (HTTP " + statusCode + ")");
        }
        if (statusCode != 200) {
            throw new LlmException(LlmException.ERR_INVALID_REQUEST,
                    "Anthropic API returned HTTP " + statusCode + ": " + responseBody);
        }

        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            String stopReason = json.has("stop_reason")
                    ? json.get("stop_reason").getAsString() : "unknown";

            StringBuilder textBuilder = new StringBuilder();
            List<LlmToolCall> toolCalls = new ArrayList<>();

            JsonArray content = json.getAsJsonArray("content");
            if (content != null) {
                for (JsonElement block : content) {
                    JsonObject blockObj = block.getAsJsonObject();
                    String type = blockObj.get("type").getAsString();

                    if ("text".equals(type)) {
                        textBuilder.append(blockObj.get("text").getAsString());
                    } else if ("tool_use".equals(type)) {
                        String id = blockObj.get("id").getAsString();
                        String name = blockObj.get("name").getAsString();
                        String arguments = blockObj.get("input").toString();
                        toolCalls.add(new LlmToolCall(id, name, arguments));
                    }
                }
            }

            String text = textBuilder.length() > 0 ? textBuilder.toString() : null;
            return new LlmResponse(text, toolCalls, stopReason);

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to parse Anthropic response: " + responseBody, e);
            throw new LlmException(LlmException.ERR_PARSE,
                    "Failed to parse Anthropic API response: " + e.getMessage(), e);
        }
    }
}

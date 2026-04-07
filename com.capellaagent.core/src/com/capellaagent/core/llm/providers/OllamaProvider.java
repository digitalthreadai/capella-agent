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
 * LLM provider implementation for the local Ollama REST API.
 * <p>
 * Communicates with Ollama at {@code http://localhost:11434/api/chat}.
 * Ollama provides local inference with no API key required, making it
 * suitable for development and air-gapped environments.
 */
public class OllamaProvider implements ILlmProvider {

    private static final Logger LOG = Logger.getLogger(OllamaProvider.class.getName());

    /**
     * Parses Ollama's flat usage fields.
     * <p>
     * Ollama shape: top-level {@code prompt_eval_count} and {@code eval_count}
     * (no {@code usage} wrapper).
     */
    @Override
    public com.capellaagent.core.llm.LlmUsage parseUsage(com.google.gson.JsonObject rawResponse) {
        if (rawResponse == null) {
            return com.capellaagent.core.llm.LlmUsage.empty();
        }
        int input = rawResponse.has("prompt_eval_count")
            ? rawResponse.get("prompt_eval_count").getAsInt() : 0;
        int output = rawResponse.has("eval_count")
            ? rawResponse.get("eval_count").getAsInt() : 0;
        if (input == 0 && output == 0) {
            return com.capellaagent.core.llm.LlmUsage.empty();
        }
        return new com.capellaagent.core.llm.LlmUsage(
            input, output, 0, 0,
            com.capellaagent.core.llm.LlmUsage.Source.EXACT);
    }

    private static final String DEFAULT_API_URL = "http://localhost:11434/api/chat";
    private static final String DEFAULT_MODEL = "llama3.1";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(300);

    private final HttpClient httpClient;
    private volatile String baseUrl;

    /**
     * Creates a new Ollama provider with a default HTTP client.
     */
    public OllamaProvider() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.baseUrl = DEFAULT_API_URL;
    }

    /**
     * Creates a new Ollama provider with a custom HTTP client (for testing).
     *
     * @param httpClient the HTTP client to use
     */
    public OllamaProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.baseUrl = DEFAULT_API_URL;
    }

    @Override
    public String getId() {
        return "ollama";
    }

    @Override
    public String getDisplayName() {
        return "Ollama (Local)";
    }

    /**
     * Sets an alternative base URL for the Ollama API.
     *
     * @param url the full chat endpoint URL (e.g., "http://remote-host:11434/api/chat")
     */
    public void setBaseUrl(String url) {
        this.baseUrl = url;
    }

    @Override
    public LlmResponse chat(List<LlmMessage> messages, List<IToolDescriptor> tools,
                             LlmRequestConfig config) throws LlmException {

        String model = (config.getModelId() != null && !config.getModelId().isBlank())
                ? config.getModelId() : DEFAULT_MODEL;

        JsonObject requestBody = buildRequestBody(messages, tools, config, model);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            return parseResponse(response);

        } catch (LlmException e) {
            throw e;
        } catch (java.net.ConnectException e) {
            throw new LlmException(LlmException.ERR_CONNECTION,
                    "Cannot connect to Ollama at " + baseUrl +
                    ". Ensure Ollama is running ('ollama serve').", e);
        } catch (java.net.http.HttpTimeoutException e) {
            throw new LlmException(LlmException.ERR_CONNECTION,
                    "Request to Ollama timed out. The model may be loading or the " +
                    "request is too complex.", e);
        } catch (java.io.IOException e) {
            throw new LlmException(LlmException.ERR_CONNECTION,
                    "Failed to connect to Ollama: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(LlmException.ERR_CONNECTION,
                    "Request to Ollama was interrupted", e);
        } catch (Exception e) {
            throw new LlmException(LlmException.ERR_UNKNOWN,
                    "Unexpected error calling Ollama: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the JSON request body for the Ollama chat API.
     */
    private JsonObject buildRequestBody(List<LlmMessage> messages, List<IToolDescriptor> tools,
                                         LlmRequestConfig config, String model) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("stream", false);

        // Options for temperature
        JsonObject options = new JsonObject();
        options.addProperty("temperature", config.getTemperature());
        options.addProperty("num_predict", config.getMaxTokens());
        body.add("options", options);

        // Convert messages
        JsonArray messagesArray = new JsonArray();

        // Prepend system prompt if configured
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

        // Convert tools to Ollama format
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
     * Converts a unified LlmMessage to Ollama message format.
     */
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
                message.addProperty("content", msg.getContent());
                break;

            case TOOL:
                // Ollama treats tool results as a message with role "tool"
                message.addProperty("role", "tool");
                message.addProperty("content", msg.getContent());
                break;
        }

        return message;
    }

    /**
     * Converts a tool descriptor to Ollama tool format.
     * <p>
     * Ollama uses the same format as OpenAI for tool definitions.
     */
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

    /**
     * Parses the HTTP response from the Ollama API.
     */
    private LlmResponse parseResponse(HttpResponse<String> httpResponse) throws LlmException {
        int statusCode = httpResponse.statusCode();
        String responseBody = httpResponse.body();

        if (statusCode == 404) {
            throw new LlmException(LlmException.ERR_INVALID_REQUEST,
                    "Ollama model not found. Pull it first with 'ollama pull <model>'.");
        }
        if (statusCode != 200) {
            throw new LlmException(LlmException.ERR_INVALID_REQUEST,
                    "Ollama returned HTTP " + statusCode + ": " + responseBody);
        }

        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonObject messageObj = json.getAsJsonObject("message");

            if (messageObj == null) {
                throw new LlmException(LlmException.ERR_PARSE,
                        "Ollama response missing 'message' field");
            }

            // Extract text content
            String textContent = null;
            if (messageObj.has("content") && !messageObj.get("content").isJsonNull()) {
                String content = messageObj.get("content").getAsString();
                if (!content.isBlank()) {
                    textContent = content;
                }
            }

            // Extract tool calls
            List<LlmToolCall> toolCalls = new ArrayList<>();
            if (messageObj.has("tool_calls") && !messageObj.get("tool_calls").isJsonNull()) {
                JsonArray toolCallsArray = messageObj.getAsJsonArray("tool_calls");
                int index = 0;
                for (JsonElement tc : toolCallsArray) {
                    JsonObject tcObj = tc.getAsJsonObject();
                    JsonObject function = tcObj.getAsJsonObject("function");
                    String name = function.get("name").getAsString();
                    String arguments = function.get("arguments").toString();
                    // Ollama does not return tool call IDs, so we generate them
                    String id = "ollama_tc_" + index;
                    toolCalls.add(new LlmToolCall(id, name, arguments));
                    index++;
                }
            }

            // Determine stop reason from Ollama's done_reason field
            String stopReason = "stop";
            if (json.has("done_reason") && !json.get("done_reason").isJsonNull()) {
                stopReason = json.get("done_reason").getAsString();
            }
            if (!toolCalls.isEmpty()) {
                stopReason = "tool_calls";
            }

            return new LlmResponse(textContent, toolCalls, stopReason);

        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to parse Ollama response: " + responseBody, e);
            throw new LlmException(LlmException.ERR_PARSE,
                    "Failed to parse Ollama response: " + e.getMessage(), e);
        }
    }
}

package com.capellaagent.teamcenter.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.runtime.Platform;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * HTTP client for the Teamcenter REST API.
 * <p>
 * Manages authentication, session handling, and provides typed methods for
 * GET and POST requests against Teamcenter SOA/REST endpoints. All responses
 * are parsed as {@link JsonObject} instances.
 * <p>
 * The client automatically re-authenticates when the session expires, using
 * credentials from {@link TcConfiguration}.
 *
 * <h3>Thread Safety</h3>
 * This class is thread-safe. The underlying {@link HttpClient} is shared, and
 * the session state is synchronized.
 */
public class TcRestClient {

    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /** PLACEHOLDER: Actual Teamcenter login endpoint path. */
    private static final String LOGIN_PATH = "/tc/soa/Core-SessionService/login";

    /** PLACEHOLDER: Actual Teamcenter logout endpoint path. */
    private static final String LOGOUT_PATH = "/tc/soa/Core-SessionService/logout";

    private final TcConfiguration configuration;
    private final HttpClient httpClient;
    private volatile TcSession session;

    /**
     * Constructs a new TcRestClient with the given configuration.
     *
     * @param configuration the Teamcenter connection configuration
     */
    public TcRestClient(TcConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(DEFAULT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Authenticates with Teamcenter and establishes a session.
     *
     * @return the authenticated session
     * @throws TcException if authentication fails
     */
    public synchronized TcSession login() throws TcException {
        JsonObject loginBody = new JsonObject();
        // PLACEHOLDER: Actual login request body format
        loginBody.addProperty("credentials.userid", configuration.getUsername());
        loginBody.addProperty("credentials.password", configuration.getPassword());
        loginBody.addProperty("credentials.group", "");
        loginBody.addProperty("credentials.role", "");
        loginBody.addProperty("credentials.locale", "");

        try {
            String url = configuration.getGatewayUrl() + LOGIN_PATH;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", CONTENT_TYPE_JSON)
                    .timeout(DEFAULT_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(loginBody.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new TcException("Login failed with status " + response.statusCode(),
                        response.statusCode(), response.body());
            }

            JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
            this.session = TcSession.fromLoginResponse(responseJson);

            Platform.getLog(getClass()).info("Teamcenter login successful for user: "
                    + configuration.getUsername());

            return session;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TcException("Login request failed", e);
        }
    }

    /**
     * Logs out from Teamcenter and invalidates the current session.
     */
    public synchronized void logout() {
        if (session == null || !session.isValid()) {
            return;
        }
        try {
            post(LOGOUT_PATH, new JsonObject());
        } catch (TcException e) {
            Platform.getLog(getClass()).warn("Logout request failed: " + e.getMessage());
        } finally {
            if (session != null) {
                session.invalidate();
            }
            session = null;
        }
    }

    /**
     * Performs an authenticated GET request against the Teamcenter REST API.
     *
     * @param path   the API endpoint path (appended to the gateway URL)
     * @param params optional query parameters
     * @return the parsed JSON response
     * @throws TcException if the request fails or the response is not valid JSON
     */
    public JsonObject get(String path, Map<String, String> params) throws TcException {
        ensureAuthenticated();

        StringBuilder urlBuilder = new StringBuilder(configuration.getGatewayUrl()).append(path);
        if (params != null && !params.isEmpty()) {
            urlBuilder.append('?');
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!first) {
                    urlBuilder.append('&');
                }
                urlBuilder.append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlBuilder.toString()))
                    .header("Content-Type", CONTENT_TYPE_JSON)
                    .header("Cookie", session.getCookie())
                    .timeout(DEFAULT_TIMEOUT)
                    .GET()
                    .build();

            return executeRequest(request);
        } catch (Exception e) {
            throw wrapException("GET " + path + " failed", e);
        }
    }

    /**
     * Performs an authenticated POST request against the Teamcenter REST API.
     *
     * @param path the API endpoint path (appended to the gateway URL)
     * @param body the JSON request body
     * @return the parsed JSON response
     * @throws TcException if the request fails or the response is not valid JSON
     */
    public JsonObject post(String path, JsonObject body) throws TcException {
        ensureAuthenticated();

        try {
            String url = configuration.getGatewayUrl() + path;
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", CONTENT_TYPE_JSON)
                    .timeout(DEFAULT_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()));

            if (session != null && session.getCookie() != null) {
                builder.header("Cookie", session.getCookie());
            }

            return executeRequest(builder.build());
        } catch (Exception e) {
            throw wrapException("POST " + path + " failed", e);
        }
    }

    /**
     * Ensures the client has a valid, authenticated session. Re-authenticates
     * if the session has expired or was never established.
     *
     * @throws TcException if re-authentication fails
     */
    public synchronized void ensureAuthenticated() throws TcException {
        if (session == null || !session.isValid()) {
            login();
        }
    }

    /**
     * Returns the current session, or {@code null} if not authenticated.
     *
     * @return the current TcSession
     */
    public TcSession getSession() {
        return session;
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private JsonObject executeRequest(HttpRequest request) throws TcException {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401 || response.statusCode() == 403) {
                // Session expired; try re-login once
                synchronized (this) {
                    session = null;
                    login();
                }
                // Retry the request with new session cookie
                HttpRequest retryRequest = HttpRequest.newBuilder(request, (n, v) -> true)
                        .header("Cookie", session.getCookie())
                        .build();
                response = httpClient.send(retryRequest, HttpResponse.BodyHandlers.ofString());
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TcException("Request failed with status " + response.statusCode(),
                        response.statusCode(), response.body());
            }

            String body = response.body();
            if (body == null || body.isBlank()) {
                return new JsonObject();
            }
            return JsonParser.parseString(body).getAsJsonObject();

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TcException("HTTP request failed", e);
        }
    }

    private TcException wrapException(String context, Exception e) {
        if (e instanceof TcException tcEx) {
            return tcEx;
        }
        return new TcException(context + ": " + e.getMessage(), e);
    }
}

package com.capellaagent.teamcenter.client;

import java.time.Instant;

import com.google.gson.JsonObject;

/**
 * Represents an authenticated Teamcenter session.
 * <p>
 * A session is obtained after successful login and contains the session cookie,
 * user identifier, and expiration information. The session automatically tracks
 * its validity based on the expiration timestamp.
 */
public class TcSession {

    private String sessionCookie;
    private String userId;
    private boolean authenticated;
    private Instant expiresAt;

    /**
     * Creates a TcSession from a Teamcenter login response.
     * <p>
     * The response is expected to contain (PLACEHOLDER format):
     * <ul>
     *   <li>{@code sessionCookie} - the session identifier cookie</li>
     *   <li>{@code userId} - the authenticated user's UID</li>
     *   <li>{@code expiresInSeconds} - session lifetime in seconds</li>
     * </ul>
     *
     * @param loginResponse the JSON response from the Teamcenter login endpoint
     * @return a new TcSession populated from the response
     */
    public static TcSession fromLoginResponse(JsonObject loginResponse) {
        TcSession session = new TcSession();

        // PLACEHOLDER: Actual field names depend on Teamcenter REST API version
        session.sessionCookie = loginResponse.has("sessionCookie")
                ? loginResponse.get("sessionCookie").getAsString()
                : "";
        session.userId = loginResponse.has("userId")
                ? loginResponse.get("userId").getAsString()
                : "";
        session.authenticated = !session.sessionCookie.isEmpty();

        int expiresInSeconds = loginResponse.has("expiresInSeconds")
                ? loginResponse.get("expiresInSeconds").getAsInt()
                : 3600;
        session.expiresAt = Instant.now().plusSeconds(expiresInSeconds);

        return session;
    }

    /**
     * Returns the session cookie used to authenticate subsequent requests.
     *
     * @return the session cookie string
     */
    public String getCookie() {
        return sessionCookie;
    }

    /**
     * Returns the authenticated user's UID.
     *
     * @return the user identifier
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Checks whether this session is still valid (authenticated and not expired).
     *
     * @return {@code true} if the session is authenticated and has not expired
     */
    public boolean isValid() {
        return authenticated && Instant.now().isBefore(expiresAt);
    }

    /**
     * Returns whether the session is currently authenticated.
     *
     * @return {@code true} if authenticated
     */
    public boolean isAuthenticated() {
        return authenticated;
    }

    /**
     * Returns the session expiration timestamp.
     *
     * @return the expiration instant
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Refreshes the session expiration by the given number of seconds from now.
     *
     * @param additionalSeconds the number of seconds to extend the session
     */
    public void refresh(int additionalSeconds) {
        this.expiresAt = Instant.now().plusSeconds(additionalSeconds);
    }

    /**
     * Invalidates this session.
     */
    public void invalidate() {
        this.authenticated = false;
        this.sessionCookie = "";
    }

    @Override
    public String toString() {
        return "TcSession{userId='" + userId + "', valid=" + isValid()
                + ", expiresAt=" + expiresAt + '}';
    }
}

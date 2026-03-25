package com.capellaagent.core.bus;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Base event class for the agent message bus.
 * <p>
 * All events in the capella-agent ecosystem extend this class. Events carry
 * a source identifier, a timestamp, and a map of arbitrary data.
 */
public class AgentEvent {

    private final String source;
    private final long timestamp;
    private final Map<String, Object> data;

    /**
     * Constructs a new event with the current timestamp.
     *
     * @param source a string identifying the event source (e.g., "capella-agent", "tc-bridge")
     */
    public AgentEvent(String source) {
        this(source, System.currentTimeMillis(), new HashMap<>());
    }

    /**
     * Constructs a new event with the current timestamp and data.
     *
     * @param source the event source identifier
     * @param data   the event data map
     */
    public AgentEvent(String source, Map<String, Object> data) {
        this(source, System.currentTimeMillis(), data);
    }

    /**
     * Constructs a fully specified event.
     *
     * @param source    the event source identifier
     * @param timestamp the event timestamp in milliseconds since epoch
     * @param data      the event data map
     */
    public AgentEvent(String source, long timestamp, Map<String, Object> data) {
        this.source = source;
        this.timestamp = timestamp;
        this.data = data != null ? new HashMap<>(data) : new HashMap<>();
    }

    /**
     * Returns the event source identifier.
     *
     * @return the source string
     */
    public String getSource() {
        return source;
    }

    /**
     * Returns the event timestamp.
     *
     * @return milliseconds since epoch
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns an unmodifiable view of the event data.
     *
     * @return the event data map
     */
    public Map<String, Object> getData() {
        return Collections.unmodifiableMap(data);
    }

    /**
     * Adds a key-value pair to the event data.
     *
     * @param key   the data key
     * @param value the data value
     */
    public void putData(String key, Object value) {
        data.put(key, value);
    }

    /**
     * Retrieves a value from the event data.
     *
     * @param key the data key
     * @return the value, or null if not present
     */
    public Object getData(String key) {
        return data.get(key);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{source='" + source +
                "', timestamp=" + timestamp + ", data=" + data + "}";
    }
}

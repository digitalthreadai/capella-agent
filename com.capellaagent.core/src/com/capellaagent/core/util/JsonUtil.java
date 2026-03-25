package com.capellaagent.core.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Gson-based JSON utility methods for the capella-agent ecosystem.
 * <p>
 * Provides centralized serialization/deserialization with consistent
 * configuration, plus convenience methods for common JSON operations.
 */
public final class JsonUtil {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    private static final Gson PRETTY_GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private JsonUtil() {
        // Utility class
    }

    /**
     * Serializes an object to a JSON string.
     *
     * @param object the object to serialize
     * @return the JSON string representation
     */
    public static String toJson(Object object) {
        return GSON.toJson(object);
    }

    /**
     * Deserializes a JSON string to an object of the specified type.
     *
     * @param json  the JSON string to parse
     * @param clazz the target class
     * @param <T>   the target type
     * @return the deserialized object
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    /**
     * Parses a JSON string into a {@link JsonObject}.
     *
     * @param json the JSON string to parse
     * @return the parsed JsonObject
     * @throws com.google.gson.JsonSyntaxException if the string is not valid JSON
     */
    public static JsonObject parseObject(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    /**
     * Creates a new empty {@link JsonObject} for use as a builder.
     * <p>
     * This is a convenience method; the returned object can be populated
     * using standard Gson methods ({@code addProperty}, {@code add}).
     *
     * @return a new empty JsonObject
     */
    public static JsonObject createObject() {
        return new JsonObject();
    }

    /**
     * Pretty-prints a {@link JsonObject} with indentation.
     *
     * @param json the JsonObject to format
     * @return the pretty-printed JSON string
     */
    public static String prettyPrint(JsonObject json) {
        return PRETTY_GSON.toJson(json);
    }

    /**
     * Pretty-prints any {@link JsonElement} with indentation.
     *
     * @param element the JsonElement to format
     * @return the pretty-printed JSON string
     */
    public static String prettyPrint(JsonElement element) {
        return PRETTY_GSON.toJson(element);
    }

    /**
     * Returns the shared Gson instance for custom operations.
     *
     * @return the Gson instance
     */
    public static Gson getGson() {
        return GSON;
    }

    /**
     * Safely extracts a string from a JsonObject, returning a default if absent.
     *
     * @param obj          the JsonObject
     * @param key          the property key
     * @param defaultValue the default value if the key is missing or null
     * @return the string value or default
     */
    public static String getString(JsonObject obj, String key, String defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return defaultValue;
    }

    /**
     * Safely extracts an int from a JsonObject, returning a default if absent.
     *
     * @param obj          the JsonObject
     * @param key          the property key
     * @param defaultValue the default value if the key is missing or null
     * @return the int value or default
     */
    public static int getInt(JsonObject obj, String key, int defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsInt();
        }
        return defaultValue;
    }

    /**
     * Safely extracts a boolean from a JsonObject, returning a default if absent.
     *
     * @param obj          the JsonObject
     * @param key          the property key
     * @param defaultValue the default value if the key is missing or null
     * @return the boolean value or default
     */
    public static boolean getBoolean(JsonObject obj, String key, boolean defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsBoolean();
        }
        return defaultValue;
    }
}

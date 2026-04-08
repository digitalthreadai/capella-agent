package com.capellaagent.core.llm;

import java.util.Objects;
import java.util.function.Function;

/**
 * Opaque wrapper around an LLM provider API key.
 * <p>
 * The sole purpose of this type is to make it impossible to accidentally
 * leak an API key through {@code toString()}, exception messages, or
 * logging statements like {@code LOG.info("token=" + token)}.
 * <p>
 * Design notes (from the Week 7-8 security review):
 * <ul>
 *   <li>We do <b>not</b> use a {@code char[]} with zero-on-use semantics.
 *       In a JDK {@link java.net.http.HttpClient} world the plaintext is
 *       immediately copied into {@code String} header values; per-call
 *       zeroing would be security theatre.</li>
 *   <li>{@link #toString()} always returns {@code "***redacted***"}. Never
 *       override this. If you need the raw value, call {@link #value()}
 *       explicitly — a code reviewer will notice.</li>
 *   <li>{@link #equals(Object)} and {@link #hashCode()} deliberately do not
 *       compare the plaintext value (would enable a timing side-channel on
 *       logged hash codes). They compare only identity.</li>
 * </ul>
 */
public final class AuthToken {

    private static final String REDACTED = "***redacted***";

    private final String value;

    private AuthToken(String value) {
        this.value = value;
    }

    /**
     * Creates a token from a raw string.
     *
     * @param raw the raw API key; may be null or blank (callers must check)
     * @return a non-null wrapper; {@link #isPresent()} will be false when
     *         {@code raw} is null or blank
     */
    public static AuthToken of(String raw) {
        return new AuthToken(raw);
    }

    /**
     * Returns the raw plaintext API key.
     * <p>
     * Call this only at the point of immediate use (e.g. building an HTTP
     * header). Never log, concatenate, or store the returned string.
     *
     * @return the plaintext value; may be null if the token was created
     *         from a null input
     */
    public String value() {
        return value;
    }

    /**
     * Applies a function to the plaintext value without exposing it to the
     * caller's local variables.
     *
     * @param fn a function that maps the plaintext to some derived value
     * @param <R> the function result type
     * @return the function's result
     */
    public <R> R map(Function<String, R> fn) {
        Objects.requireNonNull(fn, "fn");
        return fn.apply(value);
    }

    /** @return true if the wrapped value is non-null and non-blank */
    public boolean isPresent() {
        return value != null && !value.isBlank();
    }

    /** @return {@code "***redacted***"} — never the underlying value */
    @Override
    public String toString() {
        return REDACTED;
    }

    @Override
    public boolean equals(Object o) {
        // Identity-only to avoid timing side-channels on plaintext compare.
        return this == o;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}

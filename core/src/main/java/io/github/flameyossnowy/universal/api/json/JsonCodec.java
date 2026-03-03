package io.github.flameyossnowy.universal.api.json;

/**
 * Codec for serializing and deserializing objects to/from JSON.
 *
 * <p>Implementations should be <strong>thread-safe</strong> as they are typically
 * shared across repositories and sessions.
 *
 * <h3>Concurrency</h3>
 * <ul>
 *   <li>Implementations must be safe for concurrent use by multiple threads.</li>
 *   <li>Avoid storing per-operation state in instance fields.</li>
 * </ul>
 *
 * <h3>Performance and Security</h3>
 * <ul>
 *   <li>Prefer streaming implementations for large objects to reduce memory usage.</li>
 *   <li>Be cautious of deserialization vulnerabilities; validate input when possible.</li>
 *   <li>Consider using specific types instead of raw maps for better type safety.</li>
 * </ul>
 *
 * @param <T> Type to serialize/deserialize
 */
public interface JsonCodec<T> {

    /**
     * Serializes the given value to a JSON string.
     *
     * <p><strong>I/O:</strong> This is a pure in-memory operation; no external I/O.</p>
     * <p><strong>Thread Safety:</strong> Implementations must be thread-safe.</p>
     *
     * @param value the value to serialize; may be null
     * @param targetType the target type for serialization context; may be used by some implementations
     * @return JSON string representation; never null
     * @throws IllegalArgumentException if value cannot be serialized
     */
    String serialize(T value, Class<T> targetType);

    /**
     * Deserializes a JSON string to the specified type.
     *
     * <p><strong>Security:</strong> Be cautious of deserializing untrusted JSON.
     * Validate the input or use safe deserialization modes when available.</p>
     * <p><strong>I/O:</strong> This is a pure in-memory operation; no external I/O.</p>
     * <p><strong>Thread Safety:</strong> Implementations must be thread-safe.</p>
     *
     * @param json the JSON string to deserialize; must not be null
     * @param targetType the target type to deserialize to; must not be null
     * @return the deserialized object; may be null if JSON represents null
     * @throws IllegalArgumentException if JSON cannot be deserialized to the target type
     */
    T deserialize(String json, Class<T> targetType);

    /**
     * Computes a patch between two values.
     *
     * <p>The default implementation returns a full replacement patch.
     * Implementations can override this to provide more efficient diff algorithms.
     *
     * <p><strong>Performance:</strong> For large objects, consider implementing
     * a proper diff algorithm to avoid sending full payloads.</p>
     * <p><strong>Thread Safety:</strong> This method must be thread-safe.</p>
     *
     * @param oldValue the original value; may be null
     * @param newValue the new value; may be null
     * @param targetType the target type for context; may be null
     * @return a patch representing the changes; never null
     */
    default JsonPatch diff(T oldValue, T newValue, Class<T> targetType) {
        return new JsonPatch.FullReplace(serialize(newValue, targetType));
    }
}

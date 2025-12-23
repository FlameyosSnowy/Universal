package io.github.flameyossnowy.universal.api.operation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Metadata about an operation, including tags, description, and custom properties.
 */
public class OperationMetadata {
    private final String description;
    private final Map<String, Object> properties;
    private final boolean cacheable;
    private final boolean idempotent;

    private OperationMetadata(
            @Nullable String description,
            @NotNull Map<String, Object> properties,
            boolean cacheable,
            boolean idempotent) {
        this.description = description;
        this.properties = new HashMap<>(properties);
        this.cacheable = cacheable;
        this.idempotent = idempotent;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    @NotNull
    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    public boolean isCacheable() {
        return cacheable;
    }

    public boolean isIdempotent() {
        return idempotent;
    }

    @SuppressWarnings("unchecked")
    public <T> T getProperty(@NotNull String key) {
        return (T) properties.get(key);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String description;
        private final Map<String, Object> properties = new HashMap<>();
        private boolean cacheable = false;
        private boolean idempotent = false;

        public Builder description(@NotNull String description) {
            this.description = description;
            return this;
        }

        public Builder property(@NotNull String key, @NotNull Object value) {
            this.properties.put(key, value);
            return this;
        }

        public Builder cacheable(boolean cacheable) {
            this.cacheable = cacheable;
            return this;
        }

        public Builder idempotent(boolean idempotent) {
            this.idempotent = idempotent;
            return this;
        }

        public OperationMetadata build() {
            return new OperationMetadata(description, properties, cacheable, idempotent);
        }
    }
}

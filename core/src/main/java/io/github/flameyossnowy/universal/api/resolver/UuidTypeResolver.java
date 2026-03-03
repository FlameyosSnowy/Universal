package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class UuidTypeResolver implements TypeResolver<UUID> {
    @Override
    public Class<UUID> getType() {
        return UUID.class;
    }

    @Override
    public Class<String> getDatabaseType() {
        return String.class;
    }

    @Override
    public @Nullable UUID resolve(@NotNull DatabaseResult result, String columnName) {
        Object raw = result.get(columnName, Object.class);
        if (raw == null) return null;

        if (raw instanceof UUID uuid) {
            return uuid;
        }
        if (raw instanceof String s) {
            return UUID.fromString(s);
        }
        if (raw instanceof byte[] bytes) {
            if (bytes.length != 16) {
                throw new IllegalArgumentException(
                    "Invalid UUID byte[] length for column '" + columnName + "': " + bytes.length
                );
            }
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            return new UUID(buf.getLong(), buf.getLong());
        }

        if ("org.bson.types.Binary".equals(raw.getClass().getName())) {
            try {
                byte[] bytes = (byte[]) raw.getClass().getMethod("getData").invoke(raw);
                if (bytes == null) return null;
                if (bytes.length != 16) {
                    throw new IllegalArgumentException(
                        "Invalid BSON Binary UUID length for column '" + columnName + "': " + bytes.length
                    );
                }
                ByteBuffer buf = ByteBuffer.wrap(bytes);
                return new UUID(buf.getLong(), buf.getLong());
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to read BSON Binary UUID for column '" + columnName + "'", e);
            }
        }

        throw new IllegalArgumentException(
            "Unsupported UUID database value type for column '" + columnName + "': " + raw.getClass().getName()
        );
    }

    @Override
    public void insert(@NotNull DatabaseParameters parameters, String index, UUID value) {
        parameters.set(index, value != null ? value.toString() : null, String.class);
    }
}

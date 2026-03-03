package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class BinaryUuidTypeResolver implements TypeResolver<UUID> {

    @Override
    public Class<UUID> getType() {
        return UUID.class;
    }

    @Override
    public Class<byte[]> getDatabaseType() {
        return byte[].class;
    }

    @Override
    public @Nullable UUID resolve(@NotNull DatabaseResult result, String columnName) {
        byte[] bytes = result.get(columnName, byte[].class);
        if (bytes == null) return null;

        ByteBuffer buf = ByteBuffer.wrap(bytes);
        long most = buf.getLong();
        long least = buf.getLong();
        return new UUID(most, least);
    }

    @Override
    public void insert(@NotNull DatabaseParameters parameters, String index, UUID value) {
        if (value == null) {
            parameters.set(index, null, byte[].class);
            return;
        }

        ByteBuffer buf = ByteBuffer.allocate(16);
        buf.putLong(value.getMostSignificantBits());
        buf.putLong(value.getLeastSignificantBits());
        parameters.set(index, buf.array(), byte[].class);
    }

    @Override
    public SqlEncoding getEncoding() {
        return SqlEncoding.BINARY;
    }
}

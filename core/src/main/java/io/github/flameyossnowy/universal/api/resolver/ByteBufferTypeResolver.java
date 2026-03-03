package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;

public final class ByteBufferTypeResolver implements TypeResolver<ByteBuffer> {
    @Override
    public Class<ByteBuffer> getType() {
        return ByteBuffer.class;
    }

    @Override
    public Class<byte[]> getDatabaseType() {
        return byte[].class;
    }

    @Override
    public @Nullable ByteBuffer resolve(DatabaseResult result, String columnName) {
        byte[] bytes = result.get(columnName, byte[].class);
        return bytes != null ? ByteBuffer.wrap(bytes) : null;
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, ByteBuffer value) {
        byte[] bytes = value != null ? value.array() : null;
        parameters.set(index, bytes, byte[].class);
    }
}

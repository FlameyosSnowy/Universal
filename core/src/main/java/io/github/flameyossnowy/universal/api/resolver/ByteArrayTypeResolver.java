package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;

public final class ByteArrayTypeResolver implements TypeResolver<byte[]> {
    @Override
    public Class<byte[]> getType() {
        return byte[].class;
    }

    @Override
    public Class<byte[]> getDatabaseType() {
        return byte[].class;
    }

    @Override
    public byte[] resolve(DatabaseResult result, String columnName) {
        return result.get(columnName, byte[].class);
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, byte[] value) {
        parameters.set(index, value, byte[].class);
    }
}

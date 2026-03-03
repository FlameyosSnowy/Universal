package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.UnknownHostException;

public final class BinaryInetAddressTypeResolver
    implements TypeResolver<InetAddress> {

    @Override
    public Class<InetAddress> getType() {
        return InetAddress.class;
    }

    @Override
    public Class<byte[]> getDatabaseType() {
        return byte[].class;
    }

    @Override
    public @Nullable InetAddress resolve(DatabaseResult result, String columnName) {
        byte[] bytes = result.get(columnName, byte[].class);
        if (bytes == null) return null;

        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            throw new RuntimeException("Invalid binary IP address", e);
        }
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, InetAddress value) {
        parameters.set(
            index,
            value != null ? value.getAddress() : null,
            byte[].class
        );
    }

    @Override
    public SqlEncoding getEncoding() {
        return SqlEncoding.BINARY;
    }
}

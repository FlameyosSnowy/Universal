package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InetAddressTypeResolver implements TypeResolver<InetAddress> {
    private static final Map<String, InetAddress> CACHE = new ConcurrentHashMap<>(3);

    @Override
    public Class<InetAddress> getType() {
        return InetAddress.class;
    }

    @Override
    public Class<String> getDatabaseType() {
        return String.class;
    }

    @Override
    public @Nullable InetAddress resolve(DatabaseResult result, String columnName) {
        String hostAddress = result.get(columnName, String.class);
        if (hostAddress == null) return null;
        return CACHE.computeIfAbsent(hostAddress, s -> {
            try {
                return InetAddress.getByName(s);
            } catch (UnknownHostException e) {
                throw new RuntimeException("Invalid IP address in database: " + s, e);
            }
        });
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, InetAddress value) {
        parameters.set(index, value != null ? value.getHostAddress() : null, String.class);
    }
}

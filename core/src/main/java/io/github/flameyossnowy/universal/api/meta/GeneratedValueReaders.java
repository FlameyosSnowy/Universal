package io.github.flameyossnowy.universal.api.meta;

import io.github.flameyossnowy.universal.api.factory.ValueReader;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GeneratedValueReaders {
    private static final Map<String, ValueReaderRegistration<?>> FACTORIES =
        new ConcurrentHashMap<>();
    
    private GeneratedValueReaders() {}
    
    public static <ID> void register(
        String tableName,
        ValueReaderRegistration<ID> factory
    ) {
        FACTORIES.put(tableName, factory);
    }
    
    public static <ID> ValueReader get(
        String tableName,
        DatabaseResult result,
        TypeResolverRegistry registry,
        ID id
    ) {
        @SuppressWarnings("unchecked")
        ValueReaderRegistration<ID> factory = (ValueReaderRegistration<ID>) FACTORIES.get(tableName);

        if (factory == null) {
            throw new IllegalArgumentException("No ValueReader registered for: " + tableName);
        }

        return factory.apply(result, registry, id);
    }

    @FunctionalInterface
    public interface ValueReaderRegistration<ID> {
        ValueReader apply(DatabaseResult result, TypeResolverRegistry registry, ID id);
    }
}
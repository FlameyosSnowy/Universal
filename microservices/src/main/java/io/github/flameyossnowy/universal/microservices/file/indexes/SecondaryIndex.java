package io.github.flameyossnowy.universal.microservices.file.indexes;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public record SecondaryIndex<ID>(String field, boolean unique, Map<Object, Set<ID>> map) {
    public SecondaryIndex(String field, boolean unique) {
        this(field, unique, new ConcurrentHashMap<>(6));
    }
}

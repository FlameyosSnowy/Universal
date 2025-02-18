package io.github.flameyossnowy.universal.api.reflect;

import io.github.flameyossnowy.universal.api.annotations.Cacheable;
import io.github.flameyossnowy.universal.api.annotations.Constraint;
import io.github.flameyossnowy.universal.api.annotations.Index;

import java.util.Collection;
import java.util.Map;

public record RepositoryInformation(
        String repository,
        FieldData<?> primary,
        Constraint[] constraints,
        Index[] indexes,
        Cacheable cacheable,
        Class<?> type,
        Class<?>[] types,
        Map<String, FieldData<?>> fieldData,
        Collection<FieldData<?>> fields
) {
    public static final RepositoryInformation EMPTY = new RepositoryInformation(null, null, null, null, null, null, null, null, null);
}

package io.github.flameyossnow.universal.cassandra.query;

import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author FlameyosFlow
 */
public record CassandraTableParser(RepositoryInformation repositoryInformation, TypeResolverRegistry resolverRegistry) {
    public @NotNull String parseRepository(boolean ifNotExists) {
        String tableName = repositoryInformation.getRepositoryName();

        StringJoiner joiner = new StringJoiner(", ",
                "CREATE TABLE " + (ifNotExists ? "IF NOT EXISTS " : "") + tableName + " (",
                ");");

        generateColumns(joiner);

        return joiner.toString();
    }

    @Contract(pure = true)
    private void generateColumns(final StringJoiner joiner) {
        StringJoiner primaryKeysJoiner = new StringJoiner(", ");

        for (FieldData<?> data : repositoryInformation.getFields()) {
            final String name = data.name();
            final Class<?> type = data.type();
            final boolean primaryKey = data.primary();

            Logging.deepInfo("Processing Cassandra field: " + name);
            Logging.deepInfo("Field type: " + type);

            TypeResolver<?> resolvedType = this.resolverRegistry.getResolver(type);
            if (resolvedType == null)
                throw new IllegalArgumentException("Unsupported Cassandra type: " + type);

            joiner.add(name + ' ' + resolvedType);
            if (primaryKey) primaryKeysJoiner.add(name);
        }

        String pkClause = primaryKeysJoiner.toString();
        if (!pkClause.isEmpty()) {
            joiner.add("PRIMARY KEY (" + pkClause + ")");
        }
    }
}

package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Type resolver for PostgreSQL's native {@code uuid} column type.
 *
 * <p>Unlike the standard {@link UuidTypeResolver} which stores UUIDs as VARCHAR(36)
 * or BINARY(16), this resolver handles PostgreSQL's native UUID type directly,
 * which provides better performance and storage efficiency.</p>
 *
 * <p>PostgreSQL JDBC driver returns UUID values directly as {@link java.util.UUID}
 * objects when reading from native uuid columns.</p>
 *
 * @see SqlTypeMapping#uuidBuilder()
 */
public final class PostgreSQLUuidTypeResolver implements TypeResolver<UUID> {

    @Override
    public Class<UUID> getType() {
        return UUID.class;
    }

    @Override
    public Class<UUID> getDatabaseType() {
        return UUID.class;
    }

    @Override
    public @Nullable UUID resolve(@NotNull DatabaseResult result, String columnName) {
        // PostgreSQL JDBC driver returns UUID directly for native uuid columns
        return result.get(columnName, UUID.class);
    }

    @Override
    public void insert(@NotNull DatabaseParameters parameters, String index, UUID value) {
        // PostgreSQL JDBC driver accepts UUID directly for native uuid columns
        parameters.set(index, value, UUID.class);
    }
}

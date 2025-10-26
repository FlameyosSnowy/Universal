package io.github.flameyossnowy.universal.postgresql.internals;

import io.github.flameyossnowy.universal.api.factory.DatabaseObjectFactory;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.ImmutableList;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.api.RelationalRepositoryAdapter;
import io.github.flameyossnowy.universal.sql.internals.ObjectFactory;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import io.github.flameyossnowy.universal.sql.resolvers.SQLValueTypeResolver;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.*;

public class PostgresObjectFactory<T, ID> extends ObjectFactory<T, ID> {

    public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

    public PostgresObjectFactory(RepositoryInformation repoInfo, SQLConnectionProvider connectionProvider, @NotNull RelationalRepositoryAdapter<T, ID> adapter, TypeResolverRegistry resolverRegistry) {
        super(repoInfo, connectionProvider, adapter, resolverRegistry);
    }

    @Override
    protected Collection<Object> handleGenericListField(@NotNull FieldData<?> field, ID id, @NotNull ResultSet set) throws Exception {
        Array array = set.getArray(field.name());
        if (array == null) return List.of();

        Object[] rawArray = (Object[]) array.getArray();

        List<Object> immutableList = new ImmutableList<>(rawArray);
        return new ArrayList<>(immutableList);
    }

    @Override
    protected Object[] handleGenericArrayField(@NotNull FieldData<?> field, ID id, @NotNull ResultSet set) throws Exception {
        Array array = set.getArray(field.name());
        if (array == null) return EMPTY_OBJECT_ARRAY;

        return (Object[]) array.getArray();
    }

    @Override
    protected Set<Object> handleGenericSetField(@NotNull FieldData<?> field, ID id, @NotNull ResultSet set) throws Exception {
        Array array = set.getArray(field.name());
        if (array == null) return Set.of();

        Object[] rawArray = (Object[]) array.getArray();

        List<Object> immutableList = new ImmutableList<>(rawArray);
        return new HashSet<>(immutableList);
    }

    @Override
    public void insertEntity(PreparedStatement stmt, T entity) throws Exception {
        int paramIndex = 0;

        for (FieldData<?> field : repoInfo.getFields()) {
            if (field.autoIncrement()) continue;

            paramIndex++;
            if (checkCollection(stmt, entity, field, paramIndex)) continue;
            if (checkArray(stmt, entity, field, paramIndex)) continue;
            appendField(stmt, entity, field, paramIndex);
        }
    }

    private void appendField(PreparedStatement stmt, T entity, FieldData<?> field, int paramIndex) throws Exception {
        Objects.requireNonNull(field, "Field cannot be null");
        Objects.requireNonNull(entity, "Entity cannot be null");
        Objects.requireNonNull(stmt, "Statement cannot be null");

        Object valueToInsert = DatabaseObjectFactory.resolveInsertValue(field, entity);

        SQLValueTypeResolver<Object> resolver = getValueResolver(field);
        Objects.requireNonNull(resolver, "Missing resolver for field " + field.name());

        Logging.deepInfo("Binding parameter " + paramIndex + ": " + valueToInsert);
        resolver.insert(stmt, paramIndex, valueToInsert);
    }

    private boolean checkArray(PreparedStatement stmt, T entity, @NotNull FieldData<?> field, int paramIndex) throws SQLException {
        if (!field.type().isArray()) return false;
        Object[] valueToInsert = field.getValue(entity);
        if (valueToInsert == null) return true;

        Array sqlArray = stmt.getConnection().createArrayOf(
                this.typeResolverRegistry.getType(valueToInsert.getClass().getComponentType()),
                valueToInsert
        );
        stmt.setArray(paramIndex, sqlArray);
        return true;
    }

    private static <T> boolean checkCollection(PreparedStatement stmt, T entity, FieldData<?> field, int paramIndex) throws SQLException {
        if (!DatabaseObjectFactory.isListField(field) || !DatabaseObjectFactory.isSetField(field)) return false;

        Collection<Object> valueToInsert = field.getValue(entity);
        if (valueToInsert == null) return false;

        Object[] array = valueToInsert.toArray();
        Array sqlArray = stmt.getConnection().createArrayOf(field.name(), array);
        stmt.setArray(paramIndex, sqlArray);
        return true;
    }
}

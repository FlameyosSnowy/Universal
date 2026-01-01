package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.resolver.SqlEncoding;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.ImmutableList;
import io.github.flameyossnowy.universal.sql.DatabaseImplementation;
import io.github.flameyossnowy.universal.sql.params.SQLDatabaseParameters;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public final class ArraySupportingObjectFactory<T, ID>
        extends ObjectFactory<T, ID> {

    private static final Object[] OBJECTS = new Object[0];

    public ArraySupportingObjectFactory(RepositoryInformation repoInfo, SQLConnectionProvider connectionProvider, DatabaseImplementation implementation, RepositoryAdapter<T, ID, Connection> adapter, TypeResolverRegistry typeResolverRegistry) {
        super(repoInfo, connectionProvider, implementation, adapter, typeResolverRegistry);
    }

    @Override
    protected Collection<Object> readListField(FieldData<?> field, ID id, ResultSet rs)
            throws Exception {

        Array array = rs.getArray(field.name());
        if (array == null) return List.of();

        Object[] raw = (Object[]) array.getArray();

        // Optimization: ImmutableList doesn't copy the array, not even a null check, so we can use it directly to allow ArrayList to arraycopy it.
        return new ArrayList<>(new ImmutableList<>(raw));
    }

    @Override
    protected Collection<Object> readSetField(FieldData<?> field, ID id, ResultSet rs) throws Exception {
        Array array = rs.getArray(field.name());
        if (array == null) return List.of();

        Object[] raw = (Object[]) array.getArray();
        HashSet<Object> objects = new HashSet<>(raw.length);
        Collections.addAll(objects, raw);
        return objects;
    }

    @Override
    protected Object[] readArrayField(FieldData<?> field, ID id, ResultSet rs)
            throws Exception {

        Array array = rs.getArray(field.name());
        return array == null ? OBJECTS : (Object[]) array.getArray();
    }

    @Override
    protected boolean bindCollection(DatabaseParameters stmt, T entity,
                                     FieldData<?> field, int paramIndex)
            throws SQLException {

        Collection<Object> value = field.getValue(entity);
        if (value == null) return false;

        PreparedStatement ps = ((SQLDatabaseParameters) stmt).getStatement();
        Array sqlArray = ps.getConnection().createArrayOf(typeResolverRegistry.getType(field.type()), value.toArray());
        ps.setArray(paramIndex, sqlArray);
        return true;
    }

    @Override
    protected boolean bindArray(DatabaseParameters stmt, T entity,
                                FieldData<?> field, int paramIndex)
            throws SQLException {

        Object[] value = field.getValue(entity);
        if (value == null) return true;

        PreparedStatement ps = ((SQLDatabaseParameters) stmt).getStatement();
        Array sqlArray = ps.getConnection().createArrayOf(
                typeResolverRegistry.getType(
                        value.getClass().getComponentType(),
                        field.binary() ? SqlEncoding.BINARY : SqlEncoding.VISUAL
                ),
                value
        );
        ps.setArray(paramIndex, sqlArray);
        return true;
    }
}

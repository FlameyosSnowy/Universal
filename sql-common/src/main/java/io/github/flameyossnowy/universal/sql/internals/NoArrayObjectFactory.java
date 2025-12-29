package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.RelationalRepositoryAdapter;
import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.sql.DatabaseImplementation;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.sql.ResultSet;
import java.util.Collection;

public final class NoArrayObjectFactory<T, ID>
        extends ObjectFactory<T, ID> {

    public NoArrayObjectFactory(RepositoryInformation repoInfo, SQLConnectionProvider connectionProvider, DatabaseImplementation implementation, RelationalRepositoryAdapter<T, ID> adapter, TypeResolverRegistry typeResolverRegistry) {
        super(repoInfo, connectionProvider, implementation, adapter, typeResolverRegistry);
    }

    @Override
    protected Collection<Object> readListField(FieldData<?> field, ID id, ResultSet rs)
            throws Exception {
        Class<?> itemType = field.elementType();
        return relationshipHandler.handleNormalLists(id, itemType);
    }

    @Override
    protected Object[] readArrayField(FieldData<?> field, ID id, ResultSet rs)
            throws Exception {
        Class<?> itemType = field.arrayComponentType();
        return relationshipHandler.handleNormalArrays(id, itemType);
    }

    @Override
    protected boolean bindCollection(DatabaseParameters stmt, T entity,
                                     FieldData<?> field, int paramIndex) {
        return false; // handled later by relationship insert
    }

    @Override
    protected boolean bindArray(DatabaseParameters stmt, T entity,
                                FieldData<?> field, int paramIndex) {
        return false;
    }

    @Override
    protected Collection<Object> readSetField(FieldData<?> field, ID id, ResultSet rs) throws Exception {
        Class<?> itemType = field.elementType();
        return relationshipHandler.handleNormalSets(id, itemType);
    }
}

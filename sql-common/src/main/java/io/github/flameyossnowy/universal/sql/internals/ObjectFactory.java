package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.reflect.*;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.sql.RelationalRepositoryAdapter;
import io.github.flameyossnowy.universal.sql.resolvers.SQLValueTypeResolver;
import io.github.flameyossnowy.universal.sql.resolvers.ValueTypeResolverRegistry;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

@SuppressWarnings("unchecked")
public final class ObjectFactory<T, ID> {
    private final RepositoryInformation repositoryInformation;
    private final DatabaseRelationshipHandler<T, ID> databaseRelationshipHandler;
    private final boolean hasPrimaryKey;

    public ObjectFactory(RepositoryInformation repositoryInformation, SQLConnectionProvider connectionProvider, @NotNull RelationalRepositoryAdapter<T, ID> relationalRepositoryAdapter) {
        this.repositoryInformation = repositoryInformation;
        this.databaseRelationshipHandler = new DatabaseRelationshipHandler<>(repositoryInformation, connectionProvider, this, relationalRepositoryAdapter.getIdType());
        this.hasPrimaryKey = repositoryInformation.getPrimaryKey() != null;
    }

    public @NotNull T create(ResultSet resultSet) throws Exception {
        T instance = (T) repositoryInformation.newInstance();

        ID primaryKeyValue = null;

        for (FieldData<?> field : repositoryInformation.getFields()) {
            if (field.manyToOne() != null || field.oneToMany() != null) {
                Logging.error("ObjectFactory#create should not be used for relationships, use createWithRelationships instead.");
                continue;
            }

            if (List.class.isAssignableFrom(field.type())) {
                Field rawField = field.rawField();
                ParameterizedType type = (ParameterizedType) rawField.getGenericType();
                Class<?> rawType = (Class<?>) type.getActualTypeArguments()[0];
                databaseRelationshipHandler.handleNormalLists(field, instance, primaryKeyValue, rawType);
                continue;
            }

            if (field.primary()) {
                Object value = getValue(resultSet, field, field.name());
                if (value == null)
                    throw new IllegalArgumentException("Primary key cannot be null");
                primaryKeyValue = (ID) value;
                field.setValue(instance, value);
                continue;
            }

            populateFieldInternal(resultSet, field, instance, field.name());
        }

        return instance;
    }

    public @NotNull T createWithRelationships(ResultSet resultSet) throws Exception {
        ID primaryKeyValue = null;
        if (hasPrimaryKey) {
            FieldData<?> primaryKeyField = repositoryInformation.getPrimaryKey();
            SQLValueTypeResolver<ID> primaryKeyResolver = (SQLValueTypeResolver<ID>) ValueTypeResolverRegistry.INSTANCE.getResolver(primaryKeyField.type());
            primaryKeyValue = primaryKeyResolver.resolve(resultSet, primaryKeyField.name());
        }

        T instance = (T) repositoryInformation.newInstance();

        for (FieldData<?> field : repositoryInformation.getFields()) {
            if (field.oneToMany() != null) {
                if (!hasPrimaryKey) continue;
                Class<?> child = field.oneToMany().mappedBy();
                RepositoryInformation relatedRepoInfo = RepositoryMetadata.getMetadata(child);

                if (relatedRepoInfo == null) {
                    throw new IllegalArgumentException("Could not find repository information for " + child);
                }

                FieldData<?> primaryKey = relatedRepoInfo.getPrimaryKey();
                Class<?> primaryKeyType = getType(primaryKey, relatedRepoInfo, repositoryInformation);

                SQLValueTypeResolver<ID> resolver = (SQLValueTypeResolver<ID>)
                        ValueTypeResolverRegistry.INSTANCE.getResolver(primaryKeyType);

                databaseRelationshipHandler.handleOneToManyRelationship(
                        field, instance, primaryKeyValue, resolver, relatedRepoInfo
                );
                continue;
            }

            if (field.manyToOne() != null) {
                databaseRelationshipHandler.handleManyToOneRelationship(resultSet, field, instance);
                continue;
            }

            if (field.type() == List.class) {
                Field rawField = field.rawField();
                ParameterizedType type = (ParameterizedType) rawField.getGenericType();
                Class<?> rawType = (Class<?>) type.getActualTypeArguments()[0];
                if (hasPrimaryKey) {
                    databaseRelationshipHandler.handleNormalLists(field, instance, primaryKeyValue, rawType);
                }
                continue;
            }
            populateFieldInternal(resultSet, field, instance, field.name());
        }
        System.out.println(instance);
        return instance;
    }

    private static Class<?> getType(FieldData<?> primaryKey, RepositoryInformation repositoryInformation, RepositoryInformation parentInformation) {
        if (primaryKey != null) return primaryKey.type();
        for (FieldData<?> field : repositoryInformation.getManyToOneCache().values()) {
            if (field.type() == parentInformation.getType()) {
                return parentInformation.getPrimaryKey().type();
            }
        }
        throw new IllegalArgumentException("Could not find primary key for " + repositoryInformation.getType());
    }

    void populateFieldInternal(ResultSet resultSet, @NotNull FieldData<?> field, T instance, String alias) throws Exception {
        Object value = getValue(resultSet, field, alias);
        if (value != null) field.setValue(instance, value);
    }

    private static Object getValue(ResultSet resultSet, @NotNull FieldData<?> field, String alias) throws Exception {
        SQLValueTypeResolver<Object> resolver = (SQLValueTypeResolver<Object>) ValueTypeResolverRegistry.INSTANCE.getResolver(field.type());
        return resolver.resolve(resultSet, alias);
    }

    public void insertEntity(PreparedStatement statement, T entity) throws Exception {
        int parameterIndex = 1;

        for (FieldData<?> field : repositoryInformation.getFields()) {
            if (field.autoIncrement() || List.class.isAssignableFrom(field.type())) continue;

            Object value = field.getValue(entity);
            Object fieldValue = (value == null) ? field.defaultValue() : value;
            Logging.deepInfo("Binding parameter " + parameterIndex + ": " + fieldValue);

            RepositoryInformation relatedInfo = RepositoryMetadata.getMetadata(field.type());
            if (relatedInfo != null) {
                SQLValueTypeResolver<Object> resolver = (SQLValueTypeResolver<Object>) ValueTypeResolverRegistry.INSTANCE.getResolver(relatedInfo.getPrimaryKey().type());
                Object key = (fieldValue != null) ? relatedInfo.getPrimaryKey().getValue(fieldValue) : null;
                resolver.insert(statement, parameterIndex, key);
                Logging.deepInfo("Inserted related primary key: " + key);
                parameterIndex++;
                continue;
            }

            SQLValueTypeResolver<Object> resolver = (SQLValueTypeResolver<Object>) ValueTypeResolverRegistry.INSTANCE.getResolver(field.type());
            resolver.insert(statement, parameterIndex, fieldValue);
            parameterIndex++;
        }
    }
}

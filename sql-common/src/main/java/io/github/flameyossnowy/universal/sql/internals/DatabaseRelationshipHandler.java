package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.cache.LazyArrayList;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import io.github.flameyossnowy.universal.sql.RelationalRepositoryAdapter;
import io.github.flameyossnowy.universal.sql.resolvers.NormalCollectionTypeResolver;
import io.github.flameyossnowy.universal.sql.resolvers.SQLValueTypeResolver;
import io.github.flameyossnowy.universal.sql.resolvers.ValueTypeResolverRegistry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

import static io.github.flameyossnowy.universal.sql.internals.AbstractRelationalRepositoryAdapter.ADAPTERS;

/**
 * Utility class to be separated from ObjectFactory class to make it easier to look at with your eyes.
 */
@ApiStatus.Internal
@SuppressWarnings("unchecked")
public class DatabaseRelationshipHandler<T, ID> {
    private final RepositoryInformation repositoryInformation;
    private final SQLConnectionProvider connectionProvider;
    private final Class<ID> idClass;

    public DatabaseRelationshipHandler(RepositoryInformation information, SQLConnectionProvider connectionProvider, Class<ID> idClass) {
        this.repositoryInformation = information;
        this.connectionProvider = connectionProvider;
        this.idClass = idClass;
    }

    public void handleManyToOneRelationship(ResultSet resultSet, FieldData<?> field, T instance) throws Exception {
        RepositoryInformation parent = Objects.requireNonNull(RepositoryMetadata.getMetadata(field.type()));

        String alias = field.name();

        SQLValueTypeResolver<Object> resolver = (SQLValueTypeResolver<Object>) ValueTypeResolverRegistry.INSTANCE.getResolver(parent.getPrimaryKey().type());
        Object value = resolver.resolve(resultSet, alias);

        RelationalRepositoryAdapter<?, ?> adapter = ADAPTERS.get(parent.getType());
        Object fetched = adapter.find(Query.select().where(parent.getPrimaryKey().name(), value).build()).get(0);
        field.setValue(instance, fetched);
    }

    // for clarity
    public void handleOneToOneRelationship(ResultSet resultSet, FieldData<?> field, T instance) throws Exception {
        RepositoryInformation parent = Objects.requireNonNull(RepositoryMetadata.getMetadata(field.type()));

        SQLValueTypeResolver<Object> primaryKeyResolver = (SQLValueTypeResolver<Object>) ValueTypeResolverRegistry.INSTANCE.getResolver(parent.getPrimaryKey().type());

        SQLValueTypeResolver<ID> keyResolver = ValueTypeResolverRegistry.INSTANCE.getResolver(idClass);
        ID primaryKeyValue = keyResolver.resolve(resultSet, repositoryInformation.getPrimaryKey().name());

        String name = null;
        for (FieldData<?> relatedField : parent.getFields()) {
            //if (relatedField.type() != field.oneToMany().mappedBy()) continue;
            String alias = relatedField.name();
            if (relatedField.type() != repositoryInformation.getType()) continue;
            name = alias;
            break;
        }

        Objects.requireNonNull(name);

        String query = "SELECT * FROM " + parent.getRepositoryName() + " WHERE " + name + " = ?";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement prepareStatement = connectionProvider.prepareStatement(query, connection)) { // no need for prepared statement
            primaryKeyResolver.insert(prepareStatement, 1, primaryKeyValue);
            ResultSet set = prepareStatement.executeQuery();

            if (set.next()) {
                T relatedInstance = (T) parent.newInstance();

                for (FieldData<?> relatedField : parent.getFields()) {
                    if (relatedField.oneToOne() != null) {
                        relatedField.setValue(relatedInstance, instance);
                        continue;
                    }
                    ObjectFactory.populateFieldInternal(set, relatedField, relatedInstance);
                }

                field.setValue(instance, relatedInstance);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void handleOneToManyRelationship(@NotNull FieldData<?> field, T instance, ID primaryKeyValue, SQLValueTypeResolver<ID> primaryKeyResolver, RepositoryInformation relatedRepoInfo) {
        if (!field.oneToMany().lazy()) {
            String name = null;

            for (FieldData<?> relatedField : relatedRepoInfo.getManyToOneCache().values()) {
                //if (relatedField.type() != field.oneToMany().mappedBy()) continue;
                String alias = relatedField.name();
                if (relatedField.type() != repositoryInformation.getType()) continue;
                name = alias;
                break;

            }

            String query = "SELECT * FROM "
                    + relatedRepoInfo.getRepositoryName()
                    + " WHERE " + name + " = ?";

            try (Connection connection = connectionProvider.getConnection();
                 PreparedStatement prepareStatement = connectionProvider.prepareStatement(query, connection)) { // no need for prepared statement
                primaryKeyResolver.insert(prepareStatement, 1, primaryKeyValue);
                ResultSet set = prepareStatement.executeQuery();
                List<Object> list = new ArrayList<>(set.getFetchSize());
                while (set.next()) {
                    T relatedInstance = (T) relatedRepoInfo.newInstance();

                    for (FieldData<?> relatedField : relatedRepoInfo.getFields()) {
                        if (relatedField.manyToOne() != null) {
                            relatedField.setValue(relatedInstance, instance);
                            continue;
                        }
                        ObjectFactory.populateFieldInternal(set, relatedField, relatedInstance);
                    }
                    list.add(relatedInstance);
                }
                field.setValue(instance, list);
                return;

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        field.setValue(instance, new LazyArrayList<>(() -> {
            SelectQuery selectQuery = null;
            FieldData<?> relatedKeyField = null;

            for (FieldData<?> relatedField : relatedRepoInfo.getManyToOneCache().values()) {
                //if (relatedField.type() != field.oneToMany().mappedBy()) continue;
                String alias = relatedField.name();

                if (relatedField.type() != repositoryInformation.getType()) continue;
                relatedKeyField = relatedField;
                selectQuery = Query.select().where(alias, primaryKeyValue).build();
                break;

            }
            if (relatedKeyField == null || selectQuery == null) throw new IllegalArgumentException("No ManyToOne annotation found.");
            var data = (List<Object>) ADAPTERS.get(relatedRepoInfo.getType()).find(selectQuery);
            for (Object element : data) relatedKeyField.setValue(element, instance);
            return data;
        }));
    }

    public void handleNormalLists(@NotNull FieldData<?> field, T instance, ID value, Class<?> rawType) {
        NormalCollectionTypeResolver<T, ID> collectionTypeResolver = (NormalCollectionTypeResolver<T, ID>) SQLCollections.INSTANCE.getResolver(rawType, idClass, connectionProvider, repositoryInformation);
        field.setValue(instance, collectionTypeResolver.resolve(value));
    }
}

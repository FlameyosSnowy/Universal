package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.cache.FetchedDataResult;
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
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static io.github.flameyossnowy.universal.sql.internals.AbstractRelationalRepositoryAdapter.ADAPTERS;

/**
 * Utility class to be separated from ObjectFactory class to make it easier to look at with your eyes.
 */
@SuppressWarnings("unchecked")
public class DatabaseRelationshipHandler<T, ID> {
    private final RepositoryInformation repositoryInformation;
    private final SQLConnectionProvider connectionProvider;
    private final ObjectFactory<T, ID> objectFactory;
    private final Class<ID> idClass;

    public DatabaseRelationshipHandler(RepositoryInformation information, SQLConnectionProvider connectionProvider, ObjectFactory<T, ID> factory, Class<ID> idClass) {
        this.repositoryInformation = information;
        this.connectionProvider = connectionProvider;
        this.objectFactory = factory;
        this.idClass = idClass;
    }

    public void handleManyToOneRelationship(ResultSet resultSet, FieldData<?> field, T instance) throws Exception {
        RepositoryInformation parent = RepositoryMetadata.getMetadata(field.type());

        String alias = field.name();

        SQLValueTypeResolver<Object> resolver = (SQLValueTypeResolver<Object>) ValueTypeResolverRegistry.INSTANCE.getResolver(parent.getPrimaryKey().type());
        Object value = resolver.resolve(resultSet, alias);

        RelationalRepositoryAdapter<?, ?> adapter = ADAPTERS.get(parent.getType());
        Object fetched = adapter.find(Query.select().where(parent.getPrimaryKey().name(), value).build()).first();
        field.setValue(instance, fetched);
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

            List<Object> list = new ArrayList<>();
            try (Connection connection = connectionProvider.getConnection();
                 PreparedStatement prepareStatement = connectionProvider.prepareStatement(query, connection)) { // no need for prepared statement
                primaryKeyResolver.insert(prepareStatement, 1, primaryKeyValue);
                ResultSet set = prepareStatement.executeQuery();
                while (set.next()) {
                    T relatedInstance = (T) relatedRepoInfo.newInstance();

                    for (FieldData<?> relatedField : relatedRepoInfo.getFields()) {
                        if (relatedField.manyToOne() != null) {
                            relatedField.setValue(relatedInstance, instance);
                            continue;
                        }
                        objectFactory.populateFieldInternal(set, relatedField, relatedInstance, relatedField.name());
                    }
                    list.add(relatedInstance);
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            field.setValue(instance, list);
            return;
        }

        field.setValue(instance, new LazyArrayList<>(() -> {
            List<Object> list = new ArrayList<>();
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
            var data = (FetchedDataResult<Object, Object>) ADAPTERS.get(relatedRepoInfo.getType()).find(selectQuery);
            for (Object element : data) {
                relatedKeyField.setValue(element, instance);
                list.add(element);
            }
            return list;
        }));
    }

    public void handleNormalLists(@NotNull FieldData<?> field, T instance, ID value, Class<?> rawType) {
        NormalCollectionTypeResolver<T, ID> collectionTypeResolver = (NormalCollectionTypeResolver<T, ID>) SQLCollections.INSTANCE.getResolver(rawType, idClass, connectionProvider, repositoryInformation);
        field.setValue(instance, collectionTypeResolver.resolve(value));
    }
}

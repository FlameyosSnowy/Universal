package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.cache.LazyArrayList;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.OneToOneField;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import io.github.flameyossnowy.universal.sql.RelationalRepositoryAdapter;
import io.github.flameyossnowy.universal.sql.resolvers.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final Map<String, String> nameCache = new ConcurrentHashMap<>(16);

    public DatabaseRelationshipHandler(RepositoryInformation information, SQLConnectionProvider connectionProvider, Class<ID> idClass) {
        this.repositoryInformation = information;
        this.connectionProvider = connectionProvider;
        this.idClass = idClass;
    }

    public static Object handleManyToOneRelationship(ResultSet resultSet, FieldData<?> field) throws Exception {
        RepositoryInformation parent = Objects.requireNonNull(RepositoryMetadata.getMetadata(field.type()));

        String alias = field.name();

        SQLValueTypeResolver<Object> resolver = (SQLValueTypeResolver<Object>) ValueTypeResolverRegistry.INSTANCE.getResolver(parent.getPrimaryKey().type());
        Object value = resolver.resolve(resultSet, alias);

        RelationalRepositoryAdapter<?, ?> adapter = ADAPTERS.get(parent.getType());
        return adapter.find(Query.select().where(parent.getPrimaryKey().name(), value).build()).get(0);
    }

    public Object handleOneToOneRelationship(ResultSet resultSet, FieldData<?> field) throws Exception {
        RepositoryInformation parent = Objects.requireNonNull(RepositoryMetadata.getMetadata(field.type()));

        SQLValueTypeResolver<Object> primaryKeyResolver = (SQLValueTypeResolver<Object>) ValueTypeResolverRegistry.INSTANCE.getResolver(parent.getPrimaryKey().type());

        SQLValueTypeResolver<ID> keyResolver = ValueTypeResolverRegistry.INSTANCE.getResolver(idClass);
        ID primaryKeyValue = keyResolver.resolve(resultSet, repositoryInformation.getPrimaryKey().name());

        OneToOneField result = getOneToOneField(parent, repositoryInformation);

        String query = "SELECT * FROM " + parent.getRepositoryName() + " WHERE " + result.name() + " = ? LIMIT 1";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement prepareStatement = connectionProvider.prepareStatement(query, connection)) { // no need for prepared statement
            primaryKeyResolver.insert(prepareStatement, 1, primaryKeyValue);
            ResultSet set = prepareStatement.executeQuery();

            if (set.next()) {
                return ADAPTERS.get(parent.getType()).getObjectFactory().create(set);
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Contract("_, _ -> new")
    static @NotNull OneToOneField getOneToOneField(@NotNull RepositoryInformation parent, RepositoryInformation repositoryInformation) {
        String name = null;
        FieldData<?> foundRelatedField = null;
        for (FieldData<?> relatedField : parent.getFields()) {
            //if (relatedField.type() != field.oneToMany().mappedBy()) continue;
            String alias = relatedField.name();
            if (relatedField.type() != repositoryInformation.getType()) continue;
            name = alias;
            foundRelatedField = relatedField;
            break;
        }
        return new OneToOneField(name, foundRelatedField);
    }

    public List<Object> handleOneToManyRelationship(@NotNull FieldData<?> field, ID primaryKeyValue, SQLValueTypeResolver<ID> primaryKeyResolver, RepositoryInformation relatedRepoInfo) {
        if (!field.oneToMany().lazy()) {
            String name = getName(relatedRepoInfo, relatedRepoInfo.getManyToOneCache().values());

            return handleOneToMany0(relatedRepoInfo, primaryKeyValue, primaryKeyResolver, name);
        }

        return new LazyArrayList<>(() -> {
            String name = getName(relatedRepoInfo, relatedRepoInfo.getFields());

            Objects.requireNonNull(name);

            return handleOneToMany0(relatedRepoInfo, primaryKeyValue, primaryKeyResolver, name);
        });
    }

    private static String getName(RepositoryInformation relatedRepoInfo, Iterable<FieldData<?>> fields) {
        // Generate a unique cache key based on the relatedRepoInfo and fields (this can be customized to suit your needs)
        String cacheKey = generateCacheKey(relatedRepoInfo, fields);

        // Check if the result is already cached
        if (nameCache.containsKey(cacheKey)) {
            return nameCache.get(cacheKey);
        }

        for (FieldData<?> relatedField : fields) {
            if (relatedField.type() != relatedRepoInfo.getType()) continue;

            String alias = relatedField.name();

            nameCache.put(cacheKey, alias);

            return alias;
        }

        nameCache.put(cacheKey, null);
        return null;
    }

    // Utility method to generate a unique key for caching
    private static String generateCacheKey(RepositoryInformation relatedRepoInfo, Iterable<FieldData<?>> fields) {
        // Example: Combine the repository name and field names to generate a unique key
        StringBuilder keyBuilder = new StringBuilder(relatedRepoInfo.getType().getName());

        for (FieldData<?> field : fields) {
            keyBuilder.append("_").append(field.name());
        }

        return keyBuilder.toString();
    }

    public Collection<Object> handleNormalLists(ID value, Class<?> rawType) {
        CollectionTypeResolver<Object, ID> collectionTypeResolver = (CollectionTypeResolver<Object, ID>)
                SQLCollections.INSTANCE.getResolver(rawType, idClass, connectionProvider, repositoryInformation);
        return collectionTypeResolver.resolve(value);
    }

    public Object[] handleNormalArrays(ID value, Class<?> rawType) {
        CollectionTypeResolver<Object, ID> collectionTypeResolver = (CollectionTypeResolver<Object, ID>)
                SQLCollections.INSTANCE.getResolver(rawType, idClass, connectionProvider, repositoryInformation);
        return collectionTypeResolver.resolve(value).toArray();
    }

    public Collection<Object> handleNormalSets(ID value, Class<?> rawType) {
        CollectionTypeResolver<Object, ID> collectionTypeResolver = (CollectionTypeResolver<Object, ID>)
                SQLCollections.INSTANCE.getResolver(rawType, idClass, connectionProvider, repositoryInformation);
        return collectionTypeResolver.resolveSet(value);
    }

    public Map<Object, Object> handleNormalMap(ID value, Class<?> rawKeyType, Class<?> rawValueType) {
        MapTypeResolver<Object, Object, ID> collectionTypeResolver = (MapTypeResolver<Object, Object, ID>)
                SQLCollections.INSTANCE.getMapResolver(
                        rawKeyType, rawValueType, idClass,
                        connectionProvider, repositoryInformation
                );
        return collectionTypeResolver.resolve(value);
    }

    public Map<Object, List<Object>> handleMultiMap(ID value,
                               Class<?> rawKeyType, Class<?> rawValueType) {
        MultiMapTypeResolver<Object, Object, ID> collectionTypeResolver = (MultiMapTypeResolver<Object, Object, ID>)
                SQLCollections.INSTANCE.getMultiMapResolver(
                        rawKeyType, rawValueType, idClass,
                        connectionProvider, repositoryInformation
                );
        return collectionTypeResolver.resolve(value);
    }

    private List<Object> handleOneToMany0(RepositoryInformation relatedRepoInfo,
                                          ID primaryKeyValue,
                                          SQLValueTypeResolver<ID> primaryKeyResolver,
                                          String name) {
        String query = "SELECT * FROM "
                + relatedRepoInfo.getRepositoryName()
                + " WHERE " + name + " = ?";

        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement prepareStatement = connectionProvider.prepareStatement(query, connection)) { // no need for prepared statement
            primaryKeyResolver.insert(prepareStatement, 1, primaryKeyValue);
            ResultSet set = prepareStatement.executeQuery();
            List<Object> list = new ArrayList<>(set.getFetchSize());
            while (set.next()) {
                Object relatedInstance = ADAPTERS.get(relatedRepoInfo.getType()).getObjectFactory().create(set);
                list.add(relatedInstance);
            }
            return list;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

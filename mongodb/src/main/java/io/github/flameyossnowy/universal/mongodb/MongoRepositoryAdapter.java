package io.github.flameyossnowy.universal.mongodb;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import io.github.flameyossnowy.universal.api.*;
import io.github.flameyossnowy.universal.api.IndexOptions;
import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;
import io.github.flameyossnowy.universal.api.cache.*;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.exceptions.handler.DefaultExceptionHandler;
import io.github.flameyossnowy.universal.api.exceptions.handler.ExceptionHandler;
import io.github.flameyossnowy.universal.api.factory.ObjectModel;
import io.github.flameyossnowy.universal.api.factory.RelationshipLoader;
import io.github.flameyossnowy.universal.api.handler.AbstractRelationshipHandler;
import io.github.flameyossnowy.universal.api.handler.RelationshipHandler;
import io.github.flameyossnowy.universal.api.listener.AuditLogger;
import io.github.flameyossnowy.universal.api.listener.EntityLifecycleListener;
import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.GeneratedMetadata;
import io.github.flameyossnowy.universal.api.meta.GeneratedObjectFactories;
import io.github.flameyossnowy.universal.api.meta.GeneratedRelationshipLoaders;
import io.github.flameyossnowy.universal.api.meta.GeneratedValueReaders;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.operation.OperationContext;
import io.github.flameyossnowy.universal.api.operation.OperationExecutor;
import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.options.validator.QueryValidator;
import io.github.flameyossnowy.universal.api.options.validator.ValidationEstimation;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.api.json.JsonCodec;
import io.github.flameyossnowy.universal.mongodb.codec.DelegatingMongoCodecProvider;
import io.github.flameyossnowy.universal.mongodb.codec.MongoJsonCodecBridge;
import io.github.flameyossnowy.universal.mongodb.codec.MongoTypeCodecProvider;
import io.github.flameyossnowy.universal.mongodb.params.MongoDatabaseParameters;
import io.github.flameyossnowy.universal.mongodb.query.MongoQueryValidator;
import io.github.flameyossnowy.universal.mongodb.result.MongoDatabaseResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.*;
import org.bson.codecs.*;
import org.bson.codecs.configuration.*;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.LongFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.mongodb.client.model.Filters.*;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

@SuppressWarnings({ "unused", "unchecked" })
public class MongoRepositoryAdapter<T, ID> implements RepositoryAdapter<T, ID, ClientSession> {
    private final MongoClient client;

    private static final Document EMPTY = new Document();

    MongoCollection<Document> collection;
    private final RepositoryModel<T, ID> repositoryModel;
    private final ObjectModel<T, ID> objectModel;
    private final RelationshipLoader<T, ID> relationshipLoader;
    private final OperationContext<T, ID, ClientSession> operationContext;
    private final OperationExecutor<T, ID, ClientSession> operationExecutor;
    private final Class<ID> idType;
    private final MongoCollectionHandler collectionHandler;

    @Nullable
    private final DefaultResultCache<Bson, T, ID> resultCache;

    @Nullable
    private final SecondLevelCache<ID, T> l2Cache;

    @Nullable
    private final ReadThroughCache<ID, T> readThroughCache;

    private final Logger logger = LoggerFactory.getLogger(MongoRepositoryAdapter.class);

    private static final Set<Class<?>> NUMBERS = Set.of(
            Integer.class, Long.class, Float.class, Double.class,
            Short.class, Byte.class, Character.class, int.class, long.class,
            float.class, double.class, short.class, byte.class, char.class
    );

    @Nullable
    private final AuditLogger<T> auditLogger;

    @Nullable
    private final EntityLifecycleListener<T> entityLifecycleListener;

    @Nullable
    private final SessionCache<ID, T> globalCache;
    private long openSessions = 1;

    private final Class<T> elementType;

    private final LongFunction<SessionCache<ID, T>> sessionCacheSupplier;

    private final ExceptionHandler<T, ID, ClientSession> exceptionHandler;
    private final TypeResolverRegistry typeResolverRegistry = new TypeResolverRegistry();
    private final QueryValidator queryValidator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static String mongoPrimaryKeyName(@NotNull FieldModel<?> primaryKey) {
        // MongoDB reserves the document primary key field name as "_id".
        // Many models use a logical field name "id"; if metadata doesn't override the column name,
        // we map it here to avoid mismatches between queries and stored documents.
        String col = primaryKey.columnName();
        if (primaryKey.id()) {
            return "_id";
        }
        return col;
    }

    MongoRepositoryAdapter(
        @NotNull MongoClientSettings.Builder clientBuilder,
        String dbName,
        Class<T> repo,
        Class<ID> idType,
        @Nullable SessionCache<ID, T> sessionCache,
        LongFunction<SessionCache<ID, T>> sessionCacheSupplier,
        CacheWarmer<T, ID> cacheWarmer,
        MongoClient client
    ) {
        this.repositoryModel = GeneratedMetadata.getByEntityClass(repo);
        if (repositoryModel == null)
            throw new IllegalArgumentException("Unable to find repository information for " + repo.getSimpleName());

        FieldModel<T> primaryKey = repositoryModel.getPrimaryKey();
        if (primaryKey != null && (NUMBERS.contains(primaryKey.type()) || primaryKey.autoIncrement()))
            throw new IllegalArgumentException("Primary key must not be of type number and/or must not be auto-increment");

        this.idType = idType;
        this.elementType = repo;
        this.globalCache = sessionCache;
        this.sessionCacheSupplier = sessionCacheSupplier;

        ExceptionHandler<T, ID, ClientSession> uncheckedHandler = (ExceptionHandler<T, ID, ClientSession>) repositoryModel.getExceptionHandler();
        this.exceptionHandler = uncheckedHandler == null ? new DefaultExceptionHandler<>() : uncheckedHandler;

        // Enable DefaultJsonCodec usage (JsonCodec instantiation may require ObjectMapper)
        this.typeResolverRegistry.setObjectMapperSupplier(() -> objectMapper);
        this.queryValidator = new MongoQueryValidator(repositoryModel);

        this.operationExecutor = new MongoOperationExecutor<>(this);
        this.operationContext = new OperationContext<>(repositoryModel, typeResolverRegistry, this.operationExecutor);

        RepositoryRegistry.register(this.repositoryModel.tableName(), this);
        CacheConfig cacheConfig = repositoryModel.getCacheConfig();
        if (cacheConfig != null && cacheConfig.isEnabled()) {
            this.resultCache = new DefaultResultCache<>(cacheConfig.maxSize(), cacheConfig.cacheAlgorithmType());
            this.l2Cache = new SecondLevelCache<>(cacheConfig.maxSize(), 300000, cacheConfig.cacheAlgorithmType());
            this.readThroughCache = new ReadThroughCache<>(cacheConfig.maxSize(), cacheConfig.cacheAlgorithmType(), this::loadFromDatabase);
        } else {
            this.resultCache = null;
            this.l2Cache = null;
            this.readThroughCache = null;
        }

        this.entityLifecycleListener = repositoryModel.getEntityLifecycleListener();
        this.auditLogger = repositoryModel.getAuditLogger();

        for (Class<? extends TypeResolver<?>> resolverClass : repositoryModel.getRequiredResolvers()) {
            try {
                this.typeResolverRegistry.register(resolverClass.getDeclaredConstructor().newInstance());
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate TypeResolver: " + resolverClass, e);
            }
        }

        DelegatingMongoCodecProvider delegatingProvider = new DelegatingMongoCodecProvider();
        this.client = Objects.requireNonNullElseGet(client, () -> {
            CodecRegistry registry = CodecRegistries.fromRegistries(
                CodecRegistries.fromProviders(
                    delegatingProvider,
                    PojoCodecProvider.builder().automatic(true).build()
                ),
                MongoClientSettings.getDefaultCodecRegistry()
            );

            clientBuilder.codecRegistry(registry).uuidRepresentation(UuidRepresentation.STANDARD);
            return MongoClients.create(clientBuilder.build());
        });

        MongoDatabase database = this.client.getDatabase(dbName);
        this.collection = database.getCollection(repositoryModel.tableName());
        this.collectionHandler = new MongoCollectionHandler(database);
        MongoTypeCodecProvider runtimeContext = new MongoTypeCodecProvider(
            typeResolverRegistry,
            collectionHandler,
            repositoryModel
        );
        delegatingProvider.bind(runtimeContext);

        RelationshipHandler<T, ID> relationshipHandler = new MongoRelationshipHandler<>(repositoryModel, idType, typeResolverRegistry);
        this.relationshipLoader = GeneratedRelationshipLoaders.get(
            repositoryModel.tableName(),
            relationshipHandler,
            collectionHandler,
            repositoryModel
        );

        this.objectModel = GeneratedObjectFactories.getObjectModel(repositoryModel);

        List<IndexOptions> queuedIndexes = initializeIndexes(repositoryModel);
        for (IndexOptions idx : queuedIndexes) {
            createIndex(idx).expect("Should be able to create index successfully");
        }

        if (cacheWarmer != null) {
            cacheWarmer.warmCache(this);
        }
    }

    private static <T, ID> @NotNull List<IndexOptions> initializeIndexes(RepositoryModel<T, ID> repositoryModel) {
        List<IndexOptions> queued = new ArrayList<>();
        for (FieldModel<T> field : repositoryModel.fields()) {
            if (field.hasUniqueAnnotation()) {
                queued.add(IndexOptions.builder(repositoryModel.getEntityClass())
                    .type(IndexType.UNIQUE)
                    .field(field)
                    .build());
            }
        }
        return queued;
    }

    private ExceptionHandler<T, ID, ClientSession> createExceptionHandler() {
        return repositoryModel.getExceptionHandler() != null
            ? (ExceptionHandler<T, ID, ClientSession>) repositoryModel.getExceptionHandler()
            : new DefaultExceptionHandler<>();
    }

    private static @NotNull CodecRegistry getProvider(CodecProvider pojo, @NotNull List<Codec<?>> codecs, CodecRegistry registry) {
        return codecs.isEmpty() ? fromProviders(registry, pojo) : fromProviders(registry, pojo, CodecRegistries.fromCodecs(codecs));
    }

    public static <T, ID> @NotNull MongoRepositoryAdapterBuilder<T, ID> builder(Class<T> repo, Class<ID> id) {
        return new MongoRepositoryAdapterBuilder<>(repo, id);
    }

    @Override
    public List<T> find(@NotNull SelectQuery query) {
        return find(query, ReadPolicy.NO_READ_POLICY);
    }

    @Override
    public List<T> find(@NotNull SelectQuery query, ReadPolicy policy) {
        // Validate query
        ValidationEstimation validation = queryValidator.validateSelectQuery(query);
        if (validation.isFail()) {
            Logging.warn("Query validation failed: " + validation.reason());
            return List.of();
        }

        Bson filterDoc = createFilterBson(query.filters());
        boolean bypassCache = policy != null && policy.bypassCache();
        if (!bypassCache && resultCache != null) {
            List<T> cached = resultCache.fetch(filterDoc);
            if (cached != null) return cached;
        }

        FindIterable<Document> iterable = process(query, collection.find(filterDoc), repositoryModel.getFetchPageSize());
        if (query.limit() == 1) {
            Document doc = iterable.first();

            if (doc == null) {
                return List.of();
            }

            MongoDatabaseResult databaseResult = new MongoDatabaseResult(doc, collectionHandler, repositoryModel);

            FieldModel<T> primaryKey = repositoryModel.getPrimaryKey();
            if (primaryKey == null) {
                throw new IllegalArgumentException("Primary key not found for " + repositoryModel.tableName());
            }

            String pk = mongoPrimaryKeyName(primaryKey);

            Object rawId = databaseResult.get(pk, Object.class);

            ID id;
            try {
                id = typeResolverRegistry.resolve(idType).resolve(databaseResult, pk);
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }

            T result = objectModel.construct(GeneratedValueReaders.get(repositoryModel.tableName(), databaseResult, typeResolverRegistry, id));
            objectModel.populateRelationships(result, id, relationshipLoader);
            List<T> single = List.of(result);
            if (resultCache != null) {
                resultCache.insert(filterDoc, single, objectModel::getId);
            }
            return single;
        }

        try (MongoCursor<Document> cursor = iterable.iterator()) {
            List<T> results = new ArrayList<>(cursor.available());
            while (cursor.hasNext()) {
                results.add(createObject(cursor));
            }
            if (resultCache != null) {
                resultCache.insert(filterDoc, results, objectModel::getId);
            }
            return results;
        }
    }

    private T createObject(@NotNull MongoCursor<Document> cursor) {
        MongoDatabaseResult databaseResult = new MongoDatabaseResult(cursor.next(), collectionHandler, repositoryModel);
        FieldModel<T> primaryKey = repositoryModel.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryModel.tableName());
        }
        String pk = mongoPrimaryKeyName(primaryKey);
        ID id = typeResolverRegistry.resolve(idType).resolve(databaseResult, pk);
        T construct = objectModel.construct(GeneratedValueReaders.get(repositoryModel.tableName(), databaseResult, typeResolverRegistry, id));
        objectModel.populateRelationships(construct, id, relationshipLoader);
        return construct;
    }

    @Override
    public List<T> find() {
        return find(ReadPolicy.NO_READ_POLICY);
    }

    @Override
    public List<T> find(ReadPolicy policy) {
        FieldModel<T> primaryKey = repositoryModel.getPrimaryKey();
        boolean bypassCache = policy != null && policy.bypassCache();

        if (!bypassCache && resultCache != null) {
            if (primaryKey == null) {
                throw new IllegalArgumentException("Primary key not found for " + repositoryModel.tableName());
            }

            List<T> cached = resultCache.fetch(EMPTY);
            if (cached != null) return cached;
        }

        try (MongoCursor<Document> iterable = collection.find().iterator()) {
            List<T> results = new ArrayList<>(iterable.available());
            while (iterable.hasNext()) {
                Document doc = iterable.next();
                MongoDatabaseResult databaseResult = new MongoDatabaseResult(doc, collectionHandler, repositoryModel);
                FieldModel<T> pkField = repositoryModel.getPrimaryKey();
                if (pkField == null) {
                    throw new IllegalArgumentException("Primary key not found for " + repositoryModel.tableName());
                }
                String pk = mongoPrimaryKeyName(pkField);
                ID id = typeResolverRegistry.resolve(idType).resolve(databaseResult, pk);
                T construct = objectModel.construct(GeneratedValueReaders.get(repositoryModel.tableName(), databaseResult, typeResolverRegistry, id));
                objectModel.populateRelationships(construct, id, relationshipLoader);
                results.add(construct);
            }

            if (resultCache != null) resultCache.insert(EMPTY, results, objectModel::getId);
            return results;
        }
    }

    @Override
    public long count(SelectQuery query, ReadPolicy policy) {
        if (query != null && query.limit() == 0) {
            return 0L;
        }

        if (query != null) {
            ValidationEstimation validation = queryValidator.validateSelectQuery(query);
            if (validation.isFail()) {
                Logging.warn("Query validation failed: " + validation.reason());
                return 0L;
            }
        }

        Bson filter = query == null ? new Document() : createFilterBson(query.filters());
        return collection.countDocuments(filter);
    }

    @Override
    public long count(ReadPolicy policy) {
        return count(null, policy);
    }

    @Override
    public @Nullable T findById(ID key) {
        if (readThroughCache == null) {
            return this.loadFromDatabase(key);
        }
        T cached = null;
        if (l2Cache != null) {
            cached = l2Cache.get(key);
        }
        if (cached != null) {
            Logging.deepInfo(() -> "L2 cache hit for " + repositoryModel.tableName() + " id=" + key);
            return cached;
        }

        T value = readThroughCache.get(key);
        if (value != null) {
            if (l2Cache != null) {
                l2Cache.put(key, value);
            }
            return value;
        }

        return null;
    }

    @Override
    public Map<ID, T> findAllById(Collection<ID> keys) {
        FieldModel<T>  primaryKey = repositoryModel.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryModel.tableName());
        }

        if (keys.isEmpty()) return Map.of();

        Map<ID, T> result = new HashMap<>(keys.size());
        List<ID> missing = new ArrayList<>(8);

        // Identity map first
        for (ID id : keys) {
            T cached = null;
            if (globalCache != null) {
                cached = globalCache.get(id);
            }
            if (cached != null) {
                result.put(id, cached);
            } else {
                missing.add(id);
            }
        }

        if (missing.isEmpty()) return result;

        String pk = repositoryModel.getPrimaryKey().name();
        FindIterable<Document> iterable =
            collection.find(in(pk, missing));

        try (MongoCursor<Document> cursor = iterable.iterator()) {
            while (cursor.hasNext()) {
                T construct = createObject(cursor);
                ID id = this.objectModel.getId(construct);
                result.put(id, construct);
                if (globalCache != null) {
                    globalCache.put(id, construct);
                }
            }
        }

        return result;
    }

    @Override
    public @NotNull CloseableIterator<T> findIterator(@NotNull SelectQuery query) {
        Bson filter = createFilterBson(query.filters());
        FindIterable<Document> iterable = process(query, collection.find(filter), repositoryModel.getFetchPageSize());
        MongoCursor<Document> cursor = iterable.iterator();

        return new CloseableIterator<>() {
            @Override
            public boolean hasNext() {
                boolean has = cursor.hasNext();
                if (!has) close();
                return has;
            }

            @Override
            public T next() {
                return createObject(cursor);
            }

            @Override
            public void close() {
                cursor.close();
            }
        };
    }

    @Override
    public @NotNull Stream<T> findStream(@NotNull SelectQuery query) {
        CloseableIterator<T> iterator = findIterator(query);

        // Convert iterator to stream and ensure close is called when stream ends
        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(iterator, 0),
            false
        ).onClose(() -> {
            try {
                iterator.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private T loadFromDatabase(ID key) {
        FieldModel<T>  primaryKey = repositoryModel.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryModel.tableName());
        }

        String pk = mongoPrimaryKeyName(primaryKey);
        Document filter = new Document(pk, key);
        MongoDatabaseResult databaseResult = new MongoDatabaseResult(collection.find(filter).first(), collectionHandler, repositoryModel);
        ID id = typeResolverRegistry.resolve(idType).resolve(databaseResult, pk);
        T construct = objectModel.construct(GeneratedValueReaders.get(repositoryModel.tableName(), databaseResult, typeResolverRegistry, id));
        objectModel.populateRelationships(construct, id, relationshipLoader);
        if (construct != null) {
            if (resultCache != null) {
                resultCache.insert(filter, List.of(construct), objectModel::getId);
            }
        }
        return construct;
    }

    @Override
    public T first(SelectQuery query) {
        MongoDatabaseResult databaseResult = new MongoDatabaseResult(search(query).first(), collectionHandler, repositoryModel);
        FieldModel<T> primaryKey = repositoryModel.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryModel.tableName());
        }
        String pk = mongoPrimaryKeyName(primaryKey);
        ID id = typeResolverRegistry.resolve(idType).resolve(databaseResult, pk);
        T construct = objectModel.construct(GeneratedValueReaders.get(repositoryModel.tableName(), databaseResult, typeResolverRegistry, id));
        objectModel.populateRelationships(construct, id, relationshipLoader);
        return construct;
    }

    private FindIterable<Document> search(SelectQuery query) {
        return process(query, collection.find(createFilterBson(query.filters())), repositoryModel.getFetchPageSize());
    }

    private static <T> FindIterable<T> process(@NotNull SelectQuery query, FindIterable<T> iterable, int pageSize) {
        if (pageSize > 0) iterable = iterable.batchSize(pageSize);
        if (query.limit() != -1) iterable = iterable.limit(query.limit());
        if (!query.sortOptions().isEmpty()) {
            List<Bson> sorts = new ArrayList<>();
            for (SortOption o : query.sortOptions()) {
                Bson bson = o.order() == SortOrder.ASCENDING ? Sorts.ascending(o.field()) : Sorts.descending(o.field());
                sorts.add(bson);
            }
            iterable = iterable.sort(Sorts.orderBy(sorts));
        }
        return iterable;
    }

    private void processExpression(@NotNull List<Bson> filters, @NotNull FilterOption option) {
        if (option instanceof SelectOption(String option1, String operator1, Object value)) {
            filters.add(buildFilter(option1, operator1, value));
            return;
        }

        if (option instanceof JsonSelectOption(String field, String path, String operator, Object rawValue)) {
            String dotPath = path != null && path.startsWith("$.") ? path.substring(2) : path;
            String key = field + "." + dotPath;

            Object bindValue = rawValue;
            FieldModel<?> jsonField = repositoryModel.fieldByName(field);
            if (jsonField != null && jsonField.isJson() && rawValue != null && jsonField.type().isInstance(rawValue)) {
                JsonCodec<Object> codec = typeResolverRegistry.getJsonCodec(jsonField.jsonCodec());
                String json = codec.serialize(rawValue, (Class<Object>) jsonField.type());
                bindValue = MongoJsonCodecBridge.jsonToBsonFriendly(json);
            }

            filters.add(buildFilter(key, operator, bindValue));
            return;
        }

        throw new IllegalArgumentException("Unsupported filter option type: " + option.getClass().getName());
    }

    private static Bson buildFilter(String key, String operator, Object value) {
        if (operator == null) {
            throw new IllegalArgumentException("Operator must not be null");
        }

        return switch (operator.toUpperCase()) {
            case "=", "EQ" -> eq(key, value);

            case "!=", "NE" -> ne(key, value);

            case ">", "GT" -> gt(key, value);

            case ">=", "GTE" -> gte(key, value);

            case "<", "LT" -> lt(key, value);

            case "<=", "LTE" -> lte(key, value);

            case "IN" -> {
                if (!(value instanceof Collection<?> c)) {
                    throw new IllegalArgumentException("IN requires a Collection value");
                }
                yield in(key, c);
            }

            case "NOT IN", "NIN" -> {
                if (!(value instanceof Collection<?> c)) {
                    throw new IllegalArgumentException("NIN requires a Collection value");
                }
                yield nin(key, c);
            }

            case "REGEX" -> regex(key, String.valueOf(value));

            case "EXISTS" -> exists(
                key,
                value == null || Boolean.TRUE.equals(value)
            );

            case "TYPE" -> {
                if (value instanceof BsonType t) {
                    yield type(key, t);
                }

                if (value instanceof String s) {
                    yield type(key, s);
                }

                throw new IllegalArgumentException(
                    "TYPE requires a BsonType or String value"
                );
            }

            case "SIZE" -> {
                if (!(value instanceof Number n)) {
                    throw new IllegalArgumentException("SIZE requires a numeric value");
                }
                yield size(key, n.intValue());
            }

            case "ALL" -> {
                if (!(value instanceof Collection<?> c)) {
                    throw new IllegalArgumentException("ALL requires a Collection value");
                }
                yield all(key, c);
            }

            case "ELEM_MATCH" -> {
                if (!(value instanceof Bson b)) {
                    throw new IllegalArgumentException("ELEM_MATCH requires a Bson value");
                }
                yield elemMatch(key, b);
            }

            case "MATCHES" -> {
                if (!(value instanceof String keyValue)) {
                    throw new IllegalArgumentException("MATCHES requires a String value");
                }
                yield text(keyValue);
            }

            default -> throw new IllegalArgumentException(
                "Unsupported filter operation: " + operator
            );
        };
    }

    public BsonDocument createFilterBson(List<FilterOption> options) {
        if (options.isEmpty()) {
            return new BsonDocument();
        }

        List<Bson> filters = new ArrayList<>(options.size());
        for (FilterOption option : options) {
            processExpression(filters, option);
        }

        if (options.size() == 1) {
            return filters.getFirst().toBsonDocument(BsonDocument.class, collection.getCodecRegistry());
        }

        return and(filters).toBsonDocument(BsonDocument.class, collection.getCodecRegistry());
    }

    private void invalidate(Bson filters) {
        if (resultCache != null) resultCache.clear(filters);
    }

    private void invalidate() {
        if (resultCache != null) resultCache.clear();
    }

    @Override
    public TransactionResult<Boolean> insert(T value, @NotNull TransactionContext<ClientSession> tx) {
        FieldModel<T> primaryKey = repositoryModel.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryModel.tableName());
        }

        try {
            if (entityLifecycleListener != null) {
                entityLifecycleListener.onPreInsert(value);
            }
            if (auditLogger != null)
                auditLogger.onInsert(value);

            MongoDatabaseParameters parameters = new MongoDatabaseParameters(collectionHandler);
            objectModel.insertEntity(parameters, value);
            Document doc = parameters.toDocument();

            String pk = mongoPrimaryKeyName(primaryKey);

            Object idValue = primaryKey.getValue(value);
            if (idValue != null) {
                doc.put(pk, idValue);
                if (!pk.equals(primaryKey.name())) {
                    doc.remove(primaryKey.name());
                }
            }

            InsertOneResult result = collection.insertOne(tx.connection(), doc);

            ID id = (ID) primaryKey.getValue(value);
            if (l2Cache != null) {
                l2Cache.invalidate(id);
            }
            if (readThroughCache != null) {
                readThroughCache.invalidate(id);
            }

            if (entityLifecycleListener != null) {
                entityLifecycleListener.onPostInsert(value);
            }
            return TransactionResult.success(result.wasAcknowledged());
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryModel, this);
        }
    }

    @Override
    public TransactionResult<Boolean> insert(T value) {
        FieldModel<T> primaryKey = repositoryModel.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryModel.tableName());
        }

        try {
            if (entityLifecycleListener != null) {
                entityLifecycleListener.onPreInsert(value);
            }
            if (auditLogger != null)
                auditLogger.onInsert(value);

            MongoDatabaseParameters parameters = new MongoDatabaseParameters(collectionHandler);
            objectModel.insertEntity(parameters, value);
            Document doc = parameters.toDocument();

            String pk = mongoPrimaryKeyName(primaryKey);

            // FIX: Ensure the ID is properly set in the document
            Object idValue = primaryKey.getValue(value);
            if (idValue != null) {
                // Always set using the MongoDB key name
                doc.put(pk, idValue);
                // Remove the old key if it's different
                if (!pk.equals(primaryKey.name())) {
                    doc.remove(primaryKey.name());
                }
            }

            InsertOneResult result = collection.insertOne(doc);

            ID id = (ID) primaryKey.getValue(value);
            if (l2Cache != null) {
                l2Cache.invalidate(id);
            }
            if (readThroughCache != null) {
                readThroughCache.invalidate(id);
            }

            if (entityLifecycleListener != null) {
                entityLifecycleListener.onPostInsert(value);
            }
            return TransactionResult.success(result.wasAcknowledged());
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryModel, this);
        }
    }

    @Override
    public TransactionResult<Boolean> insertAll(Collection<T> values, @NotNull TransactionContext<ClientSession> tx) {
        try {
            List<Document> docs = new ArrayList<>(values.size());
            for (T value : values) {
                MongoDatabaseParameters parameters = new MongoDatabaseParameters(collectionHandler);
                objectModel.insertEntity(parameters, value);
                Document doc = parameters.toDocument();

                FieldModel<T> primaryKey = repositoryModel.getPrimaryKey();
                if (primaryKey != null) {
                    String pk = mongoPrimaryKeyName(primaryKey);

                    Object idValue = primaryKey.getValue(value);
                    if (idValue != null) {
                        doc.put(pk, idValue);
                        if (!pk.equals(primaryKey.name())) {
                            doc.remove(primaryKey.name());
                        }
                    }
                }
                docs.add(doc);
            }
            InsertManyResult result = collection.insertMany(tx.connection(), docs);
            return TransactionResult.success(result.wasAcknowledged());
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryModel, this);
        }
    }

    @Override
    public TransactionResult<Boolean> insertAll(Collection<T> values) {
        try {
            List<Document> docs = new ArrayList<>(values.size());
            for (T value : values) {
                MongoDatabaseParameters parameters = new MongoDatabaseParameters(collectionHandler);
                objectModel.insertEntity(parameters, value);
                Document doc = parameters.toDocument();

                FieldModel<T> primaryKey = repositoryModel.getPrimaryKey();
                if (primaryKey != null) {
                    String pk = mongoPrimaryKeyName(primaryKey);

                    Object idValue = primaryKey.getValue(value);
                    if (idValue != null) {
                        doc.put(pk, idValue);
                        if (!pk.equals(primaryKey.name())) {
                            doc.remove(primaryKey.name());
                        }
                    }
                }
                docs.add(doc);
            }
            InsertManyResult result = collection.insertMany(docs);
            return TransactionResult.success(result.wasAcknowledged());
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryModel, this);
        }
    }

    @Override
    public TransactionResult<Boolean> updateAll(@NotNull T entity, TransactionContext<ClientSession> tx) {
        FieldModel<T>  primaryKey = repositoryModel.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryModel.tableName());
        }

        try {
            Objects.requireNonNull(entity);
            Objects.requireNonNull(tx);

            if (entityLifecycleListener != null) entityLifecycleListener.onPreUpdate(entity);

            MongoDatabaseParameters parameters = new MongoDatabaseParameters(collectionHandler);
            objectModel.insertEntity(parameters, entity);
            Document doc = parameters.toDocument();

            String pk = mongoPrimaryKeyName(primaryKey);
            if (!pk.equals(primaryKey.name()) && doc.containsKey(primaryKey.name()) && !doc.containsKey(pk)) {
                doc.put(pk, doc.remove(primaryKey.name()));
            }

            ID id = doc.get(pk, idType);

            Document document = new Document(pk, id);
            Document replaced = collection.findOneAndReplace(document, doc);

            if (id != null) {
                if (globalCache != null) globalCache.put(id, entity);
                if (l2Cache != null) l2Cache.invalidate(id);
                if (readThroughCache != null) readThroughCache.invalidate(id);
                if (auditLogger != null) {
                    MongoDatabaseResult databaseResult = new MongoDatabaseResult(replaced, collectionHandler, repositoryModel);
                    T construct = objectModel.construct(GeneratedValueReaders.get(repositoryModel.tableName(), databaseResult, typeResolverRegistry, id));
                    objectModel.populateRelationships(construct, id, relationshipLoader);
                    auditLogger.onUpdate(entity, construct);
                }
                invalidate(document);
            }

            if (entityLifecycleListener != null) {
                entityLifecycleListener.onPostUpdate(entity);
            }
            return TransactionResult.success(replaced != null);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryModel, this);
        }
    }

    @Override
    public TransactionResult<Boolean> updateAll(@NotNull T entity) {
        FieldModel<T>  primaryKey = repositoryModel.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryModel.tableName());
        }

        T oldEntity = null;

        try {
            if (auditLogger != null) {
                oldEntity = findById(this.objectModel.getId(entity));
            }
            if (entityLifecycleListener != null) {
                if (oldEntity == null) oldEntity = findById(objectModel.getId(entity));
                entityLifecycleListener.onPreUpdate(entity);
            }

            MongoDatabaseParameters parameters = new MongoDatabaseParameters(collectionHandler);
            objectModel.insertEntity(parameters, entity);
            Document doc = parameters.toDocument();

            String pk = mongoPrimaryKeyName(primaryKey);
            if (!pk.equals(primaryKey.name()) && doc.containsKey(primaryKey.name()) && !doc.containsKey(pk)) {
                doc.put(pk, doc.remove(primaryKey.name()));
            }

            ID id = doc.get(pk, idType);

            Document document = new Document(pk, id);
            Document replaced = collection.findOneAndUpdate(document, doc);

            if (id != null) {
                if (globalCache != null) globalCache.put(id, entity);
                if (auditLogger != null) {
                    MongoDatabaseResult result = new MongoDatabaseResult(replaced, collectionHandler, repositoryModel);
                    auditLogger.onUpdate(entity, oldEntity);
                }
            }

            invalidate(document);

            if (entityLifecycleListener != null) entityLifecycleListener.onPostUpdate(entity);
            return TransactionResult.success(replaced != null);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryModel, this);
        }
    }

    @Override
    public TransactionResult<Boolean> delete(T entity) {
        FieldModel<T>  primaryKey = repositoryModel.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryModel.tableName());
        }

        try {
            ID id = objectModel.getId(entity);

            if (entityLifecycleListener != null) {
                entityLifecycleListener.onPreDelete(entity);
            }
            if (globalCache != null) {
                globalCache.remove(id);
            }
            if (l2Cache != null) {
                l2Cache.invalidate(id);
            }
            if (readThroughCache != null) {
                readThroughCache.invalidate(id);
            }

            String pk = mongoPrimaryKeyName(primaryKey);
            Document filter = new Document(pk, id);
            DeleteResult result = collection.deleteOne(filter);
            invalidate(filter);

            if (auditLogger != null) {
                auditLogger.onDelete(entity);
            }
            if (entityLifecycleListener != null) {
                entityLifecycleListener.onPostDelete(entity);
            }
            return TransactionResult.success(result.getDeletedCount() > 0);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryModel, this);
        }
    }

    @Override
    public TransactionResult<Boolean> deleteById(ID id, TransactionContext<ClientSession> transactionContext) {
        FieldModel<T>  primaryKey = repositoryModel.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryModel.tableName());
        }

        try {
            String pk = mongoPrimaryKeyName(primaryKey);
            Document filter = new Document(pk, id);
            DeleteResult result = collection.deleteOne(transactionContext.connection(), filter);

            if (globalCache != null) {
                globalCache.remove(id);
            }

            invalidate(filter);
            return TransactionResult.success(result.getDeletedCount() > 0);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryModel, this);
        }
    }

    @Override
    public TransactionResult<Boolean> deleteById(ID value) {
        FieldModel<T>  primaryKey = repositoryModel.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryModel.tableName());
        }

        try {
            String pk = mongoPrimaryKeyName(primaryKey);
            Document filter = new Document(pk, value);
            DeleteResult result = collection.deleteOne(filter);

            if (globalCache != null) globalCache.remove(value);

            invalidate(filter);

            // False; it can actually be null.
            //noinspection ConstantValue
            return TransactionResult.success(result != null && result.getDeletedCount() > 0);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryModel, this);
        }
    }

    @Override
    public TransactionResult<Boolean> updateAll(@NotNull UpdateQuery query, TransactionContext<ClientSession> tx) {
        try {
            MongoUpdateResult mongoUpdateResult = getMongoUpdateResult(query);
            List<Bson> conditions = mongoUpdateResult.conditions(), updates = mongoUpdateResult.updates();
            UpdateResult result = collection.updateMany(
                    tx.connection(),
                    conditions.isEmpty() ? new Document() : and(conditions),
                    Updates.combine(updates)
            );
            if (query.filters() != null) invalidate(createFilterBson(query.filters()));
            return TransactionResult.success(result.getModifiedCount() > 0);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryModel, this);
        }
    }

    @Override
    public TransactionResult<Boolean> updateAll(@NotNull UpdateQuery query) {
        // Validate query
        ValidationEstimation validation = queryValidator.validateUpdateQuery(query);
        if (validation.isFail()) {
            Logging.warn("Update query validation failed: " + validation.reason());
        }
        
        try {
            MongoUpdateResult mongoUpdateResult = getMongoUpdateResult(query);
            UpdateResult result = collection.updateMany(
                    mongoUpdateResult.conditions().isEmpty()
                            ? new Document()
                            : and(mongoUpdateResult.conditions()),
                    Updates.combine(mongoUpdateResult.updates()));
            invalidate(createFilterBson(query.filters()));
            return TransactionResult.success(result.getModifiedCount() > 0);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryModel, this);
        }
    }

    @NotNull
    @Contract("_ -> new")
    private MongoUpdateResult getMongoUpdateResult(@NotNull UpdateQuery query) {
        List<Bson> conditions = new ArrayList<>(3), updates = new ArrayList<>(3);
        for (FilterOption f : query.filters()) {
            processExpression(conditions, f);
        }
        for (var e : query.updates().entrySet())
            updates.add(Updates.set(e.getKey(), e.getValue()));
        return new MongoUpdateResult(conditions, updates);
    }

    private record MongoUpdateResult(List<Bson> conditions, List<Bson> updates) {
    }

    @Override
    public TransactionResult<Boolean> delete(DeleteQuery query, TransactionContext<ClientSession> tx) {
        try {
            if (query == null || query.filters().isEmpty()) {
                DeleteResult result = collection.deleteMany(tx.connection(), new Document());
                invalidate();
                return TransactionResult.success(result.getDeletedCount() > 0);
            }
            Bson filterDoc = createFilterBson(query.filters());
            DeleteResult result = collection.deleteMany(tx.connection(), filterDoc);
            invalidate(createFilterBson(query.filters()));
            return TransactionResult.success(result.getDeletedCount() > 0);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryModel, this);
        }
    }

    @Override
    public TransactionResult<Boolean> delete(@NotNull DeleteQuery query) {
        // Validate query
        ValidationEstimation validation = queryValidator.validateDeleteQuery(query);
        if (validation.isFail()) {
            Logging.warn("Delete query validation failed: " + validation.reason());
        }
        
        try {
            if (query.filters().isEmpty()) {
                DeleteResult result = collection.deleteMany(new Document());
                invalidate();
                return TransactionResult.success(result.getDeletedCount() > 0);
            }

            Bson filterDoc = createFilterBson(query.filters());
            DeleteResult result = collection.deleteMany(filterDoc);
            invalidate(createFilterBson(query.filters()));
            return TransactionResult.success(result.getDeletedCount() > 0);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryModel, this);
        }
    }

    @Override
    public TransactionResult<Boolean> delete(T entity, TransactionContext<ClientSession> tx) {
        FieldModel<T>  primaryKey = repositoryModel.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryModel.tableName());
        }

        try {
            ID id = (ID) primaryKey.getValue(entity);

            if (entityLifecycleListener != null) {
                entityLifecycleListener.onPreDelete(entity);
            }

            String pk = mongoPrimaryKeyName(primaryKey);
            Document filter = new Document(pk, id);
            DeleteResult result = collection.deleteOne(tx.connection(), filter);

            invalidate(filter);
            if (globalCache != null) {
                globalCache.remove(id);
            }

            if (auditLogger != null) {
                auditLogger.onDelete(entity);
            }
            if (entityLifecycleListener != null) {
                entityLifecycleListener.onPostDelete(entity);
            }
            return TransactionResult.success(result.getDeletedCount() > 0);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryModel, this);
        }
    }

    @Override
    public TransactionResult<Boolean> createIndex(@NotNull IndexOptions index) {
        if (index.fields().isEmpty()) {
            throw new IllegalArgumentException("Cannot create an index without fields.");
        }
        Document indexDoc = new Document();
        for (FieldModel<?> field : index.fields()) indexDoc.put(field.name(), 1);

        collection.createIndex(indexDoc, new com.mongodb.client.model.IndexOptions().name(index.indexName()).unique(index.type() == IndexType.UNIQUE));

        return TransactionResult.success(true); // impossible to fail
    }

    @Override
    public TransactionResult<Boolean> createRepository(boolean ifNotExists) {
        return TransactionResult.success(true);
    }

    @Override
    public @NotNull TransactionContext<ClientSession> beginTransaction() {
        return new SimpleTransactionContext(client.startSession());
    }

    @Override
    public @NotNull List<ID> findIds(@NotNull SelectQuery query) {
        FieldModel<T>  primaryKey = repositoryModel.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryModel.tableName());
        }

        // Validate query (same philosophy as find)
        ValidationEstimation validation = queryValidator.validateSelectQuery(query);
        if (validation.isFail()) {
            Logging.warn("findIds query validation failed: " + validation.reason());
            return List.of();
        }

        Bson filterDoc = createFilterBson(query.filters());

        String pk = mongoPrimaryKeyName(primaryKey);
        Bson projection = Projections.include(pk);

        FindIterable<Document> iterable =
            collection.find(filterDoc)
                .projection(projection);

        // Apply query modifiers
        iterable = process(query, iterable, repositoryModel.getFetchPageSize());

        List<ID> ids = new ArrayList<>(8);

        try (MongoCursor<Document> cursor = iterable.iterator()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                ID id = doc.get(pk, idType);
                if (id != null) {
                    ids.add(id);
                }
            }
        }

        return ids;
    }

    @Override
    public DatabaseSession<ID, T, ClientSession> createSession() {
        openSessions++;
        return new DefaultSession<>(this, sessionCacheSupplier.apply(openSessions), openSessions, EnumSet.noneOf(SessionOption.class));
    }

    @Override
    public DatabaseSession<ID, T, ClientSession> createSession(EnumSet<SessionOption> options) {
        openSessions++;
        return new DefaultSession<>(this, sessionCacheSupplier.apply(openSessions), openSessions, options);
    }

    @Override
    public TransactionResult<Boolean> clear() {
        try {
            DeleteResult result = collection.deleteMany(new Document());
            if (resultCache != null) {
                resultCache.clear();
            }
            if (globalCache != null) {
                globalCache.clear();
            }
            return TransactionResult.success(result.getDeletedCount() > 0);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryModel, this);
        }
    }

    @Override
    public @NotNull Class<ID> getIdType() {
        return idType;
    }

    @Override
    public void close() {
        client.close();
        RepositoryRegistry.unregister(repositoryModel.tableName());
        collection = null;
    }

    @Override
    public @NotNull OperationContext<T, ID, ClientSession> getOperationContext() {
        return operationContext;
    }

    @Override
    public @NotNull OperationExecutor<T, ID, ClientSession> getOperationExecutor() {
        return operationExecutor;
    }

    @Override
    public @NotNull RepositoryModel<T, ID> getRepositoryModel() {
        return repositoryModel;
    }

    @Override
    public @NotNull TypeResolverRegistry getTypeResolverRegistry() {
        return typeResolverRegistry;
    }

    @Override
    public Class<T> getElementType() {
        return elementType;
    }

    public MongoCollection<Document> getCollection() {
        return collection;
    }

    public MongoClient getClient() {
        return client;
    }
}

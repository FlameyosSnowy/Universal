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
import io.github.flameyossnowy.universal.api.annotations.enums.CacheAlgorithmType;
import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;
import io.github.flameyossnowy.universal.api.cache.*;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.exceptions.handler.DefaultExceptionHandler;
import io.github.flameyossnowy.universal.api.exceptions.handler.ExceptionHandler;
import io.github.flameyossnowy.universal.api.listener.AuditLogger;
import io.github.flameyossnowy.universal.api.listener.EntityLifecycleListener;
import io.github.flameyossnowy.universal.api.operation.OperationContext;
import io.github.flameyossnowy.universal.api.operation.OperationExecutor;
import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.options.validator.QueryValidator;
import io.github.flameyossnowy.universal.api.options.validator.ValidationEstimation;
import io.github.flameyossnowy.universal.api.reflect.*;
import io.github.flameyossnowy.universal.api.resolver.ResolveWith;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.mongodb.annotations.MongoResolver;
import io.github.flameyossnowy.universal.mongodb.codec.MongoTypeCodecProvider;
import io.github.flameyossnowy.universal.mongodb.query.MongoQueryValidator;
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

import static com.mongodb.client.model.Filters.*;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

@SuppressWarnings({ "unused", "unchecked" })
public class MongoRepositoryAdapter<T, ID> implements RepositoryAdapter<T, ID, ClientSession> {
    private final MongoClient client;

    private static final Document EMPTY = new Document();

    MongoCollection<Document> collection;
    private final ObjectFactory<T, ID> objectFactory;
    private final RepositoryInformation repositoryInformation;
    private final OperationContext<ClientSession> operationContext;
    private final OperationExecutor<ClientSession> operationExecutor;
    private final Class<ID> idType;

    private final DefaultResultCache<Document, T, ID> resultCache;
    // Advanced caches
    private final SecondLevelCache<ID, T> l2Cache;
    private final ReadThroughCache<ID, T> readThroughCache;

    private final Logger logger = LoggerFactory.getLogger(MongoRepositoryAdapter.class);

    private static final Set<Class<?>> NUMBERS = Set.of(
            Integer.class, Long.class, Float.class, Double.class,
            Short.class, Byte.class, Character.class, int.class, long.class,
            float.class, double.class, short.class, byte.class, char.class
    );

    private final AuditLogger<T> auditLogger;
    private final EntityLifecycleListener<T> entityLifecycleListener;

    private final SessionCache<ID, T> globalCache;
    private long openSessions = 1;

    private final Class<T> elementType;

    private final LongFunction<SessionCache<ID, T>> sessionCacheSupplier;

    private final ExceptionHandler<T, ID, ClientSession> exceptionHandler;
    private final TypeResolverRegistry typeResolverRegistry = new TypeResolverRegistry();
    private final QueryValidator queryValidator;

    MongoRepositoryAdapter(
            @NotNull MongoClientSettings.Builder clientBuilder,
            String dbName,
            Class<T> repo,
            Class<ID> idType,
            SessionCache<ID, T> sessionCache,
            LongFunction<SessionCache<ID, T>> sessionCacheSupplier,
            CacheWarmer<T, ID> cacheWarmer,
            MongoClient client) {
        this.repositoryInformation = RepositoryMetadata.getMetadata(repo);
        if (repositoryInformation == null)
            throw new IllegalArgumentException("Unable to find repository information for " + repo.getSimpleName());

        FieldData<?> primaryKey = repositoryInformation.getPrimaryKey();
        if (primaryKey != null && (NUMBERS.contains(primaryKey.type()) || primaryKey.autoIncrement()))
            throw new IllegalArgumentException("Primary key must not be of type number and/or must not be auto-increment");

        this.idType = idType;
        this.elementType = repo;
        this.globalCache = sessionCache;
        this.sessionCacheSupplier = sessionCacheSupplier;

        this.exceptionHandler = this.createExceptionHandler();
        this.queryValidator = new MongoQueryValidator(repositoryInformation);

        this.operationExecutor = new MongoOperationExecutor<>(this);
        this.operationContext = new OperationContext<>(repositoryInformation, typeResolverRegistry, this.operationExecutor);

        RepositoryRegistry.register(this.repositoryInformation.getRepositoryName(), this);
        this.objectFactory = new ObjectFactory<>(this.repositoryInformation, repo, idType);
        this.resultCache = new DefaultResultCache<>(1000, CacheAlgorithmType.LEAST_FREQ_AND_RECENTLY_USED);
        // Initialize advanced caches
        this.l2Cache = new SecondLevelCache<>(10000, 300000, CacheAlgorithmType.LEAST_FREQ_AND_RECENTLY_USED);
        this.readThroughCache = new ReadThroughCache<>(10000, CacheAlgorithmType.LEAST_FREQ_AND_RECENTLY_USED, this::loadFromDatabase);

        List<Codec<?>> codecs = new ArrayList<>(2);
        List<IndexOptions> queued = initializeCodecs(clientBuilder, repositoryInformation, typeResolverRegistry);

        this.entityLifecycleListener = (EntityLifecycleListener<T>) repositoryInformation.getEntityLifecycleListener();
        this.auditLogger = (AuditLogger<T>) repositoryInformation.getAuditLogger();

        if (client == null) {
            CodecRegistry provider = getProvider(PojoCodecProvider.builder().automatic(true).build(), codecs, MongoClientSettings.getDefaultCodecRegistry());
            clientBuilder.codecRegistry(provider);
            this.client = MongoClients.create(clientBuilder.build());
        } else {
            this.client = client;
        }

        MongoDatabase database = this.client.getDatabase(dbName);
        this.collection = database.getCollection(repositoryInformation.getRepositoryName());

        if (client != null) queued.forEach(this::createIndex);

        if (cacheWarmer != null) {
            cacheWarmer.warmCache(this);
        }
    }

    private ExceptionHandler<T, ID, ClientSession> createExceptionHandler() {
        return repositoryInformation.getExceptionHandler() != null
            ? (ExceptionHandler<T, ID, ClientSession>) repositoryInformation.getExceptionHandler()
            : new DefaultExceptionHandler<>();
    }

    private static @NotNull List<IndexOptions> initializeCodecs(
        MongoClientSettings.Builder clientBuilder,
        @NotNull RepositoryInformation repositoryInformation,
        TypeResolverRegistry typeResolverRegistry
    ) {
        if (clientBuilder == null) return List.of();
        List<Codec<?>> codecs = new ArrayList<>(8);
        List<IndexOptions> queued = new ArrayList<>(8);

        for (FieldData<?> field : repositoryInformation.getFields()) {
            if (field.unique()) {
                queued.add(IndexOptions.builder(repositoryInformation.getType())
                        .type(IndexType.UNIQUE)
                        .field(field)
                        .build());
            }

            ResolveWith resolveWith = field.resolveWith();
            if (resolveWith != null) {
                try {
                    typeResolverRegistry.register(resolveWith.value().getDeclaredConstructor().newInstance());
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "Failed to instantiate TypeResolver for field: " + field.name(), e);
                }
            }

            MongoResolver mongoResolver = field.getAnnotation(MongoResolver.class);
            if (mongoResolver != null) {
                try {
                    codecs.add(mongoResolver.value().getDeclaredConstructor().newInstance());
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "Failed to instantiate codec for field: " + field.name(), e);
                }
            }
        }

        // Create codec registry with our custom providers
        CodecRegistry codecRegistry = CodecRegistries.fromRegistries(
                fromProviders(
                        MongoTypeCodecProvider.create(typeResolverRegistry, repositoryInformation),
                        PojoCodecProvider.builder().automatic(true).build()
                ),
                MongoClientSettings.getDefaultCodecRegistry()
        );

        if (!codecs.isEmpty()) {
            codecRegistry = CodecRegistries.fromRegistries(
                    codecRegistry,
                    CodecRegistries.fromCodecs(codecs)
            );
        }

        clientBuilder.codecRegistry(codecRegistry);
        return queued;
    }

    private static @NotNull CodecRegistry getProvider(CodecProvider pojo, @NotNull List<Codec<?>> codecs, CodecRegistry registry) {
        return codecs.isEmpty() ? fromProviders(registry, pojo) : fromProviders(registry, pojo, CodecRegistries.fromCodecs(codecs));
    }

    public static <T, ID> @NotNull MongoRepositoryAdapterBuilder<T, ID> builder(Class<T> repo, Class<ID> id) {
        return new MongoRepositoryAdapterBuilder<>(repo, id);
    }

    @Override
    public List<T> find(@NotNull SelectQuery query) {
        FieldData<?> primaryKey = repositoryInformation.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryInformation.getRepositoryName());
        }

        // Validate query
        ValidationEstimation validation = queryValidator.validateSelectQuery(query);
        if (validation.isFail()) {
            Logging.warn("Query validation failed: " + validation.reason());
        }
        
        Document filterDoc = createFilterDocument(query.filters());
        List<T> cached = resultCache.fetch(filterDoc);
        if (cached != null) return cached;

        FindIterable<Document> iterable = process(query, collection.find(filterDoc), repositoryInformation.getFetchPageSize());
        if (query.limit() == 1) {
            T result = objectFactory.fromDocument(iterable.first());
            List<T> single = List.of(result);
            resultCache.insert(filterDoc, single, primaryKey::getValue);
            return single;
        }

        try (MongoCursor<Document> cursor = iterable.iterator()) {
            List<T> results = new ArrayList<>(cursor.available());
            while (cursor.hasNext()) results.add(objectFactory.fromDocument(cursor.next()));
            resultCache.insert(filterDoc, results, primaryKey::getValue);
            return results;
        }
    }

    @Override
    public List<T> find() {
        FieldData<?> primaryKey = repositoryInformation.getPrimaryKey();
        if (resultCache != null) {
            if (primaryKey == null) {
                throw new IllegalArgumentException("Primary key not found for " + repositoryInformation.getRepositoryName());
            }

            List<T> cached = resultCache.fetch(EMPTY);
            if (cached != null) return cached;
        }

        try (MongoCursor<Document> iterable = collection.find().iterator()) {
            List<T> results = new ArrayList<>(iterable.available());
            while (iterable.hasNext()) {
                Document doc = iterable.next();
                results.add(objectFactory.fromDocument(doc));
            }

            if (resultCache != null) resultCache.insert(EMPTY, results, primaryKey::getValue);
            return results;
        }
    }

    @Override
    public @Nullable T findById(ID key) {
        // L2 cache first
        T cached = l2Cache.get(key);
        if (cached != null) {
            Logging.deepInfo("L2 cache hit for " + repositoryInformation.getRepositoryName() + " id=" + key);
            return cached;
        }

        // Read-through cache next
        T value = readThroughCache.get(key);
        if (value != null) {
            l2Cache.put(key, value);
            return value;
        }

        return null;
    }

    @Override
    public Map<ID, T> findAllById(Collection<ID> keys) {
        FieldData<?> primaryKey = repositoryInformation.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryInformation.getRepositoryName());
        }

        if (keys.isEmpty()) return Map.of();

        Map<ID, T> result = new HashMap<>(keys.size());
        List<ID> missing = new ArrayList<>(8);

        // Identity map first
        for (ID id : keys) {
            T cached = globalCache.get(id);
            if (cached != null) {
                result.put(id, cached);
            } else {
                missing.add(id);
            }
        }

        if (missing.isEmpty()) return result;

        String pk = repositoryInformation.getPrimaryKey().name();
        FindIterable<Document> iterable =
            collection.find(in(pk, missing));

        try (MongoCursor<Document> cursor = iterable.iterator()) {
            while (cursor.hasNext()) {
                T entity = objectFactory.fromDocument(cursor.next());
                ID id = primaryKey.getValue(entity);
                result.put(id, entity);
                globalCache.put(id, entity);
            }
        }

        return result;
    }

    private T loadFromDatabase(ID key) {
        FieldData<?> primaryKey = repositoryInformation.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryInformation.getRepositoryName());
        }

        Document filter = new Document(primaryKey.name(), key);
        T result = objectFactory.fromDocument(collection.find(filter).first());
        if (result != null) {
            resultCache.insert(filter, List.of(result), primaryKey::getValue);
        }
        return result;
    }

    @Override
    public T first(SelectQuery query) {
        return this.objectFactory.fromDocument(search(query).first());
    }

    private FindIterable<Document> search(SelectQuery query) {
        return process(query, collection.find(createFilterDocument(query.filters())), repositoryInformation.getFetchPageSize());
    }

    private static <T> FindIterable<T> process(@NotNull SelectQuery query, FindIterable<T> iterable, int pageSize) {
        if (pageSize > 0) iterable = iterable.batchSize(pageSize);
        if (query.limit() != -1) iterable = iterable.limit(query.limit());
        if (!query.sortOptions().isEmpty()) {
            List<Bson> sorts = query.sortOptions().stream()
                    .map(o -> o.order() == SortOrder.ASCENDING ? Sorts.ascending(o.field()) : Sorts.descending(o.field()))
                    .toList();
            iterable = iterable.sort(Sorts.orderBy(sorts));
        }
        return iterable;
    }

    private static void processExpression(@NotNull List<Bson> filters, @NotNull SelectOption option, Object value) {
        filters.add(switch (option.operator()) {
            case "=" -> eq(option.option(), value);
            case ">" -> gt(option.option(), value);
            case "<" -> lt(option.option(), value);
            case ">=" -> gte(option.option(), value);
            case "<=" -> lte(option.option(), value);
            default -> throw new IllegalArgumentException("Unsupported filter operation: " + option.operator());
        });
    }

    private static Document createFilterDocument(List<SelectOption> options) {
        List<Bson> filters = new ArrayList<>(options.size());
        for (SelectOption option : options) processExpression(filters, option, option.value());
        return Document.parse((filters.isEmpty() ? new BsonDocument() : and(filters)).toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry()).toJson());
    }

    private void invalidate(Document filters) {
        if (resultCache != null) resultCache.clear(filters);
    }

    private void invalidate() {
        if (resultCache != null) resultCache.clear();
    }

    @Override
    public TransactionResult<Boolean> insert(T value, @NotNull TransactionContext<ClientSession> tx) {
        FieldData<?> primaryKey = repositoryInformation.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryInformation.getRepositoryName());
        }

        try {
            entityLifecycleListener.onPreInsert(value);
            if (repositoryInformation.getAuditLogger() != null)
                ((AuditLogger<T>) repositoryInformation.getAuditLogger()).onInsert(value);
            InsertOneResult result = collection.insertOne(tx.connection(), objectFactory.toDocument(value));
            try {
                ID id = repositoryInformation.getPrimaryKey().getValue(value);
                resultCache.invalidate(id);
                l2Cache.invalidate(id);
                readThroughCache.invalidate(id);
            } catch (Exception ignored) {}
            entityLifecycleListener.onPostInsert(value);
            return TransactionResult.success(true);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
        }
    }

    @Override
    public TransactionResult<Boolean> insert(T value) {
        FieldData<?> primaryKey = repositoryInformation.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryInformation.getRepositoryName());
        }

        try {
            if (repositoryInformation.getAuditLogger() != null)
                ((AuditLogger<T>) repositoryInformation.getAuditLogger()).onInsert(value);
            InsertOneResult result = collection.insertOne(objectFactory.toDocument(value));
            invalidate();
            try {
                ID id = primaryKey.getValue(value);
                l2Cache.invalidate(id);
                readThroughCache.invalidate(id);
            } catch (Exception ignored) {}

            //noinspection ConstantValue
            return TransactionResult.success(result != null && result.wasAcknowledged());
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
        }
    }

    @Override
    public TransactionResult<Boolean> insertAll(Collection<T> values, @NotNull TransactionContext<ClientSession> tx) {
        try {
            if (repositoryInformation.getAuditLogger() != null)
                ((AuditLogger<T>) repositoryInformation.getAuditLogger()).onInsert(values);
            List<Document> docs = values.stream().map(objectFactory::toDocument).toList();
            InsertManyResult result = collection.insertMany(tx.connection(), docs);
            invalidate();

            return TransactionResult.success(result.wasAcknowledged());
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
        }
    }

    @Override
    public TransactionResult<Boolean> insertAll(Collection<T> values) {
        try {
            List<Document> docs = values.stream().map(objectFactory::toDocument).toList();
            InsertManyResult result = collection.insertMany(docs);
            invalidate();

            return TransactionResult.success(result.wasAcknowledged());
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
        }
    }

    @Override
    public TransactionResult<Boolean> updateAll(@NotNull T entity, TransactionContext<ClientSession> tx) {
        FieldData<?> primaryKey = repositoryInformation.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryInformation.getRepositoryName());
        }

        try {
            Objects.requireNonNull(entity);
            Objects.requireNonNull(tx);

            entityLifecycleListener.onPreUpdate(entity);
            Document doc = objectFactory.toDocument(entity);
            ID id = doc.get(primaryKey.name(), idType);

            Document replaced = collection.findOneAndUpdate(new Document(primaryKey.name(), id), doc);

            if (id != null) {
                globalCache.put(id, entity);
                l2Cache.invalidate(id);
                readThroughCache.invalidate(id);
                auditLogger.onUpdate(entity, objectFactory.fromDocument(replaced));
            }

            entityLifecycleListener.onPostUpdate(entity);
            return TransactionResult.success(replaced != null);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
        }
    }

    @Override
    public TransactionResult<Boolean> updateAll(@NotNull T entity) {
        FieldData<?> primaryKey = repositoryInformation.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryInformation.getRepositoryName());
        }

        try {
            entityLifecycleListener.onPreUpdate(entity);
            Document doc = objectFactory.toDocument(entity);
            ID id = doc.get(primaryKey.name(), idType);

            Document replaced = collection.findOneAndUpdate(new Document(primaryKey.name(), id), doc);

            if (id != null) {
                globalCache.put(id, entity);
                auditLogger.onUpdate(entity, objectFactory.fromDocument(replaced));
            }

            entityLifecycleListener.onPostUpdate(entity);
            return TransactionResult.success(replaced != null);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
        }
    }

    @Override
    public TransactionResult<Boolean> delete(T entity) {
        FieldData<?> primaryKey = repositoryInformation.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryInformation.getRepositoryName());
        }

        try {
            ID id = primaryKey.getValue(entity);

            entityLifecycleListener.onPreDelete(entity);
            globalCache.remove(id);
            l2Cache.invalidate(id);
            readThroughCache.invalidate(id);

            Document filter = new Document(primaryKey.name(), id);
            DeleteResult result = collection.deleteOne(filter);
            invalidate(filter);

            auditLogger.onDelete(entity);
            entityLifecycleListener.onPostDelete(entity);
            return TransactionResult.success(result.getDeletedCount() > 0);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
        }
    }

    @Override
    public TransactionResult<Boolean> deleteById(ID id, TransactionContext<ClientSession> transactionContext) {
        FieldData<?> primaryKey = repositoryInformation.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryInformation.getRepositoryName());
        }

        try {
            Document filter = new Document(primaryKey.name(), id);
            DeleteResult result = collection.deleteOne(transactionContext.connection(), filter);

            globalCache.remove(id);

            invalidate(filter);
            return TransactionResult.success(result.getDeletedCount() > 0);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
        }
    }

    @Override
    public TransactionResult<Boolean> deleteById(ID value) {
        FieldData<?> primaryKey = repositoryInformation.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryInformation.getRepositoryName());
        }

        try {
            Document filter = new Document(primaryKey.name(), value);
            DeleteResult result = collection.deleteOne(filter);

            if (globalCache != null) globalCache.remove(value);

            invalidate(filter);

            // False; it can actually be null.
            //noinspection ConstantValue
            return TransactionResult.success(result != null && result.getDeletedCount() > 0);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
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
            invalidate(createFilterDocument(query.filters()));
            return TransactionResult.success(result.getModifiedCount() > 0);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
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
            invalidate(createFilterDocument(query.filters()));
            return TransactionResult.success(result.getModifiedCount() > 0);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
        }
    }

    private static MongoUpdateResult getMongoUpdateResult(@NotNull UpdateQuery query) {
        List<Bson> conditions = new ArrayList<>(3), updates = new ArrayList<>(3);
        for (var f : query.filters())
            conditions.add(eq(f.option(), f.value()));
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
            List<Bson> filters = new ArrayList<>(3);
            for (var f : query.filters()) filters.add(eq(f.option(), f.value()));
            DeleteResult result = collection.deleteMany(tx.connection(), and(filters));
            invalidate(createFilterDocument(query.filters()));
            return TransactionResult.success(result.getDeletedCount() > 0);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
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

            List<Bson> filters = new ArrayList<>(query.filters().size());
            for (var f : query.filters()) filters.add(eq(f.option(), f.value()));
            DeleteResult result = collection.deleteMany(and(filters));
            invalidate(createFilterDocument(query.filters()));
            return TransactionResult.success(result.getDeletedCount() > 0);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
        }
    }

    @Override
    public TransactionResult<Boolean> delete(T entity, TransactionContext<ClientSession> tx) {
        FieldData<?> primaryKey = repositoryInformation.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryInformation.getRepositoryName());
        }

        try {
            ID id = primaryKey.getValue(entity);

            entityLifecycleListener.onPreDelete(entity);

            Document filter = new Document(primaryKey.name(), id);
            DeleteResult result = collection.deleteOne(tx.connection(), filter);

            invalidate(filter);
            globalCache.remove(id);

            auditLogger.onDelete(entity);
            entityLifecycleListener.onPostDelete(entity);
            return TransactionResult.success(result.getDeletedCount() > 0);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
        }
    }

    @Override
    public TransactionResult<Boolean> createIndex(@NotNull IndexOptions index) {
        if (index.fields().isEmpty()) throw new IllegalArgumentException("Cannot create an index without fields.");
        Document indexDoc = new Document();
        for (FieldData<?> field : index.fields()) indexDoc.put(field.name(), 1);

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
        FieldData<?> primaryKey = repositoryInformation.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + repositoryInformation.getRepositoryName());
        }

        // Validate query (same philosophy as find)
        ValidationEstimation validation = queryValidator.validateSelectQuery(query);
        if (validation.isFail()) {
            Logging.warn("findIds query validation failed: " + validation.reason());
        }

        Document filterDoc = createFilterDocument(query.filters());

        String pk = primaryKey.name();
        Bson projection = Projections.include(pk);

        FindIterable<Document> iterable =
            collection.find(filterDoc)
                .projection(projection);

        // Apply query modifiers
        iterable = process(query, iterable, repositoryInformation.getFetchPageSize());

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
            resultCache.clear();
            globalCache.clear();
            return TransactionResult.success(result.getDeletedCount() > 0);
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
        }
    }

    @Override
    public @NotNull Class<ID> getIdType() {
        return idType;
    }

    @Override
    public void close() {
        client.close();
        collection = null;
    }

    @Override
    public @NotNull OperationContext<ClientSession> getOperationContext() {
        return operationContext;
    }

    @Override
    public @NotNull OperationExecutor<ClientSession> getOperationExecutor() {
        return operationExecutor;
    }

    @Override
    public @NotNull RepositoryInformation getRepositoryInformation() {
        return repositoryInformation;
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

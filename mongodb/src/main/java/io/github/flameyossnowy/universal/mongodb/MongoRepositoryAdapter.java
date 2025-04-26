package io.github.flameyossnowy.universal.mongodb;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import com.mongodb.client.result.DeleteResult;
import io.github.flameyossnowy.universal.api.*;
import io.github.flameyossnowy.universal.api.IndexOptions;
import io.github.flameyossnowy.universal.api.annotations.enums.CacheAlgorithmType;
import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;
import io.github.flameyossnowy.universal.api.cache.Session;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.listener.AuditLogger;
import io.github.flameyossnowy.universal.api.listener.EntityLifecycleListener;
import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.reflect.*;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.mongodb.annotations.MongoResolver;
import org.bson.*;
import org.bson.codecs.*;
import org.bson.codecs.configuration.*;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongFunction;

import static com.mongodb.client.model.Filters.*;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

@SuppressWarnings({ "unused", "unchecked" })
public class MongoRepositoryAdapter<T, ID> implements RepositoryAdapter<T, ID, ClientSession> {
    private final MongoClient client;

    private static final Document EMPTY = new Document();

    MongoCollection<Document> collection;
    private final ObjectFactory<T, ID> objectFactory;
    private final RepositoryInformation information;
    private final Class<ID> idType;

    private final MongoResultCache<T, ID> resultCache;

    private final Logger logger = LoggerFactory.getLogger(MongoRepositoryAdapter.class);

    private static final Set<Class<?>> NUMBERS = Set.of(
            Integer.class, Long.class, Float.class, Double.class,
            Short.class, Byte.class, Character.class, int.class, long.class,
            float.class, double.class, short.class, byte.class, char.class
    );

    static final Map<Class<?>, MongoRepositoryAdapter<?, ?>> ADAPTERS = new ConcurrentHashMap<>(10);

    private final AuditLogger<T> auditLogger;
    private final EntityLifecycleListener<T> entityLifecycleListener;

    private final SessionCache<ID, T> globalCache;
    private long openSessions = 1;

    private final LongFunction<SessionCache<ID, T>> sessionCacheSupplier;

    MongoRepositoryAdapter(@NotNull MongoClientSettings.Builder clientBuilder, String dbName, Class<T> repo, Class<ID> idType, SessionCache<ID, T> sessionCache, LongFunction<SessionCache<ID, T>> sessionCacheSupplier) {
        this.information = RepositoryMetadata.getMetadata(repo);
        this.idType = idType;
        this.globalCache = sessionCache;
        this.sessionCacheSupplier = sessionCacheSupplier;

        if (information == null) throw new IllegalArgumentException("Unable to find repository information for " + repo.getSimpleName());
        if (NUMBERS.contains(information.getPrimaryKey().type()) || information.getPrimaryKey().autoIncrement())
            throw new IllegalArgumentException("Primary key must not be of type number and/or must not be auto-increment");

        ADAPTERS.put(repo, this);
        this.objectFactory = new ObjectFactory<>(this.information, repo, idType);
        this.resultCache = new MongoResultCache<>(1000, CacheAlgorithmType.LEAST_FREQ_AND_RECENTLY_USED);

        List<Codec<?>> codecs = new ArrayList<>(2);
        List<IndexOptions> queued = new ArrayList<>(2);
        for (FieldData<?> field : information.getFields()) {
            if (field.unique()) queued.add(IndexOptions.builder(information.getType()).type(IndexType.UNIQUE).field(field).build());

            MongoResolver annotation = field.rawField().getAnnotation(MongoResolver.class);
            if (annotation != null) {
                try {
                    codecs.add((Codec<?>) annotation.value().getDeclaredConstructor().newInstance());
                } catch (Exception e) {
                    throw new IllegalArgumentException("Unable to instantiate codec, it must have an empty, public constructor.");
                }
            }
        }

        entityLifecycleListener = (EntityLifecycleListener<T>) information.getEntityLifecycleListener();
        auditLogger = (AuditLogger<T>) information.getAuditLogger();

        CodecRegistry provider = getProvider(PojoCodecProvider.builder().automatic(true).build(), codecs, MongoClientSettings.getDefaultCodecRegistry());
        clientBuilder.codecRegistry(provider);
        this.client = MongoClients.create(clientBuilder.build());

        MongoDatabase database = this.client.getDatabase(dbName);
        this.collection = database.getCollection(information.getRepositoryName());

        queued.forEach(this::createIndex);
    }

    private static @NotNull CodecRegistry getProvider(CodecProvider pojo, @NotNull List<Codec<?>> codecs, CodecRegistry registry) {
        return codecs.isEmpty() ? fromProviders(registry, pojo) : fromProviders(registry, pojo, CodecRegistries.fromCodecs(codecs));
    }

    public static <T, ID> @NotNull MongoRepositoryAdapterBuilder<T, ID> builder(Class<T> repo, Class<ID> id) {
        return new MongoRepositoryAdapterBuilder<>(repo, id);
    }

    @Override
    public List<T> find(@NotNull SelectQuery query) {
        Document filterDoc = createFilterDocument(query.filters());
        List<T> cached = resultCache.fetch(filterDoc);
        if (cached != null) return cached;

        FindIterable<Document> iterable = process(query, collection.find(filterDoc), information.getFetchPageSize());
        if (query.limit() == 1) {
            T result = objectFactory.fromDocument(iterable.first());
            List<T> single = List.of(result);
            resultCache.insert(filterDoc, single);
            return single;
        }

        try (MongoCursor<Document> cursor = iterable.iterator()) {
            List<T> results = new ArrayList<>(cursor.available());
            while (cursor.hasNext()) results.add(objectFactory.fromDocument(cursor.next()));
            resultCache.insert(filterDoc, results);
            return results;
        }
    }

    @Override
    public List<T> find() {
        List<T> cached = resultCache.fetch(EMPTY);
        if (cached != null) return cached;

        try (MongoCursor<Document> iterable = collection.find().iterator()) {
            List<T> results = new ArrayList<>(iterable.available());
            while (iterable.hasNext()) {
                Document doc = iterable.next();
                results.add(objectFactory.fromDocument(doc));
            }

            resultCache.insert(EMPTY, results);
            return results;
        }
    }

    @Override
    public T findById(ID key) {
        T cached = globalCache == null ? null : globalCache.get(key);
        if (cached != null) {
            Logging.deepInfo("Cache hit for " + information.getRepositoryName() + " with key " + key);
            return cached;
        }

        if (globalCache != null) Logging.deepInfo("Cache miss for " + information.getRepositoryName() + " with key " + key);

        Document filter = new Document(information.getPrimaryKey().name(), key);

        T result = objectFactory.fromDocument(collection.find(filter).first());
        resultCache.insert(filter, List.of(result));
        return result;
    }

    @Override
    public T first(SelectQuery query) {
        return this.objectFactory.fromDocument(search(query).first());
    }

    private FindIterable<Document> search(SelectQuery query) {
        return process(query, collection.find(createFilterDocument(query.filters())), information.getFetchPageSize());
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
    public boolean insert(T value, @NotNull TransactionContext<ClientSession> tx) {
        entityLifecycleListener.onPrePersist(value);
        if (information.getAuditLogger() != null)
            ((AuditLogger<T>) information.getAuditLogger()).onInsert(value);
        collection.insertOne(tx.connection(), objectFactory.toDocument(value));
        invalidate(); // Clear all because we don't know affected filters
        entityLifecycleListener.onPostPersist(value);
        return true;
    }

    @Override
    public boolean insert(T value) {
        if (information.getAuditLogger() != null)
            ((AuditLogger<T>) information.getAuditLogger()).onInsert(value);
        collection.insertOne(objectFactory.toDocument(value));
        invalidate();
        return true;
    }

    @Override
    public void insertAll(List<T> values, @NotNull TransactionContext<ClientSession> tx) {
        if (information.getAuditLogger() != null)
            ((AuditLogger<T>) information.getAuditLogger()).onInsert(values);
        List<Document> docs = values.stream().map(objectFactory::toDocument).toList();
        collection.insertMany(tx.connection(), docs);
        invalidate();
    }

    @Override
    public void insertAll(List<T> values) {
        List<Document> docs = values.stream().map(objectFactory::toDocument).toList();
        collection.insertMany(docs);
        invalidate();
    }

    @Override
    public boolean updateAll(@NotNull T entity, TransactionContext<ClientSession> tx) {
        Objects.requireNonNull(entity);
        Objects.requireNonNull(tx);

        entityLifecycleListener.onPreUpdate(entity);
        Document doc = objectFactory.toDocument(entity);
        ID id = doc.get(information.getPrimaryKey().name(), idType);

        Document replaced = collection.findOneAndUpdate(new Document(information.getPrimaryKey().name(), id), doc);

        if (id != null) {
            globalCache.put(id, entity);
            auditLogger.onUpdate(entity, objectFactory.fromDocument(replaced));
        }

        entityLifecycleListener.onPostUpdate(entity);
        return replaced != null;
    }

    @Override
    public boolean updateAll(@NotNull T entity) {
        entityLifecycleListener.onPreUpdate(entity);
        Document doc = objectFactory.toDocument(entity);
        ID id = doc.get(information.getPrimaryKey().name(), idType);

        Document replaced = collection.findOneAndUpdate(new Document(information.getPrimaryKey().name(), id), doc);

        if (id != null) {
            globalCache.put(id, entity);
            auditLogger.onUpdate(entity, objectFactory.fromDocument(replaced));
        }

        entityLifecycleListener.onPostUpdate(entity);
        return replaced != null;
    }

    @Override
    public boolean delete(T entity) {
        ID id = information.getPrimaryKey().getValue(entity);

        entityLifecycleListener.onPreDelete(entity);
        globalCache.remove(id);

        Document filter = new Document(information.getPrimaryKey().name(), id);
        DeleteResult result = collection.deleteOne(filter);
        invalidate(filter);

        auditLogger.onDelete(entity);
        entityLifecycleListener.onPostDelete(entity);
        return result.getDeletedCount() > 0;
    }

    @Override
    public boolean deleteById(ID id, TransactionContext<ClientSession> transactionContext) {
        Document filter = new Document(information.getPrimaryKey().name(), id);
        DeleteResult result = collection.deleteOne(transactionContext.connection(), filter);

        globalCache.remove(id);

        invalidate(filter);
        return result.getDeletedCount() > 0;
    }

    @Override
    public boolean deleteById(ID value) {
        return false;
    }

    @Override
    public boolean updateAll(@NotNull UpdateQuery query, TransactionContext<ClientSession> tx) {
        List<Bson> conditions = new ArrayList<>(), updates = new ArrayList<>();
        for (var f : query.conditions()) conditions.add(eq(f.option(), f.value()));
        for (var e : query.updates().entrySet()) updates.add(Updates.set(e.getKey(), e.getValue()));
        collection.updateMany(tx.connection(), conditions.isEmpty() ? new Document() : and(conditions), Updates.combine(updates));
        invalidate(createFilterDocument(query.conditions()));
        return true;
    }

    @Override
    public boolean updateAll(@NotNull UpdateQuery query) {
        List<Bson> conditions = new ArrayList<>(), updates = new ArrayList<>();
        for (var f : query.conditions()) conditions.add(eq(f.option(), f.value()));
        for (var e : query.updates().entrySet()) updates.add(Updates.set(e.getKey(), e.getValue()));
        collection.updateMany(conditions.isEmpty() ? new Document() : and(conditions), Updates.combine(updates));
        invalidate(createFilterDocument(query.conditions()));
        return true;
    }

    @Override
    public boolean delete(DeleteQuery query, TransactionContext<ClientSession> tx) {
        if (query == null || query.filters().isEmpty()) {
            collection.deleteMany(tx.connection(), new Document());
            invalidate();
            return true;
        }
        List<Bson> filters = new ArrayList<>();
        for (var f : query.filters()) filters.add(eq(f.option(), f.value()));
        collection.deleteMany(tx.connection(), and(filters));
        invalidate(createFilterDocument(query.filters()));
        return true;
    }

    @Override
    public boolean delete(@NotNull DeleteQuery query) {
        if (query.filters().isEmpty()) {
            collection.deleteMany(new Document());
            invalidate();
            return true;
        }

        List<Bson> filters = new ArrayList<>(query.filters().size());
        for (var f : query.filters()) filters.add(eq(f.option(), f.value()));
        collection.deleteMany(and(filters));
        invalidate(createFilterDocument(query.filters()));
        return true;
    }

    @Override
    public boolean delete(T entity, TransactionContext<ClientSession> tx) {
        ID id = information.getPrimaryKey().getValue(entity);

        entityLifecycleListener.onPreDelete(entity);

        Document filter = new Document(information.getPrimaryKey().name(), id);
        DeleteResult result = collection.deleteOne(tx.connection(), filter);

        invalidate(filter);
        globalCache.remove(id);

        auditLogger.onDelete(entity);
        entityLifecycleListener.onPostDelete(entity);
        return result.getDeletedCount() > 0;
    }

    @Override
    public void createIndex(@NotNull IndexOptions index) {
        if (index.fields().isEmpty()) throw new IllegalArgumentException("Cannot create an index without fields.");
        Document indexDoc = new Document();
        for (FieldData<?> field : index.fields()) indexDoc.put(field.name(), 1);
        collection.createIndex(indexDoc, new com.mongodb.client.model.IndexOptions().name(index.indexName()).unique(index.type() == IndexType.UNIQUE));
    }

    @Override
    public void createRepository(boolean ifNotExists) {}

    @Override
    public TransactionContext<ClientSession> beginTransaction() {
        return new SimpleTransactionContext(client.startSession());
    }

    @Override
    public Session<ID, T, ClientSession> createSession() {
        openSessions++;
        return new MongoSession<>(this, sessionCacheSupplier.apply(openSessions), openSessions);
    }

    @Override
    public void clear() {
        collection.deleteMany(new Document());
        resultCache.clear();
        globalCache.clear();
    }

    @Override
    public <A> A createDynamicProxy(Class<A> adapter) {
        return (A) Proxy.newProxyInstance(
                adapter.getClassLoader(),
                new Class[]{ adapter },
                new MongoProxiedAdapterHandler<>(this)
        );
    }

    @Override
    public Class<ID> getIdType() {
        return idType;
    }

    @Override
    public void close() {
        client.close();
        collection = null;
    }

    @Override
    public RepositoryInformation getInformation() {
        return information;
    }
}

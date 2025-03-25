package io.github.flameyossnowy.universal.mongodb;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import io.github.flameyossnowy.universal.api.*;
import io.github.flameyossnowy.universal.api.IndexOptions;
import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;
import io.github.flameyossnowy.universal.api.cache.FetchedDataResult;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.reflect.*;
import io.github.flameyossnowy.universal.mongodb.annotations.MongoResolver;
import org.bson.*;
import org.bson.codecs.*;
import org.bson.codecs.configuration.*;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.*;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.mongodb.client.model.Filters.*;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

@SuppressWarnings({ "unused", "unchecked" })
public class MongoRepositoryAdapter<T, ID> implements RepositoryAdapter<T, ID, ClientSession> {
    private final MongoClient client;
    MongoCollection<Document> collection;
    private final ObjectFactory<T, ID> objectFactory;

    private final RepositoryInformation information;

    private final Class<ID> idType;

    private static final Set<Class<?>> NUMBERS = Set.of(
            Integer.class,
            Long.class,
            Float.class,
            Double.class,
            Short.class,
            Byte.class,
            int.class,
            long.class,
            float.class,
            double.class,
            short.class,
            byte.class
    );

    static final Map<Class<?>, MongoRepositoryAdapter<?, ?>> ADAPTERS = new ConcurrentHashMap<>();

    MongoRepositoryAdapter(@NotNull MongoClientSettings.Builder client, String dbName, Class<T> repo, Class<ID> idType) {
        this.information = RepositoryMetadata.getMetadata(repo);
        this.idType = idType;

        if (information == null) {
            throw new IllegalArgumentException("Unable to find repository information for " + repo.getSimpleName());
        }

        if (NUMBERS.contains(information.getPrimaryKey().type()) || information.getPrimaryKey().autoIncrement()) {
            throw new IllegalArgumentException("Primary key must not be of type number and/or must not be auto-increment");
        }

        ADAPTERS.put(repo, this);

        this.objectFactory = new ObjectFactory<>(this.information, repo, idType);

        List<Codec<?>> codecs = new ArrayList<>();
        List<IndexOptions> queued = new ArrayList<>();
        for (FieldData<?> field : information.getFields()) {
            if (field.unique()) queued.add(IndexOptions.builder(information.getType()).type(IndexType.UNIQUE).field(field).build());

            MongoResolver annotation = field.rawField().getAnnotation(MongoResolver.class);
            if (annotation == null) continue;
            try {
                codecs.add((Codec<?>) annotation.value().getDeclaredConstructor().newInstance());
            } catch (InstantiationException |
                     InvocationTargetException |
                     IllegalAccessException |
                     NoSuchMethodException e) {
                throw new IllegalArgumentException("Unable to instantiate codec, it must have an empty, public constructor.");
            }
        }

        CodecRegistry provider = getProvider(PojoCodecProvider.builder().automatic(true).build(), codecs, MongoClientSettings.getDefaultCodecRegistry());

        client.codecRegistry(provider);
        this.client = MongoClients.create(client.build());
        MongoDatabase database = this.client.getDatabase(dbName);
        this.collection = database.getCollection(information.getRepositoryName());

        if (queued.isEmpty()) return;
        queued.forEach(this::createIndex);
    }

    private static @NotNull CodecRegistry getProvider(CodecProvider pojo, @NotNull List<Codec<?>> codecs, CodecRegistry registry) {
        return codecs.isEmpty() ? fromProviders(registry, pojo) : fromProviders(registry, pojo, CodecRegistries.fromCodecs(codecs));
    }

    public static <T, ID> @NotNull MongoRepositoryAdapterBuilder<T, ID> builder(Class<T> repo, Class<ID> id) {
        return new MongoRepositoryAdapterBuilder<>(repo, id);
    }

    @Override
    public FetchedDataResult<T, ID> find(@NotNull SelectQuery query) {
        FindIterable<Document> iterable = search(query);
        if (iterable == null) return FetchedDataResult.empty();

        if (query.limit() == 1) {
            T result = (T) this.objectFactory.fromDocument(iterable.first());
            return FetchedDataResult.of(result);
        }

        List<T> results = new ArrayList<>();
        for (Document document : iterable) results.add((T) this.objectFactory.fromDocument(document));
        return FetchedDataResult.of(results);
    }

    private FindIterable<Document> search(@NotNull SelectQuery query) {
        List<Bson> filters = new ArrayList<>();
        for (SelectOption option : query.filters()) processExpression(filters, option, option.value());
        return process(query, collection.find(filters.isEmpty() ? new BsonDocument() : and(filters)), information.getFetchPageSize());
    }

    @Override
    public FetchedDataResult<T, ID> find() {
        List<T> results = new ArrayList<>();
        for (Document document : collection.find()) results.add((T) this.objectFactory.fromDocument(document));
        return FetchedDataResult.of(results);
    }

    @Override
    public T findById(ID key) {
        return (T) this.objectFactory.fromDocument(collection.find(eq(information.getPrimaryKey().name(), key)).first());
    }

    @Override
    public T first(SelectQuery query) {
        return (T) this.objectFactory.fromDocument(search(query).first());
    }

    private static <T> FindIterable<T> process(@NotNull SelectQuery query, FindIterable<T> iterable, int pageSize) {
        if (pageSize > 0) iterable = iterable.batchSize(pageSize);
        if (query.limit() != -1) iterable = iterable.limit(query.limit());
        if (!query.sortOptions().isEmpty()) {
            List<Bson> sorts = query.sortOptions().stream().map(o -> o.order() == SortOrder.ASCENDING ? Sorts.ascending(o.field()) : Sorts.descending(o.field())).toList();
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

    @Override
    public boolean insert(T value, @NotNull TransactionContext<ClientSession> tx) {
        collection.insertOne(tx.connection(), this.objectFactory.toDocument(value));
        return false;
    }

    @Override
    public void insertAll(List<T> value, @NotNull TransactionContext<ClientSession> tx) {
        List<Document> documents = new ArrayList<>();
        for (T t : value) documents.add(this.objectFactory.toDocument(t));
        collection.insertMany(tx.connection(), documents);
    }

    @Override
    public boolean updateAll(@NotNull UpdateQuery query, TransactionContext<ClientSession> tx) {
        List<Bson> conditionsList = new ArrayList<>(), updatesList = new ArrayList<>();
        for (var f : query.conditions()) conditionsList.add(eq(f.option(), f.value()));
        for (var e : query.updates().entrySet()) updatesList.add(Updates.set(e.getKey(), e.getValue()));
        collection.updateMany(tx.connection(), query.conditions().isEmpty() ? new Document() : and(conditionsList), Updates.combine(updatesList));
        return false;
    }

    @Override
    public boolean delete(DeleteQuery query, TransactionContext<ClientSession> tx) {
        if (query == null || query.filters().isEmpty()) return collection.deleteMany(new Document()).getDeletedCount() != 0;
        List<Bson> filtersList = new ArrayList<>();
        for (var f : query.filters()) filtersList.add(eq(f.option(), f.value()));
        return collection.deleteMany(tx.connection(), and(filtersList)).getDeletedCount() != 0;
    }

    @Override
    public boolean delete(T value) {
        return collection.deleteOne(eq(information.getPrimaryKey().name(), value)).getDeletedCount() != 0;
    }

    @Override
    public void createIndex(@NotNull IndexOptions index) {
        if (index.fields().isEmpty()) throw new IllegalArgumentException("Cannot create an index without fields.");
        collection.createIndex(new Document(index.fields().stream()
                .map(f -> Map.entry(f.name(), 1))
                .collect(Document::new, (document, entry) -> document.put(entry.getKey(), entry.getValue()), Document::putAll)),
                new com.mongodb.client.model.IndexOptions().name(index.indexName()).unique(index.type() == IndexType.UNIQUE));
    }

    @Override
    public void createRepository(boolean ifNotExists) {}

    @Override
    public TransactionContext<ClientSession> beginTransaction() {
        return new SimpleTransactionContext(client.startSession());
    }

    @Override
    public void clear() {
        collection.deleteMany(new Document());
    }

    @Override
    public <A> A createDynamicProxy(Class<A> adapter) {
        return null;
    }

    @Override
    public boolean insert(T value) {
        collection.insertOne(this.objectFactory.toDocument(value));
        return false;
    }

    @Override
    public boolean updateAll(@NotNull UpdateQuery query) {
        List<Bson> conditionsList = new ArrayList<>(), updatesList = new ArrayList<>();
        for (var f : query.conditions()) conditionsList.add(eq(f.option(), f.value()));
        for (var e : query.updates().entrySet()) updatesList.add(Updates.set(e.getKey(), e.getValue()));
        collection.updateMany(query.conditions().isEmpty() ? new Document() : and(conditionsList), Updates.combine(updatesList));
        return false;
    }

    @Override
    public boolean delete(DeleteQuery query) {
        if (query == null || query.filters().isEmpty()) {
            collection.deleteMany(new Document()).getDeletedCount();
            return false;
        }
        List<Bson> filtersList = new ArrayList<>();
        for (var f : query.filters()) filtersList.add(eq(f.option(), f.value()));
        collection.deleteMany(and(filtersList)).getDeletedCount();
        return false;
    }

    @Override
    public void insertAll(List<T> query) {
        List<Document> documents = new ArrayList<>();
        for (T t : query) documents.add(this.objectFactory.toDocument(t));
        collection.insertMany(documents);
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
}

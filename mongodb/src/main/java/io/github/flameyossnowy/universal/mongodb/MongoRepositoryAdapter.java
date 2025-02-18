package io.github.flameyossnowy.universal.mongodb;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import io.github.flameyossnowy.universal.api.*;
import io.github.flameyossnowy.universal.api.IndexOptions;
import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;
import io.github.flameyossnowy.universal.api.cache.ResultCache;
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
import java.util.*;
import static com.mongodb.client.model.Filters.*;

@SuppressWarnings({"unused", "unchecked"})
public class MongoRepositoryAdapter<T, ID> implements RepositoryAdapter<T, ID, ClientSession> {
    private final MongoDatabase database;
    private final MongoClient client;
    private MongoCollection<T> collection;
    private final ResultCache cache;
    private final Class<T> repository;

    MongoRepositoryAdapter(@NotNull MongoClientSettings.Builder client, String dbName, ResultCache cache, Class<T> repo) {
        RepositoryInformation info = RepositoryMetadata.getMetadata(repo);
        List<Codec<?>> codecs = new ArrayList<>();
        for (FieldData<?> field : info.fields()) {
            if (field.unique()) createIndex(IndexOptions.builder(info.type()).type(IndexType.UNIQUE).field(field).build());

            MongoResolver annotation = field.rawField().getAnnotation(MongoResolver.class);
            if (annotation != null) codecs.add((Codec<?>) ReflectiveMetaData.newInstance(annotation.value()));
        }

        CodecRegistry provider = getProvider(PojoCodecProvider.builder().automatic(true).build(), codecs);

        client.codecRegistry(provider);
        this.client = MongoClients.create(client.build());
        this.database = this.client.getDatabase(dbName);
        this.cache = cache;
        this.repository = repo;
    }

    private static @NotNull CodecRegistry getProvider(CodecProvider pojo, List<Codec<?>> codecs) {
        return codecs.isEmpty() ? CodecRegistries.fromProviders(MongoClientSettings.getDefaultCodecRegistry(), pojo)
                : CodecRegistries.fromProviders(MongoClientSettings.getDefaultCodecRegistry(), pojo, CodecRegistries.fromCodecs(codecs));
    }

    public static <T, ID> @NotNull MongoRepositoryAdapter<T, ID> open(MongoClientSettings.Builder client, String dbName, ResultCache cache, Class<T> repo) {
        return new MongoRepositoryAdapter<>(client, dbName, cache, repo);
    }

    public static <T, ID> @NotNull MongoRepositoryAdapterBuilder<T, ID> builder(Class<T> repo, Class<ID> id) {
        return new MongoRepositoryAdapterBuilder<>(repo);
    }

    @Override
    public List<T> find(@NotNull SelectQuery query) {
        List<Bson> filters = new ArrayList<>();
        for (SelectOption option : query.filters()) processExpression(filters, option, option.value());

        FindIterable<T> findIterable = process(query, collection.find(filters.isEmpty() ? new BsonDocument() : and(filters)));
        return findIterable.into(new ArrayList<>());
    }

    @Override
    public List<T> find() {
        return collection.find().into(new ArrayList<>());
    }

    @Override
    public T first(SelectQuery query) {
        List<Bson> filters = new ArrayList<>();
        query.filters().forEach(option -> processExpression(filters, option, option.value()));
        return process(query, collection.find(filters.isEmpty() ? new BsonDocument() : and(filters))).first();
    }

    private static <T> FindIterable<T> process(SelectQuery query, FindIterable<T> iterable) {
        if (query.limit() != -1) iterable = iterable.limit(query.limit());
        if (!query.sortOptions().isEmpty()) {
            List<Bson> sorts = query.sortOptions().stream().map(o -> o.order() == SortOrder.ASCENDING ? Sorts.ascending(o.field()) : Sorts.descending(o.field())).toList();
            iterable = iterable.sort(Sorts.orderBy(sorts));
        }
        return iterable;
    }

    private static void processExpression(List<Bson> filters, @NotNull SelectOption option, Object value) {
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
    public void insert(T value, TransactionContext<ClientSession> tx) { collection.insertOne(tx.connection(), value); }

    @Override
    public void insertAll(List<T> value, TransactionContext<ClientSession> tx) { collection.insertMany(tx.connection(), value); }

    @Override
    public void updateAll(UpdateQuery query, TransactionContext<ClientSession> tx) { collection.updateMany(tx.connection(), query.conditions().isEmpty() ? new Document() : and(query.conditions().stream().map(f -> eq(f.option(), f.value())).toList()), Updates.combine(query.updates().entrySet().stream().map(e -> Updates.set(e.getKey(), e.getValue())).toList())); }

    @Override
    public void delete(DeleteQuery query, TransactionContext<ClientSession> tx) { collection.deleteMany(tx.connection(), query == null || query.filters().isEmpty() ? new Document() : and(query.filters().stream().map(f -> eq(f.option(), f.value())).toList())); }

    @Override
    public void createIndex(IndexOptions index) {
        if (index.fields().isEmpty()) throw new IllegalArgumentException("Cannot create an index without fields.");
        collection.createIndex(new Document(index.fields().stream()
                .map(f -> Map.entry(f.name(), 1))
                .collect(Document::new, (doc) -> doc::append, Document::putAll)),
                new com.mongodb.client.model.IndexOptions().name(index.indexName()).unique(index.type() == IndexType.UNIQUE));
    }

    @Override
    public void createRepository() { this.collection = database.getCollection(RepositoryMetadata.getMetadata(repository).repository(), repository); }

    @Override
    public void createRepository(boolean ifNotExists) { createRepository(); }

    @Override
    public TransactionContext<ClientSession> beginTransaction() { return new SimpleTransactionContext(client.startSession()); }

    @Override
    public void clear() { collection.deleteMany(new Document()); }

    public boolean isValueExists(String field, Object value) { return collection.find(eq(field, value)).first() != null; }

    @Override
    public void close() { client.close(); collection = null; }
}

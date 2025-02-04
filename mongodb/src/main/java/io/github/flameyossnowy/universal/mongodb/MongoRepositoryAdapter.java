package io.github.flameyossnowy.universal.mongodb;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import com.mongodb.client.model.Updates;
import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.options.DeleteQuery;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.options.UpdateQuery;
import io.github.flameyossnowy.universal.api.repository.RepositoryMetadata;
import io.github.flameyossnowy.universal.mongodb.resolvers.ValueTypeResolverRegistry;
import io.github.flameyossnowy.universal.mongodb.parsers.MongoDatabaseParser;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

public class MongoRepositoryAdapter<T> implements RepositoryAdapter<T, ClientSession> {
    private final MongoDatabase database;
    private final MongoClient client;
    private MongoCollection<Document> collection;
    private final Class<T> repository;
    private final MongoDatabaseParser parser;

    MongoRepositoryAdapter(@NotNull MongoClientSettings client, String databaseName, final ValueTypeResolverRegistry resolverRegistry, final Class<T> repository) {
        this.client = MongoClients.create(client);
        this.database = this.client.getDatabase(databaseName);
        this.repository = repository;
        this.parser = new MongoDatabaseParser(resolverRegistry, repository);
    }

    @Contract("_, _, _, _ -> new")
    public static <T> @NotNull MongoRepositoryAdapter<T> open(MongoClientSettings client,
                                                              String databaseName,
                                                              final ValueTypeResolverRegistry resolverRegistry,
                                                              final Class<T> repository) {
        return new MongoRepositoryAdapter<>(client, databaseName, resolverRegistry, repository);
    }

    public static <T> @NotNull MongoRepositoryAdapterBuilder<T> builder(Class<T> repository) {
        return new MongoRepositoryAdapterBuilder<>(repository);
    }

    @Override
    public List<T> find(@NotNull SelectQuery query) {
        List<T> results = new ArrayList<>();
        List<Bson> filters = new ArrayList<>();
        for (var f : query.filters()) {
            filters.add(eq(f.option(), f.value()));
        }
        Bson filter = and(filters);
        for (var doc : collection.find(filter)) {
            results.add(parser.fromDocument(doc));
        }
        return results;
    }

    @Override
    public List<T> find() {
        List<T> results = new ArrayList<>();
        for (var doc : collection.find()) {
            results.add(parser.fromDocument(doc));
        }
        return results;
    }

    @Override
    public void insert(T value, TransactionContext<ClientSession> transactionContext) {
        Document document = parser.toDocument(value);

        // Enforce constraints before inserting
        parser.enforceConstraints(this, document);

        // Apply conditions before inserting
        document = parser.applyConditions(document);

        collection.insertOne(transactionContext.connection(), document);
    }

    @Override
    public void insertAll(final Collection<T> value, final TransactionContext<ClientSession> transactionContext) {
        List<Document> documents = new ArrayList<>();
        for (T v : value) {
            documents.add(parser.toDocument(v));
        }
        collection.insertMany(transactionContext.connection(), documents);
    }

    @Override
    public void updateAll(UpdateQuery query, TransactionContext<ClientSession> transactionContext) {
        List<Bson> conditions = new ArrayList<>();
        for (var f : query.getConditions()) {
            conditions.add(eq(f.option(), f.value()));
        }
        Bson filter = and(conditions);

        List<Bson> updates = new ArrayList<>();
        for (var e : query.getUpdates().entrySet()) {
            updates.add(Updates.set(e.getKey(), e.getValue()));
        }
        Bson update = Updates.combine(updates);

        collection.updateMany(transactionContext.connection(), filter, update);
    }

    @Override
    public void delete(DeleteQuery query, TransactionContext<ClientSession> transactionContext) {
        List<Bson> filters = new ArrayList<>();
        for (var f : query.filters()) {
            filters.add(eq(f.option(), f.value()));
        }
        Bson filter = filters.isEmpty() ? new Document() : and(filters);
        collection.deleteMany(transactionContext.connection(), filter);
    }

    @Override
    public void createRepository() {
        this.collection = database.getCollection(RepositoryMetadata.getMetadata(repository).repository());
    }

    @Override
    public TransactionContext<ClientSession> beginTransaction() {
        return new SimpleTransactionContext(client.startSession());
    }

    @Override
    public void clear() {
        collection.deleteMany(new Document());
    }

    public boolean isValueExists(String field, Object value) {
        return collection.find(eq(field, value)).first() != null;
    }
}
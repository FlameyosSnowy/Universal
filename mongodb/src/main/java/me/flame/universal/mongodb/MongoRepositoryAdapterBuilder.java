package me.flame.universal.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import me.flame.universal.mongodb.resolvers.ValueTypeResolverRegistry;

public class MongoRepositoryAdapterBuilder<T> {
    private MongoClientSettings.Builder credentialsBuilder;

    private final Class<T> repository;
    private MongoClientSettings credentials;

    private String database;

    private final ValueTypeResolverRegistry resolverRegistry = new ValueTypeResolverRegistry();

    MongoRepositoryAdapterBuilder(Class<T> repository) {
        this.repository = repository;
    }

    public MongoRepositoryAdapterBuilder<T> withCredentials(MongoClientSettings credentials) {
        this.credentials = credentials;
        return this;
    }

    public MongoRepositoryAdapterBuilder<T> withConnectionString(ConnectionString string) {
        getCredentialsBuilder().applyConnectionString(string);
        return this;
    }

    public MongoRepositoryAdapterBuilder<T> withConnectionString(String string) {
        getCredentialsBuilder().applyConnectionString(new ConnectionString(string));
        return this;
    }

    private MongoClientSettings.Builder getCredentialsBuilder() {
        return credentialsBuilder == null ? MongoClientSettings.builder() : credentialsBuilder;
    }

    public MongoRepositoryAdapter<T> build() {
        MongoClientSettings settings = this.getSettings();
        return new MongoRepositoryAdapter<>(settings, database, resolverRegistry, repository);
    }

    private MongoClientSettings getSettings() {
        return credentials == null ? getCredentialsBuilder().build() : credentials;
    }

    public MongoRepositoryAdapterBuilder<T> setDatabase(final String database) {
        this.database = database;
        return this;
    }
}
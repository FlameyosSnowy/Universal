package io.github.flameyossnowy.universal.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;

public class MongoRepositoryAdapterBuilder<T, ID> {
    private MongoClientSettings.Builder credentialsBuilder;
    private final Class<T> repository;
    private final Class<ID> idType;
    private String database;

    MongoRepositoryAdapterBuilder(Class<T> repository, Class<ID> idType) {
        this.repository = repository;
        this.idType = idType;
    }

    public MongoRepositoryAdapterBuilder<T, ID> withCredentials(MongoClientSettings credentials) {
        this.credentialsBuilder = MongoClientSettings.builder(credentials);
        return this;
    }

    public MongoRepositoryAdapterBuilder<T, ID> withConnectionString(ConnectionString string) {
        getCredentialsBuilder().applyConnectionString(string);
        return this;
    }

    public MongoRepositoryAdapterBuilder<T, ID> withConnectionString(String string) {
        getCredentialsBuilder().applyConnectionString(new ConnectionString(string));
        return this;
    }

    private MongoClientSettings.Builder getCredentialsBuilder() {
        return credentialsBuilder == null ? MongoClientSettings.builder() : credentialsBuilder;
    }

    public MongoRepositoryAdapter<T, ID> build() {
        //Cacheable cacheable = RepositoryMetadata.getMetadata(repository).getCacheable();
        return new MongoRepositoryAdapter<>(this.credentialsBuilder, database, repository, idType);
    }

    public MongoRepositoryAdapterBuilder<T, ID> setDatabase(final String database) {
        this.database = database;
        return this;
    }
}
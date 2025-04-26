package io.github.flameyossnowy.universal.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import io.github.flameyossnowy.universal.api.annotations.Cacheable;
import io.github.flameyossnowy.universal.api.annotations.GlobalCacheable;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.function.LongFunction;

public class MongoRepositoryAdapterBuilder<T, ID> {
    private MongoClientSettings.Builder credentialsBuilder;
    private final Class<T> repository;
    private final Class<ID> idType;
    private String database;

    private LongFunction<SessionCache<ID, T>> sessionCacheSupplier = (id) -> new MongoSessionCache<>();

    MongoRepositoryAdapterBuilder(Class<T> repository, Class<ID> idType) {
        this.repository = repository;
        this.idType = idType;
    }

    public MongoRepositoryAdapterBuilder<T, ID> setSessionCacheSupplier(LongFunction<SessionCache<ID, T>> sessionCacheSupplier) {
        this.sessionCacheSupplier = sessionCacheSupplier;
        return this;
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

    @SuppressWarnings("unchecked")
    public MongoRepositoryAdapter<T, ID> build() {
        GlobalCacheable cacheable = Objects.requireNonNull(RepositoryMetadata.getMetadata(repository)).getGlobalCacheable();
        if (cacheable == null)
            return new MongoRepositoryAdapter<>(this.credentialsBuilder, database, repository, idType, null, sessionCacheSupplier);

        Class<?> cacheableClass = cacheable.sessionCache();

        if (cacheableClass == SessionCache.class) {
            return new MongoRepositoryAdapter<>(this.credentialsBuilder, database, repository, idType, new MongoSessionCache<>(), sessionCacheSupplier);
        }

        if (!SessionCache.class.isAssignableFrom(cacheableClass)) {
            throw new IllegalArgumentException("Session cache must implement SessionCache interface.");
        }

        try {
            return new MongoRepositoryAdapter<>(this.credentialsBuilder, database, repository, idType, (SessionCache<ID, T>) cacheableClass.getDeclaredConstructor().newInstance(), sessionCacheSupplier);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Session cache must have default no-arg constructor.", e);
        }
    }

    public MongoRepositoryAdapterBuilder<T, ID> setDatabase(final String database) {
        this.database = database;
        return this;
    }
}
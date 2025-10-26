package io.github.flameyossnowy.universal.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import io.github.flameyossnowy.universal.api.annotations.GlobalCacheable;
import io.github.flameyossnowy.universal.api.cache.CacheWarmer;
import io.github.flameyossnowy.universal.api.cache.DefaultSessionCache;
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

    private LongFunction<SessionCache<ID, T>> sessionCacheSupplier = (id) -> new DefaultSessionCache<>();
    private CacheWarmer<T, ID> cacheWarmer;

    MongoRepositoryAdapterBuilder(Class<T> repository, Class<ID> idType) {
        this.repository = repository;
        this.idType = idType;
    }

    /**
     * Sets the supplier for session caches.
     *
     * <p>This method allows the specification of a custom session cache supplier
     * that is used to create a session cache for each session instance. The
     * supplier is given the repository ID of the instance as an argument and
     * should return a SessionCache instance configured according to the needs
     * of the repository.
     *
     * @param sessionCacheSupplier A LongFunction that accepts the repository ID
     *                             and returns a SessionCache instance.
     * @return The builder instance, for chaining method calls.
     */
    public MongoRepositoryAdapterBuilder<T, ID> setSessionCacheSupplier(LongFunction<SessionCache<ID, T>> sessionCacheSupplier) {
        this.sessionCacheSupplier = sessionCacheSupplier;
        return this;
    }

    /**
     * Sets the MongoDB connection settings using the provided MongoClientSettings.
     *
     * <p>This method allows the configuration of a custom MongoClientSettings
     * that is used to create the MongoClient used by the repository adapter.
     * The MongoClientSettings are used to connect to the MongoDB server and
     * define the configuration options such as the cluster name, the read
     * preference, the write concern, and so on.
     *
     * @param credentials A MongoClientSettings instance that defines the
     *                    connection settings for the MongoClient.
     * @return The builder instance, for chaining method calls.
     */
    public MongoRepositoryAdapterBuilder<T, ID> withCredentials(MongoClientSettings credentials) {
        this.credentialsBuilder = MongoClientSettings.builder(credentials);
        return this;
    }

    /**
     * Sets the connection string for the MongoClientSettings.
     *
     * <p>This method allows the configuration of a custom connection string
     * that is used to connect to the MongoDB server. The connection string is
     * used to define the configuration options such as the cluster name, the
     * read preference, the write concern, and so on.
     *
     * @param string A ConnectionString instance that defines the connection
     *               string for the MongoClient.
     * @return The builder instance, for chaining method calls.
     */
    public MongoRepositoryAdapterBuilder<T, ID> withConnectionString(ConnectionString string) {
        getCredentialsBuilder().applyConnectionString(string);
        return this;
    }

    /**
     * Sets the connection string for the MongoClientSettings.
     *
     * <p>This method allows the configuration of a custom connection string
     * that is used to connect to the MongoDB server. The connection string is
     * used to define the configuration options such as the cluster name, the
     * read preference, the write concern, and so on.
     *
     * @param string A string that defines the connection string for the MongoClient.
     * @return The builder instance, for chaining method calls.
     */
    public MongoRepositoryAdapterBuilder<T, ID> withConnectionString(String string) {
        getCredentialsBuilder().applyConnectionString(new ConnectionString(string));
        return this;
    }

    private MongoClientSettings.Builder getCredentialsBuilder() {
        return credentialsBuilder == null ? MongoClientSettings.builder() : credentialsBuilder;
    }

    public MongoRepositoryAdapterBuilder<T, ID> withCacheWarmer(CacheWarmer<T, ID> cacheWarmer) {
        this.cacheWarmer = cacheWarmer;
        return this;
    }

    /**
     * Builds the {@link MongoRepositoryAdapter} instance.
     *
     * <p>This method will check if the repository is annotated with {@link GlobalCacheable} annotation.
     * If it is, it will create an instance of the {@link SessionCache} using the provided constructor
     * and maximum cache size. If not, it will return a new instance of the
     * {@link MongoRepositoryAdapter} with null caches.
     *
     * @return a new instance of the {@link MongoRepositoryAdapter}
     */
    @SuppressWarnings("unchecked")
    public MongoRepositoryAdapter<T, ID> build() {
        GlobalCacheable cacheable = Objects.requireNonNull(RepositoryMetadata.getMetadata(repository)).getGlobalCacheable();
        if (cacheable == null)
            return new MongoRepositoryAdapter<>(
                    this.credentialsBuilder, database, repository,
                    idType, null, sessionCacheSupplier, cacheWarmer
            );

        Class<?> cacheableClass = cacheable.sessionCache();

        if (cacheableClass == SessionCache.class) {
            return new MongoRepositoryAdapter<>(
                    this.credentialsBuilder, database, repository,
                    idType, new DefaultSessionCache<>(), sessionCacheSupplier, cacheWarmer
            );
        }

        if (!SessionCache.class.isAssignableFrom(cacheableClass)) {
            throw new IllegalArgumentException("Session cache must implement SessionCache interface.");
        }

        try {
            return new MongoRepositoryAdapter<>(
                    this.credentialsBuilder, database, repository, idType,
                    (SessionCache<ID, T>) cacheableClass.getDeclaredConstructor().newInstance(), sessionCacheSupplier, cacheWarmer
            );
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
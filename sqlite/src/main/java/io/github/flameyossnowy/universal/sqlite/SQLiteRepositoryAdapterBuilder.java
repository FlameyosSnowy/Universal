package io.github.flameyossnowy.universal.sqlite;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.annotations.Cacheable;
import io.github.flameyossnowy.universal.api.annotations.GlobalCacheable;
import io.github.flameyossnowy.universal.api.cache.CacheWarmer;
import io.github.flameyossnowy.universal.api.cache.DefaultResultCache;
import io.github.flameyossnowy.universal.api.cache.DefaultSessionCache;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import io.github.flameyossnowy.universal.sqlite.connections.SQLiteSimpleConnectionProvider;
import io.github.flameyossnowy.universal.sqlite.credentials.SQLiteCredentials;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongFunction;

@SuppressWarnings("unused")
public class SQLiteRepositoryAdapterBuilder<T, ID> {
    private SQLiteCredentials credentials;
    private BiFunction<SQLiteCredentials, EnumSet<Optimizations>, SQLConnectionProvider> connectionProvider;
    private final EnumSet<Optimizations> optimizations = EnumSet.noneOf(Optimizations.class);
    private final Class<T> repository;
    private final Class<ID> idClass;
    private CacheWarmer<T, ID> cacheWarmer;

    private LongFunction<SessionCache<ID, T>> sessionCacheSupplier = (id) -> new DefaultSessionCache<>();

    public SQLiteRepositoryAdapterBuilder(Class<T> repository, Class<ID> id) {
        this.repository = Objects.requireNonNull(repository, "Repository cannot be null");
        this.idClass = Objects.requireNonNull(id, "ID class cannot be null");
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
    public SQLiteRepositoryAdapterBuilder<T, ID> setSessionCacheSupplier(LongFunction<SessionCache<ID, T>> sessionCacheSupplier) {
        this.sessionCacheSupplier = sessionCacheSupplier;
        return this;
    }

    /**
     * Sets the connection provider using the provided BiFunction.
     *
     * <p>This method allows the configuration of a custom SQLConnectionProvider
     * by specifying a BiFunction that takes SQLiteCredentials and an EnumSet
     * of Optimizations as input parameters. The resulting SQLConnectionProvider
     * is applied to the repository adapter, enabling customized connection
     * handling and optimization strategies.
     *
     * @param connectionProvider A BiFunction that accepts SQLiteCredentials
     *                           and EnumSet of Optimizations to produce
     *                           an SQLConnectionProvider.
     * @return The current instance of SQLiteRepositoryAdapterBuilder for chaining.
     */
    public SQLiteRepositoryAdapterBuilder<T, ID> withConnectionProvider(BiFunction<SQLiteCredentials, EnumSet<Optimizations>, SQLConnectionProvider> connectionProvider) {
        this.connectionProvider = connectionProvider;
        return this;
    }

    public SQLiteRepositoryAdapterBuilder<T, ID> withCacheWarmer(CacheWarmer<T, ID> cacheWarmer) {
        this.cacheWarmer = cacheWarmer;
        return this;
    }

    /**
     * Sets the connection provider using a simplified function that takes
     * only SQLiteCredentials as input and returns an SQLConnectionProvider.
     * This method converts the given function into a BiFunction that
     * accepts both SQLiteCredentials and an EnumSet of Optimizations.
     *
     * @param connectionProvider a function that takes SQLiteCredentials and
     *        returns an SQLConnectionProvider to be used for database connections.
     * @return the current instance of SQLiteRepositoryAdapterBuilder for chaining.
     */
    public SQLiteRepositoryAdapterBuilder<T, ID> withConnectionProvider(Function<SQLiteCredentials, SQLConnectionProvider> connectionProvider) {
        this.connectionProvider = (credentials, optimizations) -> connectionProvider.apply(credentials);
        return this;
    }

    /**
     * Specifies the SQLite database connection credentials to be used when creating
     * the RepositoryAdapter instance. The credentials provided are used to connect
     * to the SQLite database and perform all the operations.
     *
     * @param credentials the SQLite database connection credentials
     * @return the current instance of SQLiteRepositoryAdapterBuilder
     */
    public SQLiteRepositoryAdapterBuilder<T, ID> withCredentials(SQLiteCredentials credentials) {
        this.credentials = credentials;
        return this;
    }

    /**
     * Adds the specified optimization strategies to the current set of optimizations
     * for the SQLiteRepositoryAdapterBuilder. This method allows customization of the
     * repository by applying various optimization strategies as defined in the
     * {@link Optimizations} enum.
     *
     * @param optimizations optimization strategies to be applied
     * @return the current instance of SQLiteRepositoryAdapterBuilder with updated optimizations
     */
    public SQLiteRepositoryAdapterBuilder<T, ID> withOptimizations(Optimizations... optimizations) {
        Collections.addAll(this.optimizations, optimizations);
        return this;
    }

    /**
     * Adds the specified collection of optimizations to the current set of optimizations
     * for the SQLiteRepositoryAdapterBuilder. This method allows customization of the
     * repository by applying various optimization strategies as defined in the
     * {@link Optimizations} enum.
     *
     * @param optimizations a collection of optimization strategies to be applied
     * @return the current instance of SQLiteRepositoryAdapterBuilder with updated optimizations
     */
    public SQLiteRepositoryAdapterBuilder<T, ID> withOptimizations(Collection<Optimizations> optimizations) {
        this.optimizations.addAll(optimizations);
        return this;
    }

    /**
     * Builds the {@link SQLiteRepositoryAdapter} instance.
     *
     * <p>This method will check if the repository is annotated with {@link Cacheable} and
     * {@link GlobalCacheable} annotations. If it is, it will create an instance of the
     * {@link io.github.flameyossnowy.universal.api.cache.DefaultResultCache} and {@link SessionCache} using the provided algorithms and
     * maximum cache sizes. If not, it will return a new instance of the
     * {@link SQLiteRepositoryAdapter} with null caches.
     *
     * @return a new instance of the {@link SQLiteRepositoryAdapter}
     */
    @SuppressWarnings("unchecked")
    public SQLiteRepositoryAdapter<T, ID> build() {
        RepositoryInformation information = Objects.requireNonNull(RepositoryMetadata.getMetadata(this.repository));
        Cacheable cacheable = information.getCacheable();
        DefaultResultCache<String, T, ID> cache = cacheable != null ? new DefaultResultCache<>(cacheable.maxCacheSize(), cacheable.algorithm()) : null;

        GlobalCacheable globalCacheable = information.getGlobalCacheable();
        if (globalCacheable == null)
            return new SQLiteRepositoryAdapter<>(
                    connectionProvider != null ? this.connectionProvider.apply(credentials, optimizations) : new SQLiteSimpleConnectionProvider(this.credentials, optimizations),
                    cache, this.repository, this.idClass, null, sessionCacheSupplier, cacheWarmer
            );

        Class<?> cacheableClass = globalCacheable.sessionCache();

        try {
            return new SQLiteRepositoryAdapter<>(
                    connectionProvider != null ? this.connectionProvider.apply(credentials, optimizations) : new SQLiteSimpleConnectionProvider(this.credentials, optimizations),
                    cache, this.repository, this.idClass, (SessionCache<ID, T>) cacheableClass.getDeclaredConstructor().newInstance(), sessionCacheSupplier, cacheWarmer
            );
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Session cache must have default no-arg constructor.", e);
        }
    }
}

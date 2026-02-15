package io.github.flameyossnowy.universal.microservices.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.flameyossnowy.universal.api.CloseableIterator;
import io.github.flameyossnowy.universal.api.IndexOptions;
import io.github.flameyossnowy.universal.api.ModelsBootstrap;
import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.RepositoryRegistry;
import io.github.flameyossnowy.universal.api.annotations.FileRepository;
import io.github.flameyossnowy.universal.api.annotations.enums.CompressionType;
import io.github.flameyossnowy.universal.api.annotations.enums.FileFormat;
import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;
import io.github.flameyossnowy.universal.api.cache.DatabaseSession;
import io.github.flameyossnowy.universal.api.cache.SessionOption;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.factory.ObjectModel;
import io.github.flameyossnowy.universal.api.factory.RelationshipLoader;
import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.GeneratedMetadata;
import io.github.flameyossnowy.universal.api.meta.GeneratedObjectFactories;
import io.github.flameyossnowy.universal.api.meta.GeneratedRelationshipLoaders;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.operation.Operation;
import io.github.flameyossnowy.universal.api.operation.OperationContext;
import io.github.flameyossnowy.universal.api.operation.OperationExecutor;
import io.github.flameyossnowy.universal.api.options.AggregateFieldDefinition;
import io.github.flameyossnowy.universal.api.options.AggregateFilterOption;
import io.github.flameyossnowy.universal.api.options.AggregationQuery;
import io.github.flameyossnowy.universal.api.options.AggregationType;
import io.github.flameyossnowy.universal.api.options.DeleteQuery;
import io.github.flameyossnowy.universal.api.options.FieldDefinition;
import io.github.flameyossnowy.universal.api.options.FilterOption;
import io.github.flameyossnowy.universal.api.options.JsonSelectOption;
import io.github.flameyossnowy.universal.api.options.QueryField;
import io.github.flameyossnowy.universal.api.options.SimpleFieldDefinition;
import io.github.flameyossnowy.universal.api.options.SelectOption;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.options.SortOrder;
import io.github.flameyossnowy.universal.api.options.SortOption;
import io.github.flameyossnowy.universal.api.options.SubQuery;
import io.github.flameyossnowy.universal.api.options.UpdateQuery;
import io.github.flameyossnowy.universal.api.options.WindowFieldDefinition;
import io.github.flameyossnowy.universal.api.options.WindowQuery;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.microservices.MicroservicesJsonCodecBridge;
import io.github.flameyossnowy.universal.microservices.TypeResolverJacksonModule;
import io.github.flameyossnowy.universal.microservices.file.indexes.IndexPathStrategies;
import io.github.flameyossnowy.universal.microservices.file.indexes.IndexPathStrategy;
import io.github.flameyossnowy.universal.microservices.file.indexes.SecondaryIndex;
import io.github.flameyossnowy.universal.microservices.relationship.MicroserviceRelationshipHandler;
import io.github.flameyossnowy.universal.microservices.relationship.RelationshipResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * File-based repository adapter that stores entities in files.
 * <p>
 * Supports various file formats JSON only and compression options.
 *
 * @param <T> The entity type
 * @param <ID> The ID type
 */
public class FileRepositoryAdapter<T, ID> implements RepositoryAdapter<T, ID, FileContext> {
    static {
        ModelsBootstrap.init();
    }

    private long countAllFilesFast() throws IOException {
        long count = 0L;

        if (sharding) {
            for (int i = 0; i < shardCount; i++) {
                Path shardPath = basePath.resolve(String.valueOf(i));
                if (!Files.exists(shardPath)) {
                    continue;
                }
                count += countRegularEntityFiles(shardPath);
            }
        } else {
            count += countRegularEntityFiles(basePath);
        }

        return count;
    }

    private long countRegularEntityFiles(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return 0L;
        }

        try (var files = Files.list(directory)) {
            return files
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(getFileExtension()))
                .count();
        }
    }

    private long countDirectoryMatches(Path directory, @NotNull SelectQuery query) throws IOException {
        if (!Files.exists(directory)) {
            return 0L;
        }

        long count = 0L;
        try (var files = Files.list(directory)) {
            for (Path path : (Iterable<Path>) files::iterator) {
                if (!Files.isRegularFile(path)) continue;
                if (!path.getFileName().toString().endsWith(getFileExtension())) continue;

                T entity = readEntity(path);
                if (!matchesAll(entity, query.filters())) {
                    continue;
                }

                count++;
            }
        }

        return count;
    }

    private final Class<T> entityType;
    private final Class<ID> idType;
    private final RepositoryModel<T, ID> repositoryModel;
    private final TypeResolverRegistry resolverRegistry;
    private final OperationExecutor<T, ID, FileContext> operationExecutor;
    private final OperationContext<T, ID, FileContext> operationContext;
    private final ObjectMapper objectMapper;
    private final Path basePath;
    private final FileFormat format;
    private final boolean compressed;
    private final CompressionType compressionType;
    private final boolean sharding;
    private final int shardCount;
    private final RelationshipLoader<T, ID> relationshipLoader;
    private final ObjectModel<T, ID> objectModel;

    private final Map<String, SecondaryIndex<ID>> indexes = new ConcurrentHashMap<>(16);

    private static final int STRIPE_COUNT = 64;
    private final ReentrantReadWriteLock[] stripes = new ReentrantReadWriteLock[STRIPE_COUNT];
    private final Path indexRoot;

    {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            //noinspection ObjectAllocationInLoop
            stripes[i] = new ReentrantReadWriteLock();
        }
    }

    @Override
    public List<T> find() {
        return find(ReadPolicy.NO_READ_POLICY);
    }

    @Override
    public List<T> find(ReadPolicy policy) {
        return find(null, policy);
    }

    @Override
    public long count(SelectQuery query, ReadPolicy policy) {
        try {
            if (query == null) {
                return countAllFilesFast();
            }

            if (query.limit() == 0) {
                return 0L;
            }

            long count = 0L;

            if (sharding) {
                for (int i = 0; i < shardCount; i++) {
                    Path shardPath = basePath.resolve(String.valueOf(i));
                    if (!Files.exists(shardPath)) {
                        continue;
                    }
                    count += countDirectoryMatches(shardPath, query);
                }
            } else {
                count += countDirectoryMatches(basePath, query);
            }

            return count;
        } catch (IOException e) {
            throw new RuntimeException("Failed to count entities", e);
        }
    }

    @Override
    public long count(ReadPolicy policy) {
        return count(null, policy);
    }

    private ReentrantReadWriteLock getLockForId(ID id) {
        return stripes[(id.hashCode() & Integer.MAX_VALUE) % STRIPE_COUNT];
    }

    // In-memory cache for quick access
    private final Map<ID, T> cache = new ConcurrentHashMap<>(128);

    private final RelationshipResolver<T, ID> relationshipResolver;

    FileRepositoryAdapter(
            @NotNull Class<T> entityType,
            @NotNull Class<ID> idType,
            @NotNull Path basePath,
            FileFormat format,
            boolean compressed,
            CompressionType compressionType,
            boolean sharding,
            int shardCount,
            IndexPathStrategy indexPathStrategy
    ) {
        this.indexRoot = indexPathStrategy.resolveIndexRoot(basePath, entityType);
        this.entityType = entityType;
        this.idType = idType;
        this.basePath = basePath;
        this.format = format;
        this.compressed = compressed;
        this.compressionType = compressionType;
        this.sharding = sharding;
        this.shardCount = shardCount;

        this.repositoryModel = GeneratedMetadata.getByEntityClass(entityType);
        if (repositoryModel == null) {
            throw new IllegalArgumentException("Entity " + entityType.getName() + " must be annotated with @Repository");
        }

        this.resolverRegistry = new TypeResolverRegistry();
        for (Class<? extends TypeResolver<?>> resolverClass : repositoryModel.getRequiredResolvers()) {
            try {
                this.resolverRegistry.register(resolverClass.getDeclaredConstructor().newInstance());
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate TypeResolver: " + resolverClass, e);
            }
        }

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new TypeResolverJacksonModule(resolverRegistry));
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Enable DefaultJsonCodec usage in microservices/file layer
        this.resolverRegistry.setObjectMapperSupplier(() -> this.objectMapper);

        this.operationExecutor = new FileOperationExecutor<>(this);
        this.operationContext = new OperationContext<>(
            repositoryModel,
            resolverRegistry,
            operationExecutor
        );

        this.objectModel = GeneratedObjectFactories.getObjectModel(repositoryModel);

        MicroserviceRelationshipHandler<T, ID> handler = new MicroserviceRelationshipHandler<>(repositoryModel, idType, resolverRegistry);
        this.relationshipLoader = GeneratedRelationshipLoaders.get(repositoryModel.tableName(), handler, null, repositoryModel);
        this.relationshipResolver = new RelationshipResolver<>(handler);

        RepositoryRegistry.register(repositoryModel.tableName(), this);

        try {
            if (Files.exists(basePath)) {
                return;
            }
            Files.createDirectories(basePath);
            if (sharding) {
                for (int i = 0; i < shardCount; i++) {
                    Files.createDirectories(basePath.resolve(String.valueOf(i)));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create base directory: " + basePath, e);
        }
    }

    public static <T, ID> FileRepositoryBuilder<T, ID> builder(@NotNull Class<T> entityType, @NotNull Class<ID> idType) {
        return new FileRepositoryBuilder<>(entityType, idType);
    }

    public static <T, ID> FileRepositoryAdapter<T, ID> from(
        @NotNull Class <T> entityType,
        @NotNull Class <ID> idType) {
        FileRepository annotation = entityType.getAnnotation(FileRepository.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                "Entity " + entityType.getName() + " must be annotated with @FileRepository");
        }

        return new FileRepositoryAdapter<>(
            entityType,
            idType,
            Paths.get(annotation.path()),
            annotation.format(),
            annotation.compressed(),
            annotation.compression(),
            annotation.sharding(),
            annotation.shardCount(),
            IndexPathStrategies.underBase()
        );
    }

    @Override
    @NotNull
    public <R> TransactionResult <R> execute(
        @NotNull Operation<T, ID, R, FileContext> operation,
        @NotNull TransactionContext<FileContext> transactionContext) {
        return operation.executeWithTransaction(operationContext, transactionContext);
    }

    @Override
    @NotNull
    public OperationContext<T, ID, FileContext> getOperationContext() {
        return operationContext;
    }

    @Override
    @NotNull
    public OperationExecutor<T, ID, FileContext> getOperationExecutor() {
        return operationExecutor;
    }

    @Override
    @NotNull
    public RepositoryModel<T, ID> getRepositoryModel() {
        return repositoryModel;
    }

    @Override
    @NotNull
    public TypeResolverRegistry getTypeResolverRegistry() {
        return resolverRegistry;
    }

    @Override
    @NotNull
    public Class<T> getEntityType() {
        return entityType;
    }

    @Override
    @NotNull
    public Class<ID> getIdType() {
        return idType;
    }

    @Override
    @NotNull
    public TransactionContext<FileContext> beginTransaction() {
        return new FileTransactionContext();
    }

    @Override
    @NotNull
    public List<ID> findIds(SelectQuery query) {
        if (query == null) {
            return findAllIdsFast();
        }
        if (query.limit() == 0) {
            return List.of();
        }
        return findIdsWithQuery(query);
    }


    private List<ID> findAllIdsFast() {
        try {
            List<ID> ids = new ArrayList<>(32);

            if (sharding) {
                for (int i = 0; i < shardCount; i++) {
                    Path shardPath = basePath.resolve(String.valueOf(i));
                    if (!Files.exists(shardPath)) {
                        continue;
                    }
                    scanDirectoryForIds(shardPath, null, ids);
                }
            } else {
                scanDirectoryForIds(basePath, null, ids);
            }

            return ids;
        } catch (IOException e) {
            throw new RuntimeException("Failed to find all IDs", e);
        }
    }

    private List<ID> findIdsWithQuery(@NotNull SelectQuery query) {
        try {
            int expectedSize = query.limit() > 0 ? query.limit() : 16;
            List<ID> ids = new ArrayList<>(expectedSize);

            if (sharding) {
                for (int i = 0; i < shardCount; i++) {
                    Path shardPath = basePath.resolve(String.valueOf(i));
                    if (!Files.exists(shardPath)) {
                        continue;
                    }

                    scanDirectoryForIds(shardPath, query, ids);

                    if (query.limit() >= 0 && ids.size() >= query.limit()) {
                        break;
                    }
                }
            } else {
                scanDirectoryForIds(basePath, query, ids);
            }

            return ids;
        } catch (IOException e) {
            throw new RuntimeException("Failed to find IDs", e);
        }
    }

    private void scanDirectoryForIds(
        Path directory,
        SelectQuery query,
        List<ID> ids
    ) throws IOException {

        if (!Files.exists(directory)) return;

        try (var files = Files.list(directory)) {
            for (Path path : (Iterable<Path>) files::iterator) {
                if (!Files.isRegularFile(path)) continue;
                if (!path.getFileName().toString().endsWith(getFileExtension())) continue;

                T entity = readEntity(path);

                if (query != null && !matchesAll(entity, query.filters())) {
                    continue;
                }

                ids.add(extractId(entity));

                if (query != null && query.limit() >= 0 && ids.size() >= query.limit()) {
                    return;
                }
            }
        }
    }

    private T readEntity(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            InputStream input = compressed ? unwrapCompression(is) : is;
            var storedNode = objectMapper.readTree(input);
            T result = MicroservicesJsonCodecBridge.readEntityFromStorageJson(
                objectMapper,
                resolverRegistry,
                repositoryModel,
                entityType,
                storedNode
            );
            objectModel.populateRelationships(result, objectModel.getId(result), relationshipLoader);
            return result;
        }
    }

    @Override
    public void close() {
        cache.clear();
        RepositoryRegistry.unregister(repositoryModel.tableName());
    }

    // File operations
    public Path getEntityPath(@NotNull ID id) {
        String fileName = id.toString();
        String extension = getFileExtension();

        if (sharding) {
            int shard = Math.abs(id.hashCode() % shardCount);
            return basePath.resolve(String.valueOf(shard)).resolve(fileName + extension);
        }

        return basePath.resolve(fileName + extension);
    }

    private String getFileExtension() {
        String ext = switch (format) {
            case JSON -> ".json";
        };

        if (compressed) {
            ext += switch (compressionType) {
                case GZIP -> ".gz";
                case ZIP -> ".zip";
                case BZIP2 -> ".bz2";
                case LZ4 -> ".lz4";
                case ZSTD -> ".zst";
            };
        }

        return ext;
    }

    public void writeEntity(T entity, ID id) throws IOException {
        ReentrantReadWriteLock idLock = getLockForId(id);
        idLock.writeLock().lock();
        try {
            Path path = getEntityPath(id);
            Files.createDirectories(path.getParent());

            try (OutputStream os = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                OutputStream output = compressed ? wrapCompression(os) : os;

                if (Objects.requireNonNull(format) == FileFormat.JSON) {
                    objectMapper.writeValue(
                        output,
                        MicroservicesJsonCodecBridge.toStorageJson(objectMapper, resolverRegistry, repositoryModel, entity)
                    );
                }

                if (compressed) {
                    output.close();
                }
            }

            cache.put(id, entity);
        } finally {
            idLock.writeLock().unlock();
        }
    }

    public @Nullable T readEntity(ID id) throws IOException {
        // Check cache first
        T cached = cache.get(id);
        if (cached != null) {
            return cached;
        }

        ReentrantReadWriteLock idLock = getLockForId(id);
        idLock.readLock().lock();
        try {
            // Re-check cache in case another thread populated it while waiting for the lock
            T rechecked = cache.get(id);
            if (rechecked != null) {
                return rechecked;
            }

            Path path = getEntityPath(id);
            if (!Files.exists(path)) {
                return null;
            }

            try (InputStream is = Files.newInputStream(path)) {
                InputStream input = compressed ? unwrapCompression(is) : is;

                var storedNode = objectMapper.readTree(input);
                T entity = MicroservicesJsonCodecBridge.readEntityFromStorageJson(
                    objectMapper,
                    resolverRegistry,
                    repositoryModel,
                    entityType,
                    storedNode
                );

                relationshipResolver.resolve(entity, repositoryModel);
                cache.put(id, entity);
                return entity;
            }
        } finally {
            idLock.readLock().unlock();
        }
    }

    public void deleteEntity(ID id) throws IOException {
        ReentrantReadWriteLock idLock = getLockForId(id);
        idLock.writeLock().lock();
        try {
            Path path = getEntityPath(id);
            Files.deleteIfExists(path);
            cache.remove(id);
        } finally {
            idLock.writeLock().unlock();
        }
    }

    public List<T> readAll() throws IOException {
        List<T> results = new ArrayList<>(32);
        if (sharding) {
            for (int i = 0; i < shardCount; i++) {
                Path shardPath = basePath.resolve(String.valueOf(i));
                if (Files.exists(shardPath)) {
                    results.addAll(readFromDirectory(shardPath));
                }
            }
        } else {
            results.addAll(readFromDirectory(basePath));
        }
        return results;
    }

    private List<T> readFromDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return List.of();
        }

        try (var stream = Files.list(directory)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(getFileExtension()))
                .map(path -> {
                    try (InputStream is = Files.newInputStream(path)) {
                        InputStream input = compressed ? unwrapCompression(is) : is;

                        var storedNode = objectMapper.readTree(input);
                        T entity = MicroservicesJsonCodecBridge.readEntityFromStorageJson(
                            objectMapper,
                            resolverRegistry,
                            repositoryModel,
                            entityType,
                            storedNode
                        );
                        relationshipResolver.resolve(entity, repositoryModel);
                        return entity;
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .collect(Collectors.toList());
        }
    }

    private OutputStream wrapCompression(OutputStream os) throws IOException {
        return switch (compressionType) {
            case GZIP -> new GZIPOutputStream(os);
            case ZIP, BZIP2, LZ4, ZSTD ->
                throw new UnsupportedOperationException("Compression type " + compressionType + " not yet implemented");
        };
    }

    private InputStream unwrapCompression(InputStream is) throws IOException {
        return switch (compressionType) {
            case GZIP -> new GZIPInputStream(is);
            case ZIP, BZIP2, LZ4, ZSTD ->
                throw new UnsupportedOperationException("Compression type " + compressionType + " not yet implemented");
        };
    }

    public Path getBasePath() {
        return basePath;
    }

    // RepositoryAdapter interface implementation

    @Override
    public TransactionResult<Boolean> createRepository(boolean ifNotExists){
        return TransactionResult.success(true);
    }

    @Override
    public DatabaseSession<ID, T, FileContext> createSession() {
        return createSession(EnumSet.noneOf(SessionOption.class));
    }

    @Override
    public DatabaseSession<ID, T, FileContext> createSession(EnumSet < SessionOption > options) {
        return new FileSession<>(this, options);
    }

    @Override
    public List<T> find(SelectQuery query) {
        return find(query, ReadPolicy.NO_READ_POLICY);
    }

    @Override
    public List<T> find(SelectQuery query, ReadPolicy policy) {
        try {
            // Fast path
            if (query == null) {
                return readAll();
            }

            int expectedSize = query.limit() > 0 ? query.limit() : 16;
            List<T> results = new ArrayList<>(expectedSize);

            if (sharding) {
                for (int i = 0; i < shardCount; i++) {
                    Path shardPath = basePath.resolve(String.valueOf(i));
                    if (!Files.exists(shardPath)) continue;

                    scanDirectory(shardPath, query, results);
                    if (query.limit() >= 0 && results.size() >= query.limit()) {
                        break;
                    }
                }
            } else {
                scanDirectory(basePath, query, results);
            }

            // Sorting happens AFTER filtering
            applySortingIfNeeded(results, query);

            // Hard limit enforcement (sorting may exceed limit)
            if (query.limit() >= 0 && results.size() > query.limit()) {
                return results.subList(0, query.limit());
            }

            return results;
        } catch (IOException e) {
            throw new RuntimeException("Failed to find entities", e);
        }
    }

    private void applySortingIfNeeded(List<T> results, SelectQuery query) {
        if (query.sortOptions() == null || query.sortOptions().isEmpty()) return;

        Comparator<T> comparator = null;

        for (SortOption option : query.sortOptions()) {
            Comparator<T> next = compareBySortField(option);
            comparator = (comparator == null) ? next : comparator.thenComparing(next);
        }

        results.sort(comparator);
    }

    private void scanDirectory(
        Path directory,
        SelectQuery query,
        List<T> results
    ) throws IOException {

        if (!Files.exists(directory)) return;

        try (var files = Files.list(directory)) {
            for (Path path : (Iterable<Path>) files::iterator) {
                if (!Files.isRegularFile(path)) continue;
                if (!path.getFileName().toString().endsWith(getFileExtension())) continue;

                T entity = readEntity(path);

                if (!matchesAll(entity, query.filters())) {
                    continue;
                }

                results.add(entity);

                // Early stop if limit reached
                if (query.limit() >= 0 && results.size() >= query.limit()) {
                    return;
                }
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private @NotNull Comparator<T> compareBySortField(SortOption sortOption) {
        Comparator currentComparator = Comparator.comparing(entity -> {
            try {
                return (Comparable) ((FieldModel<Object>) repositoryModel.fieldByName(sortOption.field())).getValue(entity);
            } catch (Exception e) {
                throw new RuntimeException("Failed to get sort field value", e);
            }
        });

        if (sortOption.order() == SortOrder.DESCENDING) {
            currentComparator = currentComparator.reversed();
        }

        return currentComparator;
    }

    private boolean matches(T entity, FilterOption filter){
        try {
            if (filter instanceof SelectOption s) {
                return matchesSelectOption(entity, s);
            }

            if (filter instanceof JsonSelectOption j) {
                return matchesJsonSelectOption(entity, j);
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean matchesSelectOption(T entity, SelectOption filter) {
        var field = repositoryModel.fieldByName(filter.option());
        if (field == null) {
            return false;
        }

        Object value = field.getValue(entity);
        Object filterValue = filter.value();
        String operator = filter.operator();

        if (value == null) {
            return filterValue == null;
        }

        return switch (operator) {
            case "=" -> value.equals(filterValue);
            case "!=" -> !value.equals(filterValue);
            case ">" -> value instanceof Comparable && filterValue != null && ((Comparable) value).compareTo(filterValue) > 0;
            case "<" -> value instanceof Comparable && filterValue != null && ((Comparable) value).compareTo(filterValue) < 0;
            case ">=" -> value instanceof Comparable && filterValue != null && ((Comparable) value).compareTo(filterValue) >= 0;
            case "<=" -> value instanceof Comparable && filterValue != null && ((Comparable) value).compareTo(filterValue) <= 0;
            case "IN" -> {
                if (filterValue instanceof SubQuery sq) {
                    yield evaluateInSubQuery(entity, filter.option(), sq, false);
                }
                yield filterValue instanceof Collection<?> list && list.contains(value);
            }
            case "NOT IN" -> {
                if (filterValue instanceof SubQuery sq) {
                    yield evaluateInSubQuery(entity, filter.option(), sq, true);
                }
                yield !(filterValue instanceof Collection<?> list) || !list.contains(value);
            }
            case "EXISTS" -> filterValue instanceof SubQuery sq && evaluateExistsSubQuery(entity, sq, false);
            case "NOT EXISTS" -> !(filterValue instanceof SubQuery sq) || evaluateExistsSubQuery(entity, sq, true);
            default -> false;
        };
    }

    private boolean evaluateInSubQuery(T entity, String localField, @NotNull SubQuery subQuery, boolean negate) {
        List<Object> values = executeSubQuerySelectSingleField(entity, subQuery);
        Object localValue = repositoryModel.fieldByName(localField).getValue(entity);
        boolean contains = values.contains(localValue);
        return negate != contains;
    }

    private boolean evaluateExistsSubQuery(T entity, @NotNull SubQuery subQuery, boolean negate) {
        boolean exists = !executeSubQuerySelectSingleField(entity, subQuery).isEmpty();
        return negate != exists;
    }

    private @NotNull List<Object> executeSubQuerySelectSingleField(T outerEntity, @NotNull SubQuery subQuery) {
        @SuppressWarnings("unchecked")
        RepositoryAdapter<Object, Object, ?> adapter = (RepositoryAdapter<Object, Object, ?>) RepositoryRegistry.get(subQuery.entityClass());
        if (adapter == null) {
            throw new IllegalStateException("No adapter registered for subquery entity: " + subQuery.entityClass().getName());
        }

        String selectedField = null;
        if (subQuery.selectFields() != null && subQuery.selectFields().size() == 1) {
            selectedField = subQuery.selectFields().getFirst().getFieldName();
        }

        if (selectedField == null) {
            throw new UnsupportedOperationException("File subqueries currently require selecting exactly one simple field");
        }

        // Execute subquery as a SelectQuery against the target adapter.
        // Note: correlation via OuterFieldReference is not supported in file adapter currently.
        SelectQuery sq = new SelectQuery(
            List.of(selectedField),
            subQuery.whereFilters(),
            subQuery.orderBy(),
            subQuery.limit(),
            null
        );

        List<Object> objects = adapter.find(sq, ReadPolicy.NO_READ_POLICY);
        List<Object> out = new ArrayList<>(objects.size());
        for (Object e : objects) {
            RepositoryModel<Object, Object> model = adapter.getRepositoryModel();
            FieldModel<Object> fm = model.fieldByName(selectedField);
            if (fm != null) {
                out.add(fm.getValue(e));
            }
        }
        return out;
    }

    @Override
    public List<Map<String, Object>> aggregate(@NotNull AggregationQuery query) {
        if (query.limit() == 0) {
            return Collections.emptyList();
        }

        // Filter base rows using WHERE
        List<T> base = find(new SelectQuery(
            Collections.emptyList(),
            query.whereFilters(),
            Collections.emptyList(),
            -1,
            null
        ));

        List<String> groupBy = query.groupByFields() == null ? Collections.emptyList() : query.groupByFields();
        Map<List<Object>, List<T>> groups = new LinkedHashMap<>(groupBy.size());

        List<Object> key = new ArrayList<>(groupBy.size());
        for (T entity : base) {
            for (String field : groupBy) {
                var fm = repositoryModel.fieldByName(field);
                key.add(fm != null ? fm.getValue(entity) : null);
            }
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entity);
            key.clear();
        }

        List<Map<String, Object>> rows = new ArrayList<>(groups.size());
        for (List<T> group : groups.values()) {
            Map<String, Object> row = evaluateAggregationRow(query.selectFields(), group);
            if (matchesAggregatedRowAll(row, group, query.havingFilters())) {
                rows.add(row);
            }
        }

        // ORDER BY (on produced row fields)
        if (query.orderBy() != null && !query.orderBy().isEmpty()) {
            rows.sort(createComparator(query));
        }

        if (query.limit() >= 0 && rows.size() > query.limit()) {
            return rows.subList(0, query.limit());
        }
        return rows;
    }

    private static @Nullable Comparator<Map<String, Object>> createComparator(@NotNull AggregationQuery query) {
        Comparator<Map<String, Object>> comparator = null;
        for (SortOption sort : query.orderBy()) {
            @SuppressWarnings("unchecked")
            Comparator<Map<String, Object>> next = Comparator.comparing(m -> (Comparable<Object>) m.get(sort.field()), Comparator.nullsFirst(Comparator.naturalOrder()));

            if (sort.order() == SortOrder.DESCENDING) {
                next = next.reversed();
            }
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        return comparator;
    }

    @Override
    public List<Map<String, Object>> window(@NotNull WindowQuery query) {
        if (query.limit() == 0) {
            return Collections.emptyList();
        }

        // Base data set from WHERE
        List<T> base = find(new SelectQuery(
            Collections.emptyList(),
            query.whereFilters(),
            Collections.emptyList(),
            -1,
            null
        ));

        // Apply top-level ORDER BY (for final results)
        if (!query.orderBy().isEmpty()) {
            Comparator<T> comparator = null;
            for (SortOption option : query.orderBy()) {
                Comparator<T> next = compareBySortField(option);
                comparator = (comparator == null) ? next : comparator.thenComparing(next);
            }
            base.sort(comparator);
        }

        List<Map<String, Object>> rows = evaluateWindowRows(query.selectFields(), base);
        if (query.limit() >= 0 && rows.size() > query.limit()) {
            return rows.subList(0, query.limit());
        }
        return rows;
    }

    @Override
    public <R> List<R> window(@NotNull WindowQuery query, @NotNull Class<R> resultType) {
        List<Map<String, Object>> rows = window(query);
        List<R> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            out.add(objectMapper.convertValue(row, resultType));
        }
        return out;
    }

    @Override
    public List<Map<String, Object>> executeAggregation(@NotNull Object rawQuery) {
        throw new UnsupportedOperationException("File adapter does not support raw aggregation execution");
    }

    @Override
    public <R> R aggregateScalar(@NotNull AggregationQuery query, @NotNull String fieldName, @NotNull Class<R> type) {
        List<Map<String, Object>> rows = aggregate(query);
        if (rows.isEmpty()) {
            return null;
        }
        Object v = rows.getFirst().get(fieldName);
        return objectMapper.convertValue(v, type);
    }

    private @NotNull Map<String, Object> evaluateAggregationRow(@NotNull List<FieldDefinition> fields, @NotNull List<T> group) {
        Map<String, Object> row = new LinkedHashMap<>(fields.size());
        T first = group.getFirst();

        for (FieldDefinition fd : fields) {
            if (fd instanceof SimpleFieldDefinition s) {
                var fm = repositoryModel.fieldByName(s.field());
                row.put(s.getFieldName(), fm != null ? fm.getValue(first) : null);
                continue;
            }

            if (fd instanceof QueryField<?> s) {
                var fm = repositoryModel.fieldByName(s.getFieldName());
                row.put(s.getFieldName(), fm != null ? fm.getValue(first) : null);
                continue;
            }

            if (fd instanceof AggregateFieldDefinition a) {
                row.put(a.alias(), computeAggregate(a, group));
                continue;
            }

            if (fd instanceof SubQuery.SubQueryFieldDefinition) {
                throw new UnsupportedOperationException("Scalar subqueries in SELECT are not supported by file aggregation yet");
            }

            // If an adapter accidentally passes WindowFieldDefinition into aggregation
            if (fd instanceof WindowFieldDefinition w) {
                throw new UnsupportedOperationException("Window fields are not valid in AggregationQuery: " + w.alias());
            }

            throw new UnsupportedOperationException("Unsupported field definition: " + fd.getClass().getName());
        }

        return row;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Object computeAggregate(@NotNull AggregateFieldDefinition a, @NotNull List<T> group) {
        AggregationType type = a.aggregationType();

        // Field extraction helper
        java.util.function.Function<T, Object> extractor = e -> {
            var fm = repositoryModel.fieldByName(a.field());
            Object base = fm != null ? fm.getValue(e) : null;
            if (a.isJson()) {
                JsonNode root = objectMapper.valueToTree(base);
                JsonNode selected = selectJsonPath(root, a.jsonPath());
                return selected == null ? null : objectMapper.convertValue(selected, Object.class);
            }
            return base;
        };

        return switch (type) {
            case COUNT -> (long) group.size();
            case COUNT_DISTINCT -> group.stream().map(extractor).filter(Objects::nonNull).distinct().count();
            case MIN -> group.stream().map(extractor).filter(Objects::nonNull).min((o1, o2) -> ((Comparable) o1).compareTo(o2)).orElse(null);
            case MAX -> group.stream().map(extractor).filter(Objects::nonNull).max((o1, o2) -> ((Comparable) o1).compareTo(o2)).orElse(null);
            case SUM -> sumNumbers(group, extractor);
            case AVG -> avgNumbers(group, extractor);
            case COUNT_IF -> countIf(group, a.field(), a.condition());
            case SUM_IF -> sumIf(group, a.field(), a.condition(), extractor);
            case ARRAY_LENGTH -> arrayLengthAggregate(group, extractor);
            default -> throw new UnsupportedOperationException("Aggregation not supported in file adapter: " + type);
        };
    }

    private long countIf(@NotNull List<T> group, @NotNull String field, @Nullable FilterOption condition) {
        if (condition == null) {
            return group.size();
        }

        // The DSL helper Query.eq(value) currently builds a SelectOption with empty option.
        // Interpret that as: <field> <op> <value>
        if (condition instanceof SelectOption(
            String option, String operator, Object value
        ) && (option == null || option.isBlank())) {
            return group.stream().filter(e -> matchesSelectOption(e, new SelectOption(field, operator, value))).count();
        }

        return group.stream().filter(e -> matches(e, condition)).count();
    }

    private @Nullable Double sumNumbers(@NotNull List<T> group, java.util.function.Function<T, Object> extractor) {
        double sum = 0d;
        boolean seen = false;
        for (T e : group) {
            Object v = extractor.apply(e);
            if (v instanceof Number n) {
                sum += n.doubleValue();
                seen = true;
            }
        }
        return seen ? sum : null;
    }

    private @Nullable Double avgNumbers(@NotNull List<T> group, java.util.function.Function<T, Object> extractor) {
        double sum = 0d;
        long count = 0;
        for (T e : group) {
            Object v = extractor.apply(e);
            if (v instanceof Number n) {
                sum += n.doubleValue();
                count++;
            }
        }
        return count == 0 ? null : (sum / count);
    }

    private @Nullable Double sumIf(
        @NotNull List<T> group,
        @NotNull String field,
        @Nullable FilterOption condition,
        java.util.function.Function<T, Object> extractor
    ) {
        if (condition == null) {
            return sumNumbers(group, extractor);
        }

        double sum = 0d;
        boolean seen = false;
        for (T e : group) {
            boolean matches;
            if (condition instanceof SelectOption(
                String option, String operator, Object value
            ) && (option == null || option.isBlank())) {
                matches = matchesSelectOption(e, new SelectOption(field, operator, value));
            } else {
                matches = matches(e, condition);
            }

            if (!matches) {
                continue;
            }

            Object v = extractor.apply(e);
            if (v instanceof Number n) {
                sum += n.doubleValue();
                seen = true;
            }
        }

        return seen ? sum : null;
    }

    private @Nullable Integer arrayLengthAggregate(@NotNull List<T> group, java.util.function.Function<T, Object> extractor) {
        // For grouped results, arrayLength only makes sense when grouped on an array field (we'll return first non-null)
        for (T e : group) {
            Object v = extractor.apply(e);
            if (v instanceof Collection<?> c) {
                return c.size();
            }
            if (v != null && v.getClass().isArray()) {
                return java.lang.reflect.Array.getLength(v);
            }
            if (v instanceof JsonNode node && node.isArray()) {
                return node.size();
            }
        }
        return null;
    }

    private boolean matchesAggregatedRowAll(@NotNull Map<String, Object> row, @NotNull List<T> group, @Nullable List<FilterOption> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (FilterOption f : filters) {
            if (!matchesAggregatedRow(row, group, f)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean matchesAggregatedRow(@NotNull Map<String, Object> row, @NotNull List<T> group, @NotNull FilterOption filter) {
        if (filter instanceof SelectOption(String option, String operator1, Object value1)) {
            return matchesAggregatedValue(row.get(option), operator1, value1);
        }

        if (filter instanceof AggregateFilterOption(
            String field, String jsonPath, String operator, Object value, AggregationType aggregationType,
            FilterOption condition, String alias
        )) {
            Object actual;
            if (alias != null && !alias.isBlank()) {
                actual = row.get(alias);
            } else {
                // HAVING commonly uses field("id").count().gt(1) without alias.
                // Recompute the aggregate for the current group.
                actual = computeAggregate(new AggregateFieldDefinition(
                    field,
                    jsonPath,
                    aggregationType,
                    condition,
                    "__having"
                ), group);
            }

            return matchesAggregatedValue(actual, operator, value);
        }

        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean matchesAggregatedValue(@Nullable Object actual, @NotNull String operator, @Nullable Object expected) {
        if (actual == null) {
            return expected == null;
        }

        // Make numeric comparisons robust (e.g. Long vs Integer)
        if (actual instanceof Number an && expected instanceof Number en) {
            double a = an.doubleValue();
            double e = en.doubleValue();
            return switch (operator) {
                case "=" -> Double.compare(a, e) == 0;
                case "!=" -> Double.compare(a, e) != 0;
                case ">" -> a > e;
                case "<" -> a < e;
                case ">=" -> a >= e;
                case "<=" -> a <= e;
                default -> false;
            };
        }

        return switch (operator) {
            case "=" -> actual.equals(expected);
            case "!=" -> !actual.equals(expected);
            case ">" -> actual instanceof Comparable c && expected != null && c.compareTo(expected) > 0;
            case "<" -> actual instanceof Comparable c && expected != null && c.compareTo(expected) < 0;
            case ">=" -> actual instanceof Comparable c && expected != null && c.compareTo(expected) >= 0;
            case "<=" -> actual instanceof Comparable c && expected != null && c.compareTo(expected) <= 0;
            case "IN" -> expected instanceof Collection<?> list && list.contains(actual);
            default -> false;
        };
    }

    private @NotNull List<Map<String, Object>> evaluateWindowRows(@NotNull List<FieldDefinition> fields, @NotNull List<T> base) {
        // Prepare base rows
        List<Map<String, Object>> rows = new ArrayList<>(base.size());
        Map<String, Object> row = new LinkedHashMap<>(fields.size());
        for (T e : base) {
            for (FieldDefinition fd : fields) {
                if (fd instanceof SimpleFieldDefinition s) {
                    var fm = repositoryModel.fieldByName(s.field());
                    row.put(s.getFieldName(), fm != null ? fm.getValue(e) : null);
                } else if (fd instanceof WindowFieldDefinition w) {
                    // fill later
                    row.put(w.alias(), null);
                } else {
                    throw new UnsupportedOperationException("Unsupported field in WindowQuery: " + fd.getClass().getName());
                }
            }
            // keep a backref for window computations
            row.put("__entity", e);
            rows.add(row);
        }

        // Compute each window column
        for (FieldDefinition fd : fields) {
            if (!(fd instanceof WindowFieldDefinition w)) {
                continue;
            }
            applyWindowFunction(rows, w);
        }

        // Remove backrefs
        for (Map<String, Object> existingRow : rows) {
            existingRow.remove("__entity");
        }
        return rows;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void applyWindowFunction(@NotNull List<Map<String, Object>> rows, @NotNull WindowFieldDefinition w) {
        // Partition rows
        int partitionBySize = w.partitionBy() == null ? 16 : w.partitionBy().size();
        Map<List<Object>, List<Map<String, Object>>> partitions = new LinkedHashMap<>(partitionBySize);
        List<Object> key = new ArrayList<>(partitionBySize);
        for (Map<String, Object> row : rows) {
            T e = (T) row.get("__entity");
            if (w.partitionBy() != null) {
                for (String p : w.partitionBy()) {
                    var fm = repositoryModel.fieldByName(p);
                    key.add(fm != null ? fm.getValue(e) : null);
                }
            }
            partitions.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }

        for (List<Map<String, Object>> part : partitions.values()) {
            // Sort partition according to window orderBy
            if (w.orderBy() != null && !w.orderBy().isEmpty()) {
                Comparator<Map<String, Object>> cmp = createComparatorFromWindowField(w);
                part.sort(cmp);
            }

            switch (w.functionType()) {
                case ROW_NUMBER -> {
                    for (int i = 0; i < part.size(); i++) {
                        part.get(i).put(w.alias(), i + 1L);
                    }
                }
                case RANK -> {
                    long rank = 1;
                    for (int i = 0; i < part.size(); i++) {
                        if (i > 0 && !sameOrdering(part.get(i - 1), part.get(i), w.orderBy())) {
                            rank = i + 1L;
                        }
                        part.get(i).put(w.alias(), rank);
                    }
                }
                case DENSE_RANK -> {
                    long rank = 1;
                    for (int i = 0; i < part.size(); i++) {
                        if (i > 0 && !sameOrdering(part.get(i - 1), part.get(i), w.orderBy())) {
                            rank++;
                        }
                        part.get(i).put(w.alias(), rank);
                    }
                }
                case COUNT -> {
                    long running = 0;
                    for (Map<String, Object> m : part) {
                        running++;
                        m.put(w.alias(), running);
                    }
                }
                case SUM, AVG, MIN, MAX -> {
                    double runningSum = 0d;
                    long runningCount = 0;
                    Comparable runningMin = null;
                    Comparable runningMax = null;
                    for (Map<String, Object> m : part) {
                        T e = (T) m.get("__entity");
                        var fm = repositoryModel.fieldByName(w.field());
                        Object v = fm != null ? fm.getValue(e) : null;
                        if (v instanceof Number n) {
                            runningSum += n.doubleValue();
                            runningCount++;
                        }
                        if (v instanceof Comparable c) {
                            runningMin = runningMin == null ? c : (runningMin.compareTo(c) <= 0 ? runningMin : c);
                            runningMax = runningMax == null ? c : (runningMax.compareTo(c) >= 0 ? runningMax : c);
                        }

                        Object out = switch (w.functionType()) {
                            case SUM -> runningCount == 0 ? null : runningSum;
                            case AVG -> runningCount == 0 ? null : (runningSum / runningCount);
                            case MIN -> runningMin;
                            case MAX -> runningMax;
                            default -> null;
                        };
                        m.put(w.alias(), out);
                    }
                }
                default -> throw new UnsupportedOperationException("Window function not supported in file adapter: " + w.functionType());
            }
        }
    }

    private @Nullable Comparator<Map<String, Object>> createComparatorFromWindowField(@NotNull WindowFieldDefinition w) {
        Comparator<Map<String, Object>> cmp = null;
        for (SortOption s : w.orderBy()) {
            Comparator<Map<String, Object>> next = Comparator.comparing(m -> {
                T e = (T) m.get("__entity");
                var fm = repositoryModel.fieldByName(s.field());
                return (Comparable) (fm != null ? fm.getValue(e) : null);
            }, Comparator.nullsFirst(Comparator.naturalOrder()));
            if (s.order() == SortOrder.DESCENDING) {
                next = next.reversed();
            }
            cmp = cmp == null ? next : cmp.thenComparing(next);
        }
        return cmp;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean sameOrdering(
        @NotNull Map<String, Object> a,
        @NotNull Map<String, Object> b,
        @Nullable List<SortOption> order
    ) {
        if (order == null || order.isEmpty()) {
            return true;
        }
        T ea = (T) a.get("__entity");
        T eb = (T) b.get("__entity");
        for (SortOption s : order) {
            var fm = repositoryModel.fieldByName(s.field());
            Object va = fm != null ? fm.getValue(ea) : null;
            Object vb = fm != null ? fm.getValue(eb) : null;
            if (!Objects.equals(va, vb)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean matchesJsonSelectOption(T entity, JsonSelectOption filter) {
        var jsonField = repositoryModel.fieldByName(filter.field());
        if (jsonField == null) {
            return false;
        }

        Object jsonValue = jsonField.getValue(entity);
        if (jsonValue == null) {
            return filter.value() == null;
        }

        JsonNode root = objectMapper.valueToTree(jsonValue);
        JsonNode selected = selectJsonPath(root, filter.jsonPath());
        if (selected == null || selected.isMissingNode() || selected.isNull()) {
            return filter.value() == null;
        }

        Object actual = objectMapper.convertValue(selected, Object.class);
        Object expected = filter.value();
        String operator = filter.operator();

        if (actual == null) {
            return expected == null;
        }

        return switch (operator) {
            case "=" -> actual.equals(expected);
            case "!=" -> !actual.equals(expected);
            case ">" -> actual instanceof Comparable comparable && expected != null && comparable.compareTo(expected) > 0;
            case "<" -> actual instanceof Comparable comparable && expected != null && comparable.compareTo(expected) < 0;
            case ">=" -> actual instanceof Comparable comparable && expected != null && comparable.compareTo(expected) >= 0;
            case "<=" -> actual instanceof Comparable comparable && expected != null && comparable.compareTo(expected) <= 0;
            case "IN" -> expected instanceof Collection<?> list && list.contains(actual);
            default -> false;
        };
    }

    private static @Nullable JsonNode selectJsonPath(@NotNull JsonNode root, @Nullable String jsonPath) {
        if (jsonPath == null || jsonPath.isBlank()) {
            return null;
        }

        // Supported: $.a.b.c
        if (!jsonPath.startsWith("$")) {
            return null;
        }

        String path = jsonPath;
        if (path.startsWith("$.")) {
            path = path.substring(2);
        } else if ("$".equals(path)) {
            return root;
        } else {
            return null;
        }

        JsonNode current = root;
        for (String part : path.split("\\.")) {
            if (part.isEmpty()) {
                return null;
            }
            current = current.get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    @Override
    public T findById(ID key) {
        try {
            return readEntity(key);
        } catch (IOException e) {
            throw new RuntimeException("Failed to find entity by ID: " + key, e);
        }
    }

    @Override
    public Map<ID, T> findAllById(Collection<ID> keys) {
        Map<ID, T> result = new HashMap<>(keys.size());
        for (ID id : keys) {
            try {
                T entity = readEntity(id);
                if (entity != null) {
                    result.put(id, entity);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to read entity: " + id, e);
            }
        }
        return result;
    }

    @Override
    public @NotNull CloseableIterator<T> findIterator(SelectQuery query) {
        Stream<T> stream = findStream(query);
        Iterator<T> iterator = stream.iterator();

        return new CloseableIterator<>() {
            @Override
            public boolean hasNext() { return iterator.hasNext(); }

            @Override
            public T next() { return iterator.next(); }

            @Override
            public void close() { stream.close(); }
        };
    }

    @Override
    public @NotNull Stream<T> findStream(SelectQuery query) {
        try {
            Stream<T> entityStream = getFileStream();

            if (query != null && query.filters() != null && !query.filters().isEmpty()) {
                entityStream = entityStream.filter(entity -> matchesAll(entity, query.filters()));
            }

            if (query != null && query.sortOptions() != null && !query.sortOptions().isEmpty()) {
                List<Comparator<T>> comparators = new ArrayList<>();
                for (SortOption sortOption : query.sortOptions()) {
                    Comparator<T> tComparator = compareBySortField(sortOption);
                    comparators.add(tComparator);
                }

                Comparator<T> combined = findCombinedComparator(comparators);

                entityStream = entityStream.sorted(combined);
            }

            // Apply limit lazily
            if (query != null && query.limit() >= 0) {
                entityStream = entityStream.limit(query.limit());
            }

            // nothing special to close here, Files.list() streams are already closed by map operations
            return entityStream;

        } catch (Exception e) {
            throw new RuntimeException("Failed to open file stream", e);
        }
    }

    private static <T> @NotNull Comparator<T> findCombinedComparator(List<Comparator<T>> comparators) {
        boolean seen = false;
        Comparator<T> acc = null;
        for (Comparator<T> comparator : comparators) {
            if (!seen) {
                seen = true;
                acc = comparator;
            } else {
                acc = acc.thenComparing(comparator);
            }
        }
        Comparator<T> combined = (seen ? acc : null);
        if (combined == null) {
            throw new IllegalArgumentException("Should not be null.");
        }
        return combined;
    }

    private @NotNull Stream<T> getFileStream() {
        Stream<Path> fileStream = Stream.iterate(0, i -> i + 1)
            .limit(sharding ? shardCount : 1)
            .map(i -> sharding ? basePath.resolve(String.valueOf(i)) : basePath)
            .filter(Files::exists)
            .flatMap(path -> {
                try {
                    return Files.list(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            })
            .filter(Files::isRegularFile)
            .filter(path -> path.getFileName().toString().endsWith(getFileExtension()));

        // Map paths to entities lazily
        return fileStream.map(path -> {
            try {
                return readEntity(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public @Nullable T first(SelectQuery query){
        List<T> results = find(query);
        return results.isEmpty() ? null : results.getFirst();
    }

    @Override
    public TransactionResult<Boolean> insert(T value, TransactionContext < FileContext > transactionContext){
        try {
            ID id = extractId(value);
            writeEntity(value, id);
            updateIndexes(value, id);
            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public TransactionResult<Boolean> insertAll(Collection<T> value, TransactionContext <FileContext> transactionContext){
        try {
            for (T entity : value) {
                ID id = extractId(entity);
                writeEntity(entity, id);
            }
            updateIndexesBatch(value);
            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public TransactionResult<Boolean> updateAll(T entity, TransactionContext<FileContext> transactionContext){
        try {
            ID id = extractId(entity);
            writeEntity(entity, id);
            updateIndexes(entity, id);
            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public TransactionResult<Boolean> delete(T entity, TransactionContext<FileContext> transactionContext){
        try {
            ID id = extractId(entity);
            deleteEntity(id);
            removeFromIndexes(id, entity);
            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public TransactionResult<Boolean> delete(T value){
        return delete(value, beginTransaction());
    }

    @Override
    public TransactionResult<Boolean> deleteById(ID entity, TransactionContext < FileContext > transactionContext){
        try {
            if (indexes.isEmpty()) { // If we didn't do this then we would need to read the entity even if we don't have any indexes that need it.
                deleteEntity(entity);
                return TransactionResult.success(true);
            }

            final T value = findById(entity);
            deleteEntity(entity);
            removeFromIndexes(entity, value);
            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public TransactionResult<Boolean> deleteById(ID value) {
        return deleteById(value, beginTransaction());
    }

    @Override
    public TransactionResult<Boolean> updateAll(
        @NotNull UpdateQuery query,
        TransactionContext<FileContext> transactionContext
    ) {
        try {
            List<T> all = readAll();
            List<T> updatedElements = new ArrayList<>(all.size());
            boolean updated = false;

            for (T entity : all) {
                if (!matchesAll(entity, query.filters())) {
                    continue;
                }

                applyUpdates(entity, query);
                ID id = extractId(entity);
                writeEntity(entity, id);
                updated = true;
                updatedElements.add(entity);
            }

            updateIndexesBatch(updatedElements);
            return TransactionResult.success(updated);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    private void removeFromIndexes(ID id, T entity) {
        indexes.values().forEach(index -> {
            try {
                Object value = repositoryModel
                    .fieldByName(index.field())
                    .getValue(entity);

                Set<ID> ids = index.map().get(value);
                if (ids != null) {
                    ids.remove(id);
                    if (ids.isEmpty()) {
                        index.map().remove(value);
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    private boolean matchesAll(T entity, List<FilterOption> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (FilterOption filter : filters) {
            if (!matches(entity, filter)) {
                return false;
            }
        }
        return true;
    }

    private void applyUpdates(T entity, UpdateQuery query) {
        Map<String, Object> updates = query.updates();
        updates.forEach((fieldName, newValue) -> {
            try {
                var field = repositoryModel.fieldByName(fieldName);
                if (field == null) {
                    throw new IllegalArgumentException("Unknown field: " + fieldName);
                }
                field.setValue(entity, newValue);
            } catch (Exception e) {
                throw new RuntimeException("Failed to apply update on field: " + fieldName, e);
            }
        });
    }

    @Override
    public TransactionResult<Boolean> updateAll(@NotNull UpdateQuery query){
        return updateAll(query, beginTransaction());
    }

    @Override
    public TransactionResult<Boolean> delete(
        @NotNull DeleteQuery query,
        TransactionContext<FileContext> tx
    ) {
        try {
            List<T> all = readAll();
            List<T> deletedElements = new ArrayList<>(all.size());
            boolean deleted = false;

            for (T entity : all) {
                if (!matchesAll(entity, query.filters())) {
                    continue;
                }
                ID id = extractId(entity);
                deleteEntity(id);
                deletedElements.add(entity);
                deleted = true;
            }

            removeFromIndexesBatch(deletedElements);
            return TransactionResult.success(deleted);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    private void updateIndexesBatch(Collection<T> entities) {
        if (entities.isEmpty() || indexes.isEmpty()) return;

        Map<Object, Set<ID>> additions = new HashMap<>();
        for (SecondaryIndex<ID> index : indexes.values()) {
            FieldModel<T> field = repositoryModel.fieldByName(index.field());

            updateCollectionFromEntities(entities, field, additions);

            for (Map.Entry<Object, Set<ID>> entry : additions.entrySet()) {
                index.map()
                    .computeIfAbsent(entry.getKey(), k -> ConcurrentHashMap.newKeySet())
                    .addAll(entry.getValue());
            }
        }
    }

    private void updateCollectionFromEntities(Collection<T> entities, FieldModel<T> field, Map<Object, Set<ID>> additions) {
        for (T entity : entities) {
            try {
                Object value = field.getValue(entity);
                ID id = extractId(entity);

                additions
                    .computeIfAbsent(value, k -> new HashSet<>())
                    .add(id);

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void removeFromIndexesBatch(Collection<T> entities) {
        if (entities.isEmpty() || indexes.isEmpty()) return;

        Map<Object, Set<ID>> removals = new HashMap<>(entities.size());
        for (SecondaryIndex<ID> index : indexes.values()) {
            var field = repositoryModel.fieldByName(index.field());

            // value -> IDs to remove

            updateCollectionFromEntities(entities, field, removals);

            for (Map.Entry<Object, Set<ID>> entry : removals.entrySet()) {
                Object value = entry.getKey();
                Set<ID> ids = entry.getValue();
                Set<ID> existing = index.map().get(value);
                if (existing == null) return;

                existing.removeAll(ids);

                if (existing.isEmpty()) {
                    index.map().remove(value);
                }
            }
        }
    }

    @Override
    public TransactionResult<Boolean> delete(@NotNull DeleteQuery query){
        return delete(query, beginTransaction());
    }

    @Override
    public TransactionResult<Boolean> insert(T value){
        return insert(value, beginTransaction());
    }

    @Override
    public TransactionResult<Boolean> updateAll(T entity){
        return updateAll(entity, beginTransaction());
    }

    @Override
    public TransactionResult<Boolean> insertAll(Collection < T > query) {
        return insertAll(query, beginTransaction());
    }

    @Override
    public TransactionResult<Boolean> clear() {
        try {
            cache.clear();
            if (sharding) {
                for (int i = 0; i < shardCount; i++) {
                    Path shardPath = basePath.resolve(String.valueOf(i));
                    deleteDirectory(shardPath);
                    Files.createDirectories(shardPath);
                }
            } else {
                deleteDirectory(basePath);
                Files.createDirectories(basePath);
            }
            indexes.clear();
            return TransactionResult.success(true);
        } catch (IOException e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public TransactionResult<Boolean> createIndex(IndexOptions index) {
        String field = index.indexName();

        if (indexes.containsKey(field)) {
            return TransactionResult.success(false);
        }

        try {
            SecondaryIndex<ID> idx = new SecondaryIndex<>(field, index.type() == IndexType.UNIQUE);

            for (T entity : readAll()) {
                Object value = repositoryModel.fieldByName(field).getValue(entity);
                ID id = extractId(entity);

                Map<Object, Set<ID>> map = idx.map();
                map.computeIfAbsent(value, k -> ConcurrentHashMap.newKeySet())
                    .add(id);
            }

            indexes.put(field, idx);
            persistIndex(idx);

            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    private void persistIndex(SecondaryIndex<ID> index) throws IOException {
        Files.createDirectories(indexRoot.getParent());

        try (OutputStream os = Files.newOutputStream(indexRoot)) {
            objectMapper.writeValue(os, index);
        }
    }

    private void updateIndexes(T entity, ID id) {
        if (indexes.isEmpty()) return;
        indexes.values().forEach(index -> {
            try {
                Object value = repositoryModel
                    .fieldByName(index.field())
                    .getValue(entity);

                Map<Object, Set<ID>> map = index.map();
                map.computeIfAbsent(value, k -> ConcurrentHashMap.newKeySet())
                    .add(id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    @NotNull
    public Class<T> getElementType() {
        return entityType;
    }

    // Helper methods
    public ID extractId(T entity) {
        try {
            return repositoryModel.getPrimaryKeyValue(entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract ID from entity", e);
        }
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var stream = Files.walk(directory)) {
                stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
            }
        }
    }
}
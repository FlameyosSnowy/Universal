package io.github.flameyossnowy.universal.microservices.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.flameyossnowy.universal.api.IndexOptions;
import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.RepositoryRegistry;
import io.github.flameyossnowy.universal.api.annotations.FileRepository;
import io.github.flameyossnowy.universal.api.annotations.enums.CompressionType;
import io.github.flameyossnowy.universal.api.annotations.enums.FileFormat;
import io.github.flameyossnowy.universal.api.cache.DatabaseSession;
import io.github.flameyossnowy.universal.api.cache.SessionOption;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.operation.Operation;
import io.github.flameyossnowy.universal.api.operation.OperationContext;
import io.github.flameyossnowy.universal.api.operation.OperationExecutor;
import io.github.flameyossnowy.universal.api.options.DeleteQuery;
import io.github.flameyossnowy.universal.api.options.SelectOption;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.options.SortOrder;
import io.github.flameyossnowy.universal.api.options.SortOption;
import io.github.flameyossnowy.universal.api.options.UpdateQuery;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.microservices.relationship.RelationshipResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
 * Supports various file formats (JSON, CSV, etc.) and compression options.
 *
 * @param <T> The entity type
 * @param <ID> The ID type
 */
public class FileRepositoryAdapter<T, ID> implements RepositoryAdapter<T, ID, FileContext> {
    private final Class<T> entityType;
    private final Class<ID> idType;
    private final RepositoryInformation repositoryInformation;
    private final TypeResolverRegistry resolverRegistry;
    private final OperationExecutor<FileContext> operationExecutor;
    private final OperationContext<FileContext> operationContext;
    private final ObjectMapper objectMapper;
    private final Path basePath;
    private final FileFormat format;
    private final boolean compressed;
    private final CompressionType compressionType;
    private final boolean sharding;
    private final int shardCount;
    private final RelationshipResolver relationshipResolver;


    private static final int STRIPE_COUNT = 64;
    private final ReentrantReadWriteLock[] stripes = new ReentrantReadWriteLock[STRIPE_COUNT];

    {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            stripes[i] = new ReentrantReadWriteLock();
        }
    }

    private ReentrantReadWriteLock getLockForId(ID id) {
        return stripes[(id.hashCode() & 0x7fffffff) % STRIPE_COUNT];
    }

    // In-memory cache for quick access
    private final Map<ID, T> cache = new ConcurrentHashMap<>(128);

    public FileRepositoryAdapter(
            @NotNull Class < T > entityType,
            @NotNull Class < ID > idType,
            @NotNull Path basePath,
            FileFormat format,
        boolean compressed,
        CompressionType compressionType,
        boolean sharding,
        int shardCount
    ) {
        this.entityType = entityType;
        this.idType = idType;
        this.basePath = basePath;
        this.format = format;
        this.compressed = compressed;
        this.compressionType = compressionType;
        this.sharding = sharding;
        this.shardCount = shardCount;

        this.repositoryInformation = RepositoryMetadata.getMetadata(entityType);
        if (repositoryInformation == null) {
            throw new IllegalArgumentException("Entity " + entityType.getName() + " must be annotated with @Repository");
        }
        this.resolverRegistry = new TypeResolverRegistry();

        // Configure Jackson ObjectMapper
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        this.operationExecutor = new FileOperationExecutor<>(this);
        this.operationContext = new OperationContext<>(
            repositoryInformation,
            resolverRegistry,
            operationExecutor
        );

        this.relationshipResolver = new RelationshipResolver();
        RepositoryRegistry.register(repositoryInformation.getRepositoryName(), this);

        // Create base directory if it doesn't exist
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create base directory: " + basePath, e);
        }
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
            annotation.shardCount()
        );
    }

    @Override
    @NotNull
    public <R> TransactionResult <R> execute(
        @NotNull Operation< R, FileContext > operation,
        @NotNull TransactionContext < FileContext > transactionContext) {
        return operation.executeWithTransaction(operationContext, transactionContext);
    }

    @Override
    @NotNull
    public OperationContext<FileContext> getOperationContext () {
        return operationContext;
    }

    @Override
    @NotNull
    public OperationExecutor<FileContext> getOperationExecutor () {
        return operationExecutor;
    }

    @Override
    @NotNull
    public RepositoryInformation getRepositoryInformation () {
        return repositoryInformation;
    }

    @Override
    @NotNull
    public TypeResolverRegistry getTypeResolverRegistry () {
        return resolverRegistry;
    }

    @Override
    @NotNull
    public Class<T> getEntityType () {
        return entityType;
    }

    @Override
    @NotNull
    public Class<ID> getIdType () {
        return idType;
    }

    @Override
    @NotNull
    public TransactionContext<FileContext> beginTransaction () {
        return new FileTransactionContext();
    }

    @Override
    public @NotNull List<ID> findIds(SelectQuery query) {
        return List.of();
    }

    @Override
    public void close () {
        cache.clear();
    }

    // File operations

    public Path getEntityPath (ID id){
        String fileName = id.toString();
        String extension = getFileExtension();

        if (sharding) {
            int shard = Math.abs(id.hashCode() % shardCount);
            return basePath.resolve(String.valueOf(shard)).resolve(fileName + extension);
        }

        return basePath.resolve(fileName + extension);
    }

    private String getFileExtension () {
        String ext = switch (format) {
            case JSON -> ".json";
            case XML -> ".xml";
            case CSV -> ".csv";
            case YAML -> ".yaml";
            case BINARY -> ".bin";
            case CUSTOM -> ".dat";
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

    public void writeEntity (T entity, ID id) throws IOException {
        ReentrantReadWriteLock idLock = getLockForId(id);
        idLock.writeLock().lock();
        try {
            Path path = getEntityPath(id);
            Files.createDirectories(path.getParent());

            try (OutputStream os = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                OutputStream output = compressed ? wrapCompression(os) : os;

                switch (format) {
                    case JSON -> objectMapper.writeValue(output, entity);
                    case XML, CSV, YAML, BINARY, CUSTOM ->
                        throw new UnsupportedOperationException("Format " + format + " not yet implemented");
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

    public @Nullable T readEntity (ID id) throws IOException {
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

                T entity = switch (format) {
                    case JSON -> objectMapper.readValue(input, entityType);
                    case XML, CSV, YAML, BINARY, CUSTOM ->
                        throw new UnsupportedOperationException("Format " + format + " not yet implemented");
                };

                relationshipResolver.resolve(entity, repositoryInformation);
                cache.put(id, entity);
                return entity;
            }
        } finally {
            idLock.readLock().unlock();
        }
    }

    public void deleteEntity (ID id) throws IOException {
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

    public List<T> readAll () throws IOException {
        List<T> results = new ArrayList<>();
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

    private List<T> readFromDirectory (Path directory) throws IOException {
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
                        T entity = objectMapper.readValue(input, entityType);
                        relationshipResolver.resolve(entity, repositoryInformation);
                        return entity;
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .collect(Collectors.toList());
        }
    }

    private OutputStream wrapCompression (OutputStream os) throws IOException {
        return switch (compressionType) {
            case GZIP -> new GZIPOutputStream(os);
            case ZIP, BZIP2, LZ4, ZSTD ->
                throw new UnsupportedOperationException("Compression type " + compressionType + " not yet implemented");
        };
    }

    private InputStream unwrapCompression (InputStream is) throws IOException {
        return switch (compressionType) {
            case GZIP -> new GZIPInputStream(is);
            case ZIP, BZIP2, LZ4, ZSTD ->
                throw new UnsupportedOperationException("Compression type " + compressionType + " not yet implemented");
        };
    }

    public Path getBasePath () {
        return basePath;
    }

    // RepositoryAdapter interface implementation

    @Override
    public TransactionResult<Boolean> createRepository ( boolean ifNotExists){
        try {
            if (ifNotExists && Files.exists(basePath)) {
                return TransactionResult.success(true);
            }
            Files.createDirectories(basePath);
            if (sharding) {
                for (int i = 0; i < shardCount; i++) {
                    Files.createDirectories(basePath.resolve(String.valueOf(i)));
                }
            }
            return TransactionResult.success(true);
        } catch (IOException e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public DatabaseSession<ID, T, FileContext> createSession () {
        return createSession(EnumSet.noneOf(SessionOption.class));
    }

    @Override
    public DatabaseSession<ID, T, FileContext> createSession (EnumSet < SessionOption > options) {
        return new FileSession<>(this, options);
    }

    @Override
    public List<T> find (SelectQuery query){
        try {
            List<T> all = readAll();
            if (query == null) {
                return all;
            }

            Stream<T> stream = all.stream();

            // Apply filters
            if (query.filters() != null && !query.filters().isEmpty()) {
                for (SelectOption filter : query.filters()) {
                    stream = stream.filter(entity -> matches(entity, filter));
                }
            }

            // Apply sorting
            if (query.sortOptions() != null && !query.sortOptions().isEmpty()) {
                Comparator<T> comparator = null;
                for (SortOption sortOption : query.sortOptions()) {
                    Comparator<T> currentComparator = Comparator.comparing(entity -> {
                        try {
                            return (Comparable) repositoryInformation.getField(sortOption.field()).getValue(entity);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to get sort field value", e);
                        }
                    });
                    if (sortOption.order() == SortOrder.DESCENDING) {
                        currentComparator = currentComparator.reversed();
                    }
                    if (comparator == null) {
                        comparator = currentComparator;
                    } else {
                        comparator = comparator.thenComparing(currentComparator);
                    }
                }
                if (comparator != null) {
                    stream = stream.sorted(comparator);
                }
            }

            // Apply limit
            if (query.limit() >= 0) {
                stream = stream.limit(query.limit());
            }

            return stream.collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Failed to find entities", e);
        }
    }

    private boolean matches (T entity, SelectOption filter){
        try {
            var field = repositoryInformation.getField(filter.option());
            if (field == null) {
                return false; // Or throw an exception
            }
            Object value = field.getValue(entity);
            Object filterValue = filter.value();
            String operator = filter.operator();

            if (value == null) {
                return filterValue == null;
            }

            switch (operator) {
                case "=":
                    return value.equals(filterValue);
                case "!=":
                    return !value.equals(filterValue);
                case ">":
                    if (value instanceof Comparable) {
                        return ((Comparable) value).compareTo(filterValue) > 0;
                    }
                    return false;
                case "<":
                    if (value instanceof Comparable) {
                        return ((Comparable) value).compareTo(filterValue) < 0;
                    }
                    return false;
                case ">=":
                    if (value instanceof Comparable) {
                        return ((Comparable) value).compareTo(filterValue) >= 0;
                    }
                    return false;
                case "<=":
                    if (value instanceof Comparable) {
                        return ((Comparable) value).compareTo(filterValue) <= 0;
                    }
                    return false;
                case "IN":
                    if (filterValue instanceof Collection) {
                        return ((Collection<?>) filterValue).contains(value);
                    }
                    return false;
                default:
                    return false;
            }
        } catch (Exception e) {
            // Log error or handle it as per application's needs
            return false;
        }
    }

    @Override
    public List<T> find () {
        return find(null);
    }

    @Override
    public T findById (ID key){
        try {
            return readEntity(key);
        } catch (IOException e) {
            throw new RuntimeException("Failed to find entity by ID: " + key, e);
        }
    }

    @Override
    public Map<ID, T> findAllById (Collection < ID > keys) {
        return Map.of();
    }

    @Override
    public @Nullable T first (SelectQuery query){
        List<T> results = find(query);
        return results.isEmpty() ? null : results.getFirst();
    }

    @Override
    public TransactionResult<Boolean> insert (T value, TransactionContext < FileContext > transactionContext){
        try {
            ID id = extractId(value);
            writeEntity(value, id);
            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public TransactionResult<Boolean> insertAll
    (Collection < T > value, TransactionContext < FileContext > transactionContext){
        try {
            for (T entity : value) {
                ID id = extractId(entity);
                writeEntity(entity, id);
            }
            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public TransactionResult<Boolean> updateAll (T entity, TransactionContext < FileContext > transactionContext){
        try {
            ID id = extractId(entity);
            writeEntity(entity, id);
            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public TransactionResult<Boolean> delete (T entity, TransactionContext < FileContext > transactionContext){
        try {
            ID id = extractId(entity);
            deleteEntity(id);
            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public TransactionResult<Boolean> delete (T value){
        return delete(value, beginTransaction());
    }

    @Override
    public TransactionResult<Boolean> deleteById (ID entity, TransactionContext < FileContext > transactionContext){
        try {
            deleteEntity(entity);
            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public TransactionResult<Boolean> deleteById(ID value){
        return deleteById(value, beginTransaction());
    }

    @Override
    public TransactionResult<Boolean> updateAll(@NotNull UpdateQuery query, TransactionContext<FileContext> transactionContext) {
        return null;
    }

    @Override
    public TransactionResult<Boolean> updateAll (@NotNull UpdateQuery query){
        return updateAll(query, beginTransaction());
    }

    @Override
    public TransactionResult<Boolean> delete (DeleteQuery query, TransactionContext < FileContext > tx){
        // TODO: Implement query-based deletes
        return TransactionResult.failure(new UnsupportedOperationException("Query-based deletes not yet implemented for file repositories"));
    }

    @Override
    public TransactionResult<Boolean> delete (@NotNull DeleteQuery query){
        return delete(query, beginTransaction());
    }

    @Override
    public TransactionResult<Boolean> insert (T value){
        return insert(value, beginTransaction());
    }

    @Override
    public TransactionResult<Boolean> updateAll (T entity){
        return updateAll(entity, beginTransaction());
    }

    @Override
    public TransactionResult<Boolean> insertAll (Collection < T > query) {
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
            return TransactionResult.success(true);
        } catch (IOException e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public TransactionResult<Boolean> createIndex (IndexOptions index){
        return TransactionResult.success(false);
    }

    @Override
    @NotNull
    public Class<T> getElementType() {
        return entityType;
    }

    // Helper methods
    public ID extractId(T entity) {
        try {
            var idField = repositoryInformation.getPrimaryKey();
            if (idField == null) {
                throw new IllegalArgumentException("Cannot extract ID from " + repositoryInformation.getRepositoryName() + " because there's no id.");
            }
            return idField.getValue(entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract ID from entity", e);
        }
    }

    private static void deleteDirectory (Path directory) throws IOException {
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
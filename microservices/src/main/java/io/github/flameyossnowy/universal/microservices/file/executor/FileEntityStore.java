package io.github.flameyossnowy.universal.microservices.file.executor;

import io.github.flameyossnowy.universal.api.annotations.enums.CompressionType;
import io.github.flameyossnowy.universal.api.annotations.enums.FileFormat;
import io.github.flameyossnowy.universal.api.factory.ObjectModel;
import io.github.flameyossnowy.universal.api.factory.RelationshipLoader;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.microservices.MicroservicesJsonCodecBridge;
import io.github.flameyossnowy.universal.microservices.relationship.RelationshipResolver;
import me.flame.uniform.json.JsonAdapter;
import me.flame.uniform.json.writers.JsonWriterOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Handles all raw file I/O for entity persistence: reading, writing, deleting,
 * and listing entity files. Knows nothing about queries, filters, or indexes.
 *
 * <p>When constructed with {@code parallelReads = true}, multi-shard {@link #readAll()}
 * calls submit each shard to a shared {@link ForkJoinPool} and join the results,
 * rather than deserializing shards sequentially. Single-directory reads are always
 * sequential (parallelism across files in one directory would create more overhead
 * than it saves for typical shard sizes).
 *
 * <p>All stream I/O is wrapped in {@link BufferedInputStream} /
 * {@link BufferedOutputStream} to batch syscalls. File metadata is read via
 * {@link BasicFileAttributes} so each directory entry requires only one {@code stat()}
 * call instead of two. The file extension string is computed once at construction
 * and cached.
 */
public class FileEntityStore<T, ID> {

    private static final int STRIPE_COUNT  = 64;
    private static final int BUFFER_SIZE   = 8192;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final Class<T>                    entityType;
    private final RepositoryModel<T, ID>      repositoryModel;
    private final TypeResolverRegistry        resolverRegistry;
    private final JsonAdapter objectMapper;
    private final ObjectModel<T, ID>          objectModel;
    private final RelationshipLoader<T, ID>   relationshipLoader;
    private final RelationshipResolver<T, ID> relationshipResolver;

    private final Path            basePath;
    private final FileFormat      format;
    private final boolean         compressed;
    private final CompressionType compressionType;
    private final boolean         sharding;
    private final int             shardCount;
    private final boolean         parallelReads;

    /** Cached once at construction - never changes. */
    private final String fileExtension;

    private final Map<ID, T>                cache   = new ConcurrentHashMap<>(128);
    private final ReentrantReadWriteLock[]  stripes = new ReentrantReadWriteLock[STRIPE_COUNT];

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public FileEntityStore(
        @NotNull Class<T>                    entityType,
        @NotNull RepositoryModel<T, ID>      repositoryModel,
        @NotNull TypeResolverRegistry        resolverRegistry,
        @NotNull JsonAdapter                objectMapper,
        @NotNull ObjectModel<T, ID>          objectModel,
        @NotNull RelationshipLoader<T, ID>   relationshipLoader,
        @NotNull RelationshipResolver<T, ID> relationshipResolver,
        @NotNull Path                        basePath,
        @NotNull FileFormat                  format,
        boolean                              compressed,
        @NotNull CompressionType             compressionType,
        boolean                              sharding,
        int                                  shardCount,
        boolean                              parallelReads
    ) {
        this.entityType          = entityType;
        this.repositoryModel     = repositoryModel;
        this.resolverRegistry    = resolverRegistry;
        this.objectMapper        = objectMapper;
        this.objectModel         = objectModel;
        this.relationshipLoader  = relationshipLoader;
        this.relationshipResolver = relationshipResolver;
        this.basePath            = basePath;
        this.format              = format;
        this.compressed          = compressed;
        this.compressionType     = compressionType;
        this.sharding            = sharding;
        this.shardCount          = shardCount;
        this.parallelReads       = parallelReads;
        this.fileExtension       = buildFileExtension(format, compressed, compressionType);

        for (int i = 0; i < STRIPE_COUNT; i++) {
            stripes[i] = new ReentrantReadWriteLock();
        }
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    public void write(T entity, ID id) throws IOException {
        ReentrantReadWriteLock lock = lockForId(id);
        lock.writeLock().lock();
        try {
            Path path = entityPath(id);
            Files.createDirectories(path.getParent());

            try (OutputStream raw = Files.newOutputStream(
                path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                 OutputStream buffered = new BufferedOutputStream(raw, BUFFER_SIZE)) {

                OutputStream output = compressed ? wrapCompression(buffered) : buffered;

                if (Objects.requireNonNull(format) == FileFormat.JSON) {
                    objectMapper.createWriter(JsonWriterOptions.ASYNC_WRITES).write(
                        objectMapper.writeValue(MicroservicesJsonCodecBridge.toStorageJson(objectMapper, resolverRegistry, repositoryModel, entity)),
                        path
                    );
                }

                // Flush/close the compression wrapper before the buffered stream closes,
                // so the compression trailer is written inside the buffer and flushed atomically.
                if (compressed) {
                    output.close();
                }
            }

            cache.put(id, entity);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Read by ID
    // -------------------------------------------------------------------------

    public @Nullable T read(ID id) throws IOException {
        T cached = cache.get(id);
        if (cached != null) return cached;

        ReentrantReadWriteLock lock = lockForId(id);
        lock.readLock().lock();
        try {
            // Re-check after acquiring lock - another thread may have populated the cache.
            T rechecked = cache.get(id);
            if (rechecked != null) return rechecked;

            Path path = entityPath(id);

            // Use readAttributes for a single stat() rather than Files.exists + open.
            BasicFileAttributes attrs;
            try {
                attrs = Files.readAttributes(path, BasicFileAttributes.class);
            } catch (NoSuchFileException e) {
                return null;
            }
            if (!attrs.isRegularFile()) return null;

            try (InputStream raw      = Files.newInputStream(path);
                 InputStream buffered = new BufferedInputStream(raw, BUFFER_SIZE)) {

                T entity = deserialize(compressed ? unwrapCompression(buffered) : buffered);
                relationshipResolver.resolve(entity, repositoryModel);
                cache.put(id, entity);
                return entity;
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Read from path (used by directory scans in FileQueryExecutor)
    // -------------------------------------------------------------------------

    /**
     * Reads and deserializes an entity directly from a {@link Path}.
     * Called during directory scans where the ID is not known in advance.
     * Does NOT consult or populate the cache - scan results are short-lived
     * and caching every scanned entity would thrash the cache on large reads.
     */
    public T readFromPath(Path path) throws IOException {
        try (InputStream raw      = Files.newInputStream(path);
             InputStream buffered = new BufferedInputStream(raw, BUFFER_SIZE)) {

            InputStream input = compressed ? unwrapCompression(buffered) : buffered;
            T result = deserialize(input);
            objectModel.populateRelationships(result, objectModel.getId(result), relationshipLoader);
            return result;
        }
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    public void delete(ID id) throws IOException {
        ReentrantReadWriteLock lock = lockForId(id);
        lock.writeLock().lock();
        try {
            Files.deleteIfExists(entityPath(id));
            cache.remove(id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Read all
    // -------------------------------------------------------------------------

    /**
     * Reads every entity. When {@code parallelReads} is enabled and sharding is
     * active, each shard directory is submitted to {@link ForkJoinPool#commonPool()}
     * as an independent task; results are joined and merged after all tasks complete.
     *
     * <p>Single-directory (non-sharded) reads are always sequential - the overhead
     * of task submission outweighs the gain for a single directory.
     */
    public List<T> readAll() throws IOException {
        if (!sharding) {
            return readFromDirectory(basePath);
        }

        if (parallelReads) {
            return readAllShardsParallel();
        } else {
            return readAllShardsSequential();
        }
    }

    private List<T> readAllShardsSequential() throws IOException {
        List<T> results = new ArrayList<>(32);
        for (int i = 0; i < shardCount; i++) {
            Path shardPath = basePath.resolve(String.valueOf(i));
            if (Files.exists(shardPath)) {
                results.addAll(readFromDirectory(shardPath));
            }
        }
        return results;
    }

    private List<T> readAllShardsParallel() throws IOException {
        // One task per shard; use the common pool so we don't create threads ourselves.
        @SuppressWarnings("unchecked")
        ForkJoinTask<List<T>>[] tasks = new ForkJoinTask[shardCount];

        for (int i = 0; i < shardCount; i++) {
            final Path shardPath = basePath.resolve(String.valueOf(i));
            tasks[i] = ForkJoinPool.commonPool().submit(() -> {
                if (!Files.exists(shardPath)) return List.of();
                return readFromDirectory(shardPath);
            });
        }

        // Collect - propagate the first IOException if any task failed.
        List<T> results = new ArrayList<>(shardCount * 16);
        for (ForkJoinTask<List<T>> task : tasks) {
            try {
                results.addAll(task.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while reading shards", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException io) throw io;
                throw new IOException("Failed to read shard", cause);
            }
        }
        return results;
    }

    // -------------------------------------------------------------------------
    // Count
    // -------------------------------------------------------------------------

    public long countFiles() throws IOException {
        if (!sharding) {
            return countFilesInDirectory(basePath);
        }

        long count = 0L;
        for (int i = 0; i < shardCount; i++) {
            count += countFilesInDirectory(basePath.resolve(String.valueOf(i)));
        }
        return count;
    }

    /**
     * Counts entity files in {@code directory} using a single {@code stat()} per
     * entry via {@link BasicFileAttributes}, and uses a glob filter so the OS
     * pre-filters by extension before Java sees the paths.
     */
    public long countFilesInDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return 0L;

        long count = 0L;

        // The glob pre-filters at the OS level; we still validate isRegularFile
        // via BasicFileAttributes to skip symlinks etc., all in one stat() call.
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(directory, "*" + fileExtension)) {
            for (Path path : ds) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                    if (attrs.isRegularFile()) count++;
                } catch (NoSuchFileException ignored) {
                    // File disappeared between listing and stat - safe to skip.
                }
            }
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // Cache management
    // -------------------------------------------------------------------------

    public void clearCache() {
        cache.clear();
    }

    public void invalidate(ID id) {
        cache.remove(id);
    }

    // -------------------------------------------------------------------------
    // Path helpers (package-visible for FileQueryExecutor)
    // -------------------------------------------------------------------------

    public Path entityPath(@NotNull ID id) {
        String fileName = id + fileExtension;
        if (sharding) {
            int shard = Math.abs(id.hashCode() % shardCount);
            return basePath.resolve(String.valueOf(shard)).resolve(fileName);
        }
        return basePath.resolve(fileName);
    }

    public Path    basePath()        { return basePath; }
    public boolean isSharding()      { return sharding; }
    public int     shardCount()      { return shardCount; }
    public String  fileExtension()   { return fileExtension; }
    public boolean isParallelReads() { return parallelReads; }

    // -------------------------------------------------------------------------
    // Static utility
    // -------------------------------------------------------------------------

    public static void deleteDirectoryRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
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

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<T> readFromDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return List.of();

        List<T> results = new ArrayList<>(32);

        // Use BasicFileAttributes glob - one stat() per entry, OS-level extension filter.
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(directory, "*" + fileExtension)) {
            for (Path path : ds) {
                BasicFileAttributes attrs;
                try {
                    attrs = Files.readAttributes(path, BasicFileAttributes.class);
                } catch (NoSuchFileException ignored) {
                    continue;
                }
                if (!attrs.isRegularFile()) continue;

                try (InputStream raw      = Files.newInputStream(path);
                     InputStream buffered = new BufferedInputStream(raw, BUFFER_SIZE)) {

                    T entity = deserialize(compressed ? unwrapCompression(buffered) : buffered);
                    relationshipResolver.resolve(entity, repositoryModel);
                    results.add(entity);
                }
            }
        }

        return results;
    }

    private T deserialize(InputStream input) throws IOException {
        var storedNode = objectMapper.readValue(input.readAllBytes());
        return MicroservicesJsonCodecBridge.readEntityFromStorageJson(
            objectMapper, resolverRegistry, repositoryModel, entityType, storedNode
        );
    }

    private ReentrantReadWriteLock lockForId(ID id) {
        return stripes[(id.hashCode() & Integer.MAX_VALUE) % STRIPE_COUNT];
    }

    private OutputStream wrapCompression(OutputStream os) throws IOException {
        return switch (compressionType) {
            case GZIP -> new GZIPOutputStream(os);
            case ZIP, BZIP2, LZ4, ZSTD ->
                throw new UnsupportedOperationException(
                    "Compression type " + compressionType + " not yet implemented");
        };
    }

    private InputStream unwrapCompression(InputStream is) throws IOException {
        return switch (compressionType) {
            case GZIP -> new GZIPInputStream(is);
            case ZIP, BZIP2, LZ4, ZSTD ->
                throw new UnsupportedOperationException(
                    "Compression type " + compressionType + " not yet implemented");
        };
    }

    /**
     * Computes the file extension string once. Called only from the constructor
     * so the result can be stored in a {@code final} field.
     */
    private static String buildFileExtension(
        FileFormat format,
        boolean compressed,
        CompressionType compressionType
    ) {
        String ext = switch (format) {
            case JSON -> ".json";
        };
        if (compressed) {
            ext += switch (compressionType) {
                case GZIP  -> ".gz";
                case ZIP   -> ".zip";
                case BZIP2 -> ".bz2";
                case LZ4   -> ".lz4";
                case ZSTD  -> ".zst";
            };
        }
        return ext;
    }
}
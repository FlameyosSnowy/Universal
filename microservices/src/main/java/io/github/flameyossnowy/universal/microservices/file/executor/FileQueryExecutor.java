package io.github.flameyossnowy.universal.microservices.file.executor;

import io.github.flameyossnowy.universal.api.CloseableIterator;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static io.github.flameyossnowy.universal.microservices.file.executor.FileEntityStore.collectTask;

/**
 * Executes read queries (find, count, findIds, stream) against the file store.
 * Delegates I/O to {@link FileEntityStore} and predicate evaluation to
 * {@link FileFilterEngine}. Owns sorting and limit enforcement.
 *
 * <p>When the underlying {@link FileEntityStore} is configured for parallel reads
 * and sharding is active, {@link #find(SelectQuery)} and {@link #count(SelectQuery)}
 * scan shards concurrently via {@link ForkJoinPool#commonPool()}. Limit-based early
 * termination is best-effort under parallel execution - results may slightly exceed
 * the limit before being trimmed, which is consistent with the semantics of unordered
 * parallel scans.
 */
public class FileQueryExecutor<T, ID> {

    private final FileEntityStore<T, ID>    store;
    private final FileFilterEngine<T, ID>   filterEngine;
    private final RepositoryModel<T, ID>    repositoryModel;

    public FileQueryExecutor(
        @NotNull FileEntityStore<T, ID>  store,
        @NotNull FileFilterEngine<T, ID> filterEngine,
        @NotNull RepositoryModel<T, ID>  repositoryModel
    ) {
        this.store           = store;
        this.filterEngine    = filterEngine;
        this.repositoryModel = repositoryModel;
    }

    // -------------------------------------------------------------------------
    // find
    // -------------------------------------------------------------------------

    public List<T> findAll() throws IOException {
        return store.readAll();
    }

    public List<T> find(@NotNull SelectQuery query) throws IOException {
        if (store.isSharding() && store.isParallelReads()) {
            return findParallel(query);
        }
        return findSequential(query);
    }

    private List<T> findSequential(@NotNull SelectQuery query) throws IOException {
        int expectedSize = query.limit() > 0 ? query.limit() : 16;
        List<T> results  = new ArrayList<>(expectedSize);

        if (store.isSharding()) {
            for (int i = 0; i < store.shardCount(); i++) {
                Path shardPath = store.basePath().resolve(String.valueOf(i));
                if (!Files.exists(shardPath)) continue;

                scanDirectory(shardPath, query, results);
                if (query.limit() >= 0 && results.size() >= query.limit()) break;
            }
        } else {
            scanDirectory(store.basePath(), query, results);
        }

        return finalize(results, query);
    }

    private List<T> findParallel(@NotNull SelectQuery query) throws IOException {
        @SuppressWarnings("unchecked")
        CompletableFuture<List<T>>[] tasks = new CompletableFuture[store.shardCount()];

        for (int i = 0; i < store.shardCount(); i++) {
            final Path shardPath = store.basePath().resolve(String.valueOf(i));
            tasks[i] = CompletableFuture.supplyAsync(() -> {
                if (!Files.exists(shardPath)) return List.of();
                List<T> partial = new ArrayList<>(16);
                try {
                    scanDirectory(shardPath, query, partial);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return partial;
            });
        }

        List<T> results = collectTasks(tasks);
        return finalize(results, query);
    }

    /** Applies sorting and hard limit trim after all results are gathered. */
    private List<T> finalize(List<T> results, @NotNull SelectQuery query) {
        applySorting(results, query.sortOptions());
        if (query.limit() >= 0 && results.size() > query.limit()) {
            return results.subList(0, query.limit());
        }
        return results;
    }

    // -------------------------------------------------------------------------
    // count
    // -------------------------------------------------------------------------

    public long countAll() throws IOException {
        return store.countFiles();
    }

    public long count(@NotNull SelectQuery query) throws IOException {
        if (query.limit() == 0) return 0L;

        if (store.isSharding() && store.isParallelReads()) {
            return countParallel(query);
        }
        return countSequential(query);
    }

    private long countSequential(@NotNull SelectQuery query) throws IOException {
        long count = 0L;
        if (store.isSharding()) {
            for (int i = 0; i < store.shardCount(); i++) {
                Path shardPath = store.basePath().resolve(String.valueOf(i));
                if (!Files.exists(shardPath)) continue;
                count += countMatches(shardPath, query);
            }
        } else {
            count = countMatches(store.basePath(), query);
        }
        return count;
    }

    private long countParallel(@NotNull SelectQuery query) throws IOException {
        @SuppressWarnings("unchecked")
        ForkJoinTask<Long>[] tasks = new ForkJoinTask[store.shardCount()];

        for (int i = 0; i < store.shardCount(); i++) {
            final Path shardPath = store.basePath().resolve(String.valueOf(i));
            tasks[i] = ForkJoinPool.commonPool().submit(() -> {
                if (!Files.exists(shardPath)) return 0L;
                return countMatches(shardPath, query);
            });
        }

        long total = 0L;
        for (ForkJoinTask<Long> task : tasks) {
            try {
                total += task.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while counting shards", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException io) throw io;
                throw new IOException("Failed to count shard", cause);
            }
        }
        return total;
    }

    // -------------------------------------------------------------------------
    // findIds
    // -------------------------------------------------------------------------

    public List<ID> findAllIds() throws IOException {
        List<ID> ids = new ArrayList<>(32);
        if (store.isSharding()) {
            for (int i = 0; i < store.shardCount(); i++) {
                scanDirectoryForIds(store.basePath().resolve(String.valueOf(i)), null, ids);
            }
        } else {
            scanDirectoryForIds(store.basePath(), null, ids);
        }
        return ids;
    }

    public List<ID> findIds(@NotNull SelectQuery query) throws IOException {
        if (query.limit() == 0) return List.of();

        int expectedSize = query.limit() > 0 ? query.limit() : 16;
        List<ID> ids = new ArrayList<>(expectedSize);

        if (store.isSharding()) {
            for (int i = 0; i < store.shardCount(); i++) {
                Path shardPath = store.basePath().resolve(String.valueOf(i));
                if (!Files.exists(shardPath)) continue;

                scanDirectoryForIds(shardPath, query, ids);
                if (query.limit() >= 0 && ids.size() >= query.limit()) break;
            }
        } else {
            scanDirectoryForIds(store.basePath(), query, ids);
        }
        return ids;
    }

    // -------------------------------------------------------------------------
    // stream
    // -------------------------------------------------------------------------

    public @NotNull Stream<T> stream(@Nullable SelectQuery query) {
        Stream<T> entityStream = buildFileStream();

        if (query != null && query.filters() != null && !query.filters().isEmpty()) {
            entityStream = entityStream.filter(e -> filterEngine.matchesAll(e, query.filters()));
        }

        if (query != null && query.sortOptions() != null && !query.sortOptions().isEmpty()) {
            entityStream = entityStream.sorted(buildComparator(query.sortOptions()));
        }

        if (query != null && query.limit() >= 0) {
            entityStream = entityStream.limit(query.limit());
        }

        return entityStream;
    }

    public @NotNull CloseableIterator<T> iterator(@Nullable SelectQuery query) {
        Stream<T> s  = stream(query);
        Iterator<T> it = s.iterator();
        return new CloseableIterator<>() {
            @Override public boolean hasNext() { return it.hasNext(); }
            @Override public T next()          { return it.next(); }
            @Override public void close()      { s.close(); }
        };
    }

    // -------------------------------------------------------------------------
    // Sorting helpers (package-visible for FileAggregationEngine reuse)
    // -------------------------------------------------------------------------

    public void applySorting(List<T> results, @Nullable List<SortOption> sortOptions) {
        if (sortOptions == null || sortOptions.isEmpty()) return;
        results.sort(buildComparator(sortOptions));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public @NotNull Comparator<T> buildComparator(@NotNull List<SortOption> sortOptions) {
        Comparator<T> acc = null;
        for (SortOption option : sortOptions) {
            Comparator<T> next = Comparator.comparing(entity -> {
                try {
                    return (Comparable) repositoryModel
                        .fieldByName(option.field())
                        .getValue(entity);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to get sort field value", e);
                }
            });
            if (option.order() == SortOrder.DESCENDING) next = next.reversed();
            acc = (acc == null) ? next : acc.thenComparing(next);
        }
        if (acc == null) throw new IllegalArgumentException("sortOptions must not be empty");
        return acc;
    }

    // -------------------------------------------------------------------------
    // Private – directory scanning
    // -------------------------------------------------------------------------

    private void scanDirectory(
        Path directory,
        SelectQuery query,
        List<T> results
    ) throws IOException {
        if (!Files.exists(directory)) return;

        String ext = store.fileExtension();

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(directory, "*" + ext)) {
            for (Path path : ds) {
                // One stat() per entry covers isRegularFile; also guards against
                // the file disappearing between listing and open.
                BasicFileAttributes attrs;
                try {
                    attrs = Files.readAttributes(path, BasicFileAttributes.class);
                } catch (NoSuchFileException ignored) {
                    continue;
                }
                if (!attrs.isRegularFile()) continue;

                T entity = store.readFromPath(path);
                if (!filterEngine.matchesAll(entity, query.filters())) continue;

                results.add(entity);
                if (query.limit() >= 0 && results.size() >= query.limit()) return;
            }
        }
    }

    private long countMatches(Path directory, SelectQuery query) throws IOException {
        if (!Files.exists(directory)) return 0L;

        long count = 0L;
        String ext = store.fileExtension();

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(directory, "*" + ext)) {
            for (Path path : ds) {
                BasicFileAttributes attrs;
                try {
                    attrs = Files.readAttributes(path, BasicFileAttributes.class);
                } catch (NoSuchFileException ignored) {
                    continue;
                }
                if (!attrs.isRegularFile()) continue;

                T entity = store.readFromPath(path);
                if (filterEngine.matchesAll(entity, query.filters())) count++;
            }
        }
        return count;
    }

    private void scanDirectoryForIds(
        Path directory,
        @Nullable SelectQuery query,
        List<ID> ids
    ) throws IOException {
        if (!Files.exists(directory)) return;

        String ext = store.fileExtension();

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(directory, "*" + ext)) {
            for (Path path : ds) {
                BasicFileAttributes attrs;
                try {
                    attrs = Files.readAttributes(path, BasicFileAttributes.class);
                } catch (NoSuchFileException ignored) {
                    continue;
                }
                if (!attrs.isRegularFile()) continue;

                T entity = store.readFromPath(path);
                if (query != null && !filterEngine.matchesAll(entity, query.filters())) continue;

                ids.add(repositoryModel.getPrimaryKeyValue(entity));
                if (query != null && query.limit() >= 0 && ids.size() >= query.limit()) return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private – file stream (used by stream() / iterator())
    // -------------------------------------------------------------------------

    private @NotNull Stream<T> buildFileStream() {
        List<Path> paths = new ArrayList<>(16);
        String ext       = store.fileExtension();
        int max          = store.isSharding() ? store.shardCount() : 1;
        String glob = "*" + ext;

        for (int i = 0; i < max; i++) {
            Path dir = store.isSharding()
                ? store.basePath().resolve(String.valueOf(i))
                : store.basePath();

            if (!Files.exists(dir)) continue;

            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, glob)) {
                for (Path path : ds) {
                    if (Files.isRegularFile(path)) paths.add(path);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        return paths.stream().map(path -> {
            try {
                return store.readFromPath(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static <R> List<R> collectTasks(CompletableFuture<List<R>>[] tasks) throws IOException {
        List<R> results = new ArrayList<>(tasks.length * 16);
        for (CompletableFuture<List<R>> task : tasks) {
            collectTask(task, results);
        }
        return results;
    }
}
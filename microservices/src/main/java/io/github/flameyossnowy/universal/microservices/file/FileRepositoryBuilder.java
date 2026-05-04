package io.github.flameyossnowy.universal.microservices.file;

import io.github.flameyossnowy.universal.api.ModelsBootstrap;
import io.github.flameyossnowy.universal.api.annotations.FileRepository;
import io.github.flameyossnowy.universal.api.annotations.enums.CompressionType;
import io.github.flameyossnowy.universal.api.annotations.enums.FileFormat;
import io.github.flameyossnowy.universal.api.resolver.TypeRegistration;
import io.github.flameyossnowy.universal.api.resolver.internal.DefaultTypeRegistry;
import io.github.flameyossnowy.universal.microservices.file.indexes.IndexPathStrategies;
import io.github.flameyossnowy.universal.microservices.file.indexes.IndexPathStrategy;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating {@link FileRepositoryAdapter} instances.
 *
 * @param <T> The entity type
 * @param <ID> The ID type
 */
@SuppressWarnings("unused")
public class FileRepositoryBuilder<T, ID> {
    static {
        ModelsBootstrap.init();
    }

    private final Class<T> entityType;
    private final Class<ID> idType;
    private Path basePath;
    private FileFormat format = FileFormat.JSON;
    private boolean compressed = false;
    private CompressionType compressionType = CompressionType.GZIP;
    private boolean sharding = false;
    private int shardCount = 10;

    private boolean autoCreate = true;

    private IndexPathStrategy indexPathStrategy = IndexPathStrategies.underBase();
    private boolean parallelReads;
    private final List<TypeRegistration> typeRegistrations = new ArrayList<>();

    /**
     * Creates a new builder for the given entity and ID types.
     */
    public FileRepositoryBuilder(@NotNull Class<T> entityType, @NotNull Class<ID> idType) {
        this.entityType = entityType;
        this.idType = idType;
    }

    /**
     * Creates a new builder from a class annotated with {@code @FileRepository}.
     */
    public static <T, ID> FileRepositoryBuilder<T, ID> from(@NotNull Class<T> entityType, @NotNull Class<ID> idType) {
        FileRepository annotation = entityType.getAnnotation(FileRepository.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                    "Entity " + entityType.getName() + " must be annotated with @FileRepository");
        }

        return new FileRepositoryBuilder<>(entityType, idType)
                .basePath(Paths.get(annotation.path()))
                .format(annotation.format())
                .compressed(annotation.compressed())
                .compressionType(annotation.compression())
                .sharding(annotation.sharding())
                .shardCount(annotation.shardCount());
    }

    public FileRepositoryBuilder<T, ID> indexPathStrategy(IndexPathStrategy strategy) {
        this.indexPathStrategy = strategy;
        return this;
    }

    public FileRepositoryBuilder<T, ID> basePath(Path basePath) {
        this.basePath = basePath;
        return this;
    }

    public FileRepositoryBuilder<T, ID> format(FileFormat format) {
        this.format = format;
        return this;
    }

    public FileRepositoryBuilder<T, ID> compressed(boolean compressed) {
        this.compressed = compressed;
        return this;
    }

    public FileRepositoryBuilder<T, ID> compressionType(CompressionType compressionType) {
        this.compressionType = compressionType;
        return this;
    }

    public FileRepositoryBuilder<T, ID> sharding(boolean sharding) {
        this.sharding = sharding;
        return this;
    }

    public FileRepositoryBuilder<T, ID> shardCount(int shardCount) {
        this.shardCount = shardCount;
        return this;
    }

    public FileRepositoryBuilder<T, ID> setAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
        return this;
    }

    public FileRepositoryBuilder<T, ID> setParallelReads(boolean parallelReads) {
        this.parallelReads = parallelReads;
        return this;
    }

    /**
     * Registers custom types with the repository adapter.
     *
     * <p>This method allows you to register custom type mappings, resolvers, and enums
     * that will be applied to the repository's TypeResolverRegistry during initialization.
     * Multiple registrations can be added and will be applied in order.</p>
     *
     * @param registration the type registration callback
     * @return this builder for chaining
     */
    public FileRepositoryBuilder<T, ID> registerTypes(TypeRegistration registration) {
        this.typeRegistrations.add(registration);
        return this;
    }

    private TypeRegistration combineRegistrations() {
        return DefaultTypeRegistry.combineRegistrations(typeRegistrations);
    }

    /**
     * Builds and returns a new {@link FileRepositoryAdapter} instance.
     */
    public FileRepositoryAdapter<T, ID> build() {
        if (basePath == null) {
            throw new IllegalStateException("basePath must be specified");
        }

        TypeRegistration combinedRegistration = combineRegistrations();

        return new FileRepositoryAdapter<>(
                entityType,
                idType,
                basePath,
                format,
                compressed,
                compressionType,
                sharding,
                shardCount,
                indexPathStrategy,
                autoCreate,
                parallelReads,
                combinedRegistration
        );
    }
}
